package com.center.whatsapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.notification.service.NotificationService;
import com.center.settings.service.SettingsService;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.dto.WhatsappStatusResponse;
import com.center.whatsapp.entity.WhatsappInstance;
import com.center.whatsapp.entity.WhatsappResponsibility;
import com.center.whatsapp.entity.WhatsappResponsibilityId;
import com.center.whatsapp.entity.WhatsappConfig;
import com.center.whatsapp.repository.WhatsappConfigRepository;
import com.center.whatsapp.repository.WhatsappInstanceRepository;
import com.center.whatsapp.repository.WhatsappResponsibilityRepository;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The WhatsApp number pool: who owns which numbers, which number carries which
 * kind of message, and what happens when one of them stops working.
 *
 * <p>Every number lives on the official WhatsApp account hosted by Meta. There is
 * no per-number credential to manage here - the token belongs to the platform and
 * lives in the environment, and a number is addressed by the {@code
 * phone_number_id} Meta issued for it. Provisioning a number (adding, verifying,
 * registering it) is the super admin's job and lives in {@code CloudNumberService};
 * this class owns everything that happens to a number afterwards.
 *
 * <p>Sending resolves by the caller's tenant: an admin's messages go through the
 * admin's number for that message type, falling back to any of the admin's
 * working numbers, then the platform's.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappInstanceService {

    /** Responsibility rows for the super-admin scope use this owner sentinel. */
    private static final UUID SUPER = new UUID(0L, 0L);

    private final WhatsappInstanceRepository repo;
    private final WhatsappResponsibilityRepository respRepo;
    private final WhatsappConfigRepository configRepo;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SettingsService settings;

    /**
     * Everything needed to send through one resolved number: Meta's id for it,
     * and the line itself (which a wa.me button points at). {@code configured}
     * false means there is nothing to send with and the caller must say so rather
     * than attempt a call that cannot succeed.
     */
    public record Creds(UUID rowId, String phoneNumberId, String phone, boolean configured,
            String reason) {

        /**
         * Why nothing can be sent, for the log row and the toast.
         *
         * <p>It is carried rather than re-derived because the callers cannot tell
         * the cases apart: "no number is connected" and "the teacher paused
         * sending" both arrive here as {@code configured == false}, and a paused
         * workspace reading "لم يتم تفعيل رقم واتساب" would go hunting for a
         * number problem that does not exist.
         */
        public String reasonOrDefault() {
            return reason == null || reason.isBlank() ? "لم يتم تفعيل رقم واتساب" : reason;
        }
    }

    private static boolean isAuthorized(String state) {
        return "authorized".equalsIgnoreCase(state);
    }

    private static UUID respOwner(UUID scope) {
        return scope == null ? SUPER : scope;
    }

    /** The super admin (owner null) is always enabled; an admin needs the flag on. */
    public boolean enabledFor(UUID owner) {
        return owner == null
                || configRepo.findById(owner).map(c -> c.isEnabled()).orElse(false);
    }

    /**
     * Whether the workspace's own master switch is on - the teacher's pause,
     * not the platform's grant.
     *
     * <p>Missing row reads as ON, matching the column default: a workspace that
     * has never been configured is not "paused", it is not enabled at all, and
     * {@link #enabledFor} is what says so.
     */
    public boolean sendingEnabledFor(UUID owner) {
        return owner == null
                || configRepo.findById(owner).map(WhatsappConfig::isSendingEnabled).orElse(true);
    }

    /** Both switches: the platform allows it AND the workspace has not paused it. */
    public boolean canSend(UUID owner) {
        return enabledFor(owner) && sendingEnabledFor(owner);
    }

    /**
     * Flips the workspace's own switch.
     *
     * <p>Refuses when the platform switch is off: letting a teacher "turn on"
     * something the super admin has withheld would leave the page claiming a
     * state the sender does not honour.
     */
    @Transactional
    public void setSending(UUID owner, boolean enabled) {
        requireEnabled(owner);
        WhatsappConfig row = configRepo.findById(owner)
                .orElseGet(() -> new WhatsappConfig(owner, true));
        row.setSendingEnabled(enabled);
        configRepo.save(row);
    }

    private void requireEnabled(UUID owner) {
        if (!enabledFor(owner)) {
            throw new BusinessRuleException("ميزة واتساب غير مُفعّلة لحسابك من قبل الإدارة");
        }
    }

    private List<WhatsappInstance> pool(UUID owner) {
        return owner == null
                ? repo.findByOwnerAdminIdIsNullOrderByCreatedAtAsc()
                : repo.findByOwnerAdminIdOrderByCreatedAtAsc(owner);
    }

    // ---- credential resolution for sending (scope = current tenant) ---------

    @Transactional(readOnly = true)
    public Creds resolve() {
        UUID scope = TenantContext.get();
        if (!canSend(scope)) {
            // Either the super admin turned the feature off for this workspace or
            // the teacher paused their own sending. Refusing here rather than at
            // each call site is the whole point: there are a dozen send paths and
            // only one of them would have remembered to ask.
            return disabled(enabledFor(scope) ? PAUSED : null);
        }
        return firstConnected(scope)
                .or(() -> scope == null ? Optional.empty() : firstConnected(null))
                .map(WhatsappInstanceService::credsOf)
                .orElseGet(WhatsappInstanceService::disabled);
    }

    @Transactional(readOnly = true)
    public Creds resolveFor(String responsibilityCode) {
        UUID scope = TenantContext.get();
        if (!canSend(scope)) {
            return disabled(enabledFor(scope) ? PAUSED : null);
        }
        return assignedNumber(scope, responsibilityCode)
                .map(WhatsappInstanceService::credsOf)
                .orElseGet(this::resolve);
    }

    /**
     * The number that would carry {@code responsibilityCode} for {@code owner}
     * right now: the one explicitly chosen if it is working, otherwise the first
     * working number in the scope, otherwise the platform's.
     *
     * <p>Same order {@link #resolveFor} sends by, deliberately - it is what the
     * message-types screen reports, and a screen that disagreed with the sender
     * would be worse than no screen.
     */
    @Transactional(readOnly = true)
    public Optional<WhatsappInstance> effectiveNumber(UUID owner, String responsibilityCode) {
        if (!enabledFor(owner)) {
            return Optional.empty();
        }
        return assignedNumber(owner, responsibilityCode)
                .or(() -> firstConnected(owner))
                .or(() -> owner == null ? Optional.empty() : firstConnected(null));
    }

    /** The explicitly chosen number for a purpose, only if it can send. */
    private Optional<WhatsappInstance> assignedNumber(UUID owner, String responsibilityCode) {
        return respRepo.findById(new WhatsappResponsibilityId(respOwner(owner), responsibilityCode))
                .flatMap(r -> repo.findById(r.getInstanceId()))
                .filter(w -> isAuthorized(w.getState()));
    }

    /** The answer for a workspace with nothing it can send through. */
    private static Creds disabled() {
        return disabled(null);
    }

    private static Creds disabled(String reason) {
        return new Creds(null, null, null, false, reason);
    }

    /** Refusal wording for a workspace that switched its own sending off. */
    static final String PAUSED = "إرسال الواتساب موقوف من صفحة الخدمات";

    private Optional<WhatsappInstance> firstConnected(UUID owner) {
        return pool(owner).stream().filter(w -> isAuthorized(w.getState())).findFirst();
    }

    private static Creds credsOf(WhatsappInstance w) {
        return new Creds(w.getId(), w.getPhoneNumberId(), w.getPhone(), true, null);
    }

    // ---- number pool (UI) - scoped by owner ---------------------------------

    /**
     * The owner's numbers, as stored.
     *
     * <p>No round trip to Meta: a number's live quality and verification state are
     * pulled by the super admin's explicit refresh, and doing it here would put a
     * third-party call on a page the app polls. The state on the row is what the
     * sender itself resolves by, so this and the sender always agree.
     */
    @Transactional(readOnly = true)
    public List<WhatsappStatusResponse> list(UUID owner) {
        if (!enabledFor(owner)) {
            return List.of();
        }
        List<WhatsappStatusResponse> out = new ArrayList<>();
        for (WhatsappInstance w : pool(owner)) {
            out.add(toStatus(w));
        }
        return out;
    }

    /**
     * Drop a number after moving whatever it was responsible for onto a backup
     * and telling its owner. The ONLY way a number should leave the system:
     * deleting the row directly would leave message types pointing at an id that
     * no longer resolves, and those messages would fail one by one with nobody
     * having been told the number went.
     */
    @Transactional
    public void forget(WhatsappInstance row) {
        handleDown(row, row.getState(), true);
        respRepo.deleteByInstanceId(row.getId());
        repo.delete(row);
    }

    /**
     * Record that a number stopped working, failing its responsibilities over to
     * a backup and notifying the owner. Called when Meta reports the number is no
     * longer connected, which is the only authority on the matter.
     */
    @Transactional
    public void noteDown(WhatsappInstance row, String reason) {
        handleDown(row, reason, false);
    }

    /**
     * Rename a number's friendly label. This is the only change an admin makes to
     * their own numbers - the number itself is provisioned on the official account
     * by the super admin, so all the admin does is name it.
     */
    @Transactional
    public WhatsappStatusResponse rename(UUID owner, UUID id, String label) {
        WhatsappInstance w = require(owner, id);
        w.setLabel(label == null || label.isBlank() ? null : label.trim());
        return toStatus(w);
    }

    // ---- responsibilities - scoped by owner ---------------------------------

    /** The owner's numbers as entities, for the callers that need more than the DTO. */
    @Transactional(readOnly = true)
    public List<WhatsappInstance> numbers(UUID owner) {
        return pool(owner);
    }

    /** The explicit choices only, by code. Null values are not present. */
    @Transactional(readOnly = true)
    public Map<String, UUID> assignments(UUID owner) {
        Map<String, UUID> out = new java.util.HashMap<>();
        for (WhatsappResponsibility r : respRepo.findByOwnerAdminId(respOwner(owner))) {
            out.put(r.getCode(), r.getInstanceId());
        }
        return out;
    }

    /**
     * Give one message type to one number, or take it back ({@code instanceId}
     * null). The key is (owner, code), so a type is owned by exactly one number
     * and assigning it elsewhere moves it rather than duplicating it - the
     * database enforces the rule, not the screen.
     */
    @Transactional
    public void assign(UUID owner, String code, UUID instanceId) {
        requireEnabled(owner);
        if (!WhatsappResponsibilityCatalog.isValid(code)) {
            throw new BusinessRuleException("نوع رسالة غير معروف");
        }
        WhatsappResponsibilityId key = new WhatsappResponsibilityId(respOwner(owner), code);
        if (instanceId == null) {
            respRepo.findById(key).ifPresent(respRepo::delete);
            return;
        }
        WhatsappInstance target = require(owner, instanceId);
        if (!isAuthorized(target.getState())) {
            throw new BusinessRuleException("لا يمكن إسناد نوع رسالة إلى رقم غير مُفعّل");
        }
        respRepo.save(new WhatsappResponsibility(respOwner(owner), code, instanceId));
    }

    // ---- failover -----------------------------------------------------------

    private void handleDown(WhatsappInstance down, String reason, boolean manual) {
        UUID owner = down.getOwnerAdminId();
        List<WhatsappResponsibility> owned = respRepo.findByInstanceId(down.getId());
        WhatsappInstance backup = pool(owner).stream()
                .filter(w -> !w.getId().equals(down.getId()) && isAuthorized(w.getState()))
                .findFirst()
                .orElse(null);

        String who = displayName(down);
        String cause = manual ? "تمت إزالته" : "توقف عن العمل" + reasonSuffix(reason);

        if (backup != null && !owned.isEmpty()) {
            for (WhatsappResponsibility r : owned) {
                r.setInstanceId(backup.getId());
                respRepo.save(r);
            }
            notifyOwner(owner, "تحويل مسؤوليات رقم واتساب",
                    "الرقم (" + who + ") " + cause + ". تم تحويل " + owned.size()
                            + " من مسؤولياته تلقائيًا إلى الرقم (" + displayName(backup) + ").");
        } else if (backup == null) {
            notifyOwner(owner, "توقف رقم واتساب دون بديل",
                    "الرقم (" + who + ") " + cause
                            + " ولا يوجد رقم آخر يعمل. سيتم الاكتفاء بإشعارات التطبيق فقط "
                            + "حتى يتم تفعيل رقم آخر.");
        } else {
            notifyOwner(owner, "توقف رقم واتساب",
                    "الرقم (" + who + ") " + cause + ". لا مسؤوليات مرتبطة به.");
        }
    }

    /** Notify the pool owner: the admin themselves, or every super admin. */
    private void notifyOwner(UUID owner, String title, String body) {
        String sender = settings.senderName();
        if (owner == null) {
            for (User u : userRepository.findByRoleAndActiveTrue(Role.SUPER_ADMIN)) {
                notificationService.notify(u.getId(), sender, "whatsapp", title, body, null);
            }
        } else {
            notificationService.notify(owner, sender, "whatsapp", title, body, null);
        }
    }

    private static String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : " (الحالة: " + reason + ")";
    }

    private static String displayName(WhatsappInstance w) {
        if (w.getLabel() != null && !w.getLabel().isBlank()) return w.getLabel();
        if (w.getDisplayName() != null && !w.getDisplayName().isBlank()) return w.getDisplayName();
        if (w.getPhone() != null && !w.getPhone().isBlank()) return "+" + w.getPhone();
        return w.getPhoneNumberId();
    }

    private WhatsappInstance require(UUID owner, UUID id) {
        return repo.findById(id)
                .filter(w -> Objects.equals(w.getOwnerAdminId(), owner))
                .orElseThrow(() -> new BusinessRuleException("الرقم غير موجود"));
    }

    private static WhatsappStatusResponse toStatus(WhatsappInstance w) {
        boolean connected = isAuthorized(w.getState());
        return new WhatsappStatusResponse(
                w.getId(),
                w.getLabel(),
                connected,
                w.getState(),
                w.getPhone(),
                w.getDisplayName(),
                w.getQualityRating());
    }
}
