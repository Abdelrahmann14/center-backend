package com.center.whatsapp.cloud.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.whatsapp.cloud.dto.CloudNumberRequest;
import com.center.whatsapp.cloud.dto.CloudNumberResponse;
import com.center.whatsapp.cloud.dto.CloudSendResultResponse;
import com.center.whatsapp.cloud.dto.CloudTestSendRequest;
import com.center.whatsapp.cloud.service.CloudApiClient.NumberInfo;
import com.center.whatsapp.entity.WhatsappInstance;
import com.center.whatsapp.repository.WhatsappInstanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provisions a teacher's number on the official WhatsApp account, from the super
 * admin's screen.
 *
 * <p>The whole point is that the teacher does nothing but read out one code. Four
 * calls, in order, each one a button:
 *
 * <ol>
 *   <li>{@link #add} - the number joins the business account.</li>
 *   <li>{@link #requestCode} - Meta sends it a code.</li>
 *   <li>{@link #verify} - the teacher reads the code back, it is confirmed.</li>
 *   <li>{@link #register} - a two-step PIN is set and the number can send.</li>
 * </ol>
 *
 * <p>A row is written at step 1 and updated as it advances, so a provisioning
 * that stalls halfway is visible on the screen instead of vanishing - the number
 * still exists on Meta's side either way, and pretending otherwise would leave
 * the two out of step.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CloudNumberService {

    /** How far provisioning has got. Only AUTHORIZED can send. */
    private static final String PENDING = "pending";
    private static final String VERIFIED = "verified";
    private static final String AUTHORIZED = "authorized";

    private final CloudApiClient cloud;
    private final WhatsappInstanceRepository repo;
    private final com.center.whatsapp.service.WhatsappInstanceService instances;

    /**
     * Adds the number to the business account and records it against a teacher.
     *
     * @param ownerAdminId the teacher who will send with it, null for the platform
     */
    @Transactional
    public CloudNumberResponse add(UUID ownerAdminId, CloudNumberRequest request) {
        String phoneNumberId = cloud.addNumber(request.countryCode(), request.localNumber(),
                request.displayName());
        if (phoneNumberId == null) {
            throw new BusinessRuleException("لم يرجع واتساب معرّف الرقم");
        }

        WhatsappInstance row = new WhatsappInstance();
        row.setOwnerAdminId(ownerAdminId);
        row.setLabel(request.label() == null || request.label().isBlank()
                ? request.displayName()
                : request.label());
        row.setPhoneNumberId(phoneNumberId);
        row.setDisplayName(request.displayName());
        row.setPhone(request.countryCode() + request.localNumber());
        // Not able to send yet - it has not been verified, let alone registered.
        row.setState(PENDING);
        return toResponse(repo.save(row), null);
    }

    /**
     * Adopts numbers that already exist on the business account but that this
     * system has never seen - the ones added straight in WhatsApp Manager.
     *
     * <p>Needed because Meta is the real owner of the list: a number added there
     * cannot be added again from here (Meta rejects the duplicate), so without
     * this it would be permanently invisible to the app while being perfectly
     * usable. Importing is also how the very first number gets in, since the
     * account is normally set up by hand before any of this code runs.
     *
     * @param ownerAdminId the teacher to attach the imported numbers to
     * @return only the numbers that were newly adopted
     */
    @Transactional
    public List<CloudNumberResponse> importExisting(UUID ownerAdminId) {
        List<CloudNumberResponse> adopted = new java.util.ArrayList<>();
        for (NumberInfo info : cloud.listNumbers()) {
            if (info.id() == null || repo.findByPhoneNumberId(info.id()).isPresent()) {
                continue;
            }
            WhatsappInstance row = new WhatsappInstance();
            row.setOwnerAdminId(ownerAdminId);
            row.setPhoneNumberId(info.id());
            row.setDisplayName(info.verifiedName());
            row.setLabel(info.verifiedName());
            row.setPhone(info.displayPhoneNumber());
            row.setQualityRating(info.qualityRating());
            // Meta's own view decides whether it can send. A number imported mid
            // setup is usually still short of registration, and saying otherwise
            // would put it in the send rotation before it can deliver anything.
            row.setState("CONNECTED".equalsIgnoreCase(info.status()) ? AUTHORIZED
                    : "VERIFIED".equalsIgnoreCase(info.codeVerificationStatus()) ? VERIFIED
                    : PENDING);
            adopted.add(toResponse(repo.save(row), info));
        }
        return adopted;
    }

    /** Asks Meta to send the verification code to the number. */
    @Transactional(readOnly = true)
    public void requestCode(UUID id, String method) {
        cloud.requestCode(cloudRow(id).getPhoneNumberId(), method);
    }

    /** Confirms the code the teacher read out. */
    @Transactional
    public CloudNumberResponse verify(UUID id, String code) {
        WhatsappInstance row = cloudRow(id);
        cloud.verifyCode(row.getPhoneNumberId(), code);
        row.setState(VERIFIED);
        return toResponse(repo.save(row), null);
    }

    /**
     * Sets the two-step PIN and registers the number, which is the step that makes
     * it able to send. Also subscribes the app to the account's webhooks - a
     * one-off that is harmless to repeat, and skipping it would mean the sends
     * work while nothing ever reports whether they arrived.
     */
    @Transactional
    public CloudNumberResponse register(UUID id, String pin) {
        WhatsappInstance row = cloudRow(id);
        cloud.register(row.getPhoneNumberId(), pin);
        try {
            cloud.subscribeApp();
        } catch (RuntimeException ex) {
            // The number can send regardless; only the status reports are lost, and
            // a failure here must not undo a registration that already succeeded.
            log.warn("Registered {} but could not subscribe the app to webhooks: {}",
                    row.getPhoneNumberId(), ex.getMessage());
        }
        row.setState(AUTHORIZED);
        return toResponse(repo.save(row), null);
    }

    /**
     * Every number this system knows about, each one carrying whatever Meta says
     * about it right now (quality, verification). One Graph call for the whole
     * list, and if it fails the local rows are still returned - a screen that
     * shows the numbers without their live quality beats a screen that shows an
     * error.
     */
    @Transactional(readOnly = true)
    public List<CloudNumberResponse> list() {
        Map<String, NumberInfo> live = liveByPhoneNumberId();
        return repo.findAllByOrderByCreatedAtAsc().stream()
                .map(row -> toResponse(row, live.get(row.getPhoneNumberId())))
                .toList();
    }

    /**
     * Pulls Meta's view of every number into the local rows: quality rating, the
     * display name once its review finishes, and the number as Meta formats it.
     */
    @Transactional
    public List<CloudNumberResponse> refresh() {
        Map<String, NumberInfo> live = liveByPhoneNumberId();
        List<WhatsappInstance> rows = repo.findAllByOrderByCreatedAtAsc();
        for (WhatsappInstance row : rows) {
            NumberInfo info = live.get(row.getPhoneNumberId());
            if (info == null) {
                continue;
            }
            row.setQualityRating(info.qualityRating());
            if (info.verifiedName() != null) {
                row.setDisplayName(info.verifiedName());
            }
            if (info.displayPhoneNumber() != null) {
                row.setPhone(info.displayPhoneNumber());
            }
            // Meta reporting the number as CONNECTED is the only authority on
            // whether it can send; a local row can be stale in either direction.
            boolean connected = "CONNECTED".equalsIgnoreCase(info.status());
            if (!connected && AUTHORIZED.equalsIgnoreCase(row.getState())) {
                // It was working and is not any more. Meta is the only place
                // that can tell us, so without this the message types it carries
                // would keep being routed to a dead number and fail one parent
                // at a time.
                instances.noteDown(row, info.status());
            }
            row.setState(connected ? AUTHORIZED
                    : "VERIFIED".equalsIgnoreCase(info.codeVerificationStatus()) ? VERIFIED
                    : PENDING);
        }
        repo.saveAll(rows);
        return rows.stream().map(row -> toResponse(row, live.get(row.getPhoneNumberId()))).toList();
    }

    /**
     * Removes the number from the business account and forgets it here.
     *
     * <p>Deleting on Meta's side is what frees the number to be used elsewhere, so
     * it is attempted first: dropping the local row while Meta still holds the
     * number would leave a number nobody can see and nobody can re-add.
     */
    @Transactional
    public void delete(UUID id) {
        WhatsappInstance row = cloudRow(id);
        cloud.deleteNumber(row.getPhoneNumberId());
        // Not repo.delete: the number may own message types, and they have to be
        // moved to a backup - and their owner told - before the row disappears.
        instances.forget(row);
    }

    /**
     * Sends one template from one number, to check the account end to end.
     *
     * <p>Goes through the same client the real messages use, so a pass here
     * means the token, the number, the template and the parameter count are all
     * genuinely right - not merely configured.
     */
    @Transactional(readOnly = true)
    public CloudSendResultResponse testSend(UUID id, CloudTestSendRequest request) {
        WhatsappInstance row = cloudRow(id);
        if (!AUTHORIZED.equalsIgnoreCase(row.getState())) {
            throw new BusinessRuleException("الرقم لم يُفعّل بعد");
        }
        CloudApiClient.SendResult result = cloud.sendTemplate(row.getPhoneNumberId(), request.to(),
                new CloudApiClient.TemplateSpec(request.templateName(), request.language(),
                        request.params() == null ? List.of() : request.params(), null,
                        request.headerParam(), request.urlButtonParam()));
        return new CloudSendResultResponse(result.sent(), result.messageId(),
                result.failureReason());
    }

    private Map<String, NumberInfo> liveByPhoneNumberId() {
        try {
            return cloud.listNumbers().stream()
                    .filter(info -> info.id() != null)
                    .collect(Collectors.toMap(NumberInfo::id, Function.identity(), (a, b) -> a));
        } catch (RuntimeException ex) {
            log.warn("Could not read the numbers from Meta: {}", ex.getMessage());
            return Map.of();
        }
    }

    private WhatsappInstance cloudRow(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("الرقم غير موجود"));
    }

    private static CloudNumberResponse toResponse(WhatsappInstance row, NumberInfo info) {
        return new CloudNumberResponse(
                row.getId(),
                row.getLabel(),
                row.getOwnerAdminId(),
                row.getPhoneNumberId(),
                info != null && info.displayPhoneNumber() != null
                        ? info.displayPhoneNumber()
                        : row.getPhone(),
                row.getDisplayName(),
                AUTHORIZED.equalsIgnoreCase(row.getState()),
                row.getState(),
                info != null ? info.qualityRating() : row.getQualityRating(),
                info != null ? info.codeVerificationStatus() : null);
    }
}
