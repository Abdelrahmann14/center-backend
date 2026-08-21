package com.center.whatsapp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.whatsapp.cloud.entity.WhatsappCloudTemplate;
import com.center.whatsapp.cloud.entity.WhatsappTypeTemplate;
import com.center.whatsapp.cloud.repository.WhatsappCloudTemplateRepository;
import com.center.whatsapp.cloud.repository.WhatsappTypeTemplateRepository;
import com.center.whatsapp.dto.WhatsappAvailabilityResponse;
import com.center.whatsapp.dto.WhatsappResponsibilityResponse;
import com.center.whatsapp.entity.WhatsappInstance;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog.Responsibility;

import lombok.RequiredArgsConstructor;

/**
 * The one place that answers "can this workspace send this kind of message right
 * now, and through which number?".
 *
 * <p>It exists because that question has three independent answers stacked on top
 * of each other - the feature switch, a working number, and an approved template
 * with the right shape - and every one of them had previously been re-implemented,
 * differently, by whichever screen needed it. The frontend now reads this and
 * mirrors it; it decides nothing itself, so it cannot promise something the sender
 * would refuse.
 *
 * <p>The readiness rules, in the order they are checked:
 *
 * <ol>
 *   <li>The super admin has to have enabled WhatsApp for the account.</li>
 *   <li>Some number in scope has to be registered and able to send.</li>
 *   <li>The type has to have an APPROVED template - WhatsApp refuses anything
 *       else that the business starts.</li>
 *   <li>If the message carries a PDF, that template has to have a DOCUMENT
 *       header, or there is nowhere for the file to go.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class WhatsappAvailabilityService {

    /** Type-template rows for the platform scope use this owner sentinel. */
    private static final UUID PLATFORM = new UUID(0L, 0L);

    private final WhatsappInstanceService instances;
    private final WhatsappTypeTemplateRepository typeTemplates;
    private final WhatsappCloudTemplateRepository templates;

    /** The whole picture for one owner scope (null = the platform's own). */
    @Transactional(readOnly = true)
    public WhatsappAvailabilityResponse availability(UUID owner) {
        boolean enabled = instances.enabledFor(owner);
        boolean sending = instances.sendingEnabledFor(owner);
        List<WhatsappInstance> pool = enabled ? instances.numbers(owner) : List.of();
        long connected = pool.stream()
                .filter(w -> "authorized".equalsIgnoreCase(w.getState()))
                .count();

        return new WhatsappAvailabilityResponse(
                enabled,
                sending,
                (int) connected,
                messageTypes(owner, enabled, sending));
    }

    /** Every message type, resolved to the number and template that will carry it. */
    @Transactional(readOnly = true)
    public List<WhatsappResponsibilityResponse> messageTypes(UUID owner) {
        return messageTypes(owner, instances.enabledFor(owner), instances.sendingEnabledFor(owner));
    }

    private List<WhatsappResponsibilityResponse> messageTypes(UUID owner, boolean enabled,
            boolean sending) {
        Map<String, UUID> chosen = instances.assignments(owner);
        Map<String, WhatsappTypeTemplate> mapped = templateMapping(owner);

        List<WhatsappResponsibilityResponse> out = new ArrayList<>();
        for (Responsibility r : WhatsappResponsibilityCatalog.ALL) {
            WhatsappInstance number =
                    enabled ? instances.effectiveNumber(owner, r.code()).orElse(null) : null;
            WhatsappCloudTemplate template = Optional.ofNullable(mapped.get(r.code()))
                    .flatMap(row -> templates.findById(row.getTemplateId()))
                    .orElse(null);

            String blocked = blockedReason(enabled, sending, number, template, r);

            out.add(new WhatsappResponsibilityResponse(
                    r.code(),
                    r.label(),
                    r.description(),
                    r.carriesFile(),
                    chosen.get(r.code()),
                    number == null ? null : number.getId(),
                    number == null ? null : displayName(number),
                    template == null ? null : template.getId(),
                    template == null ? null : template.getName(),
                    blocked == null,
                    blocked));
        }
        return out;
    }

    /** Why a type cannot be sent, or null when it can. */
    private static String blockedReason(boolean enabled, boolean sending, WhatsappInstance number,
            WhatsappCloudTemplate template, Responsibility r) {
        if (!enabled) {
            return "ميزة واتساب غير مُفعّلة لهذا الحساب";
        }
        // Checked before the number and the template deliberately. A paused
        // workspace usually has both, and reporting "لا يوجد رقم" to someone who
        // can see their own connected number reads as a bug rather than a switch
        // they themselves flipped.
        if (!sending) {
            return "إرسال الواتساب موقوف — فعّله من صفحة الخدمات";
        }
        if (number == null) {
            return "لا يوجد رقم واتساب مُفعّل";
        }
        if (template == null) {
            return "هذا النوع من الرسائل يحتاج قالباً معتمداً";
        }
        if (!"APPROVED".equalsIgnoreCase(template.getStatus())) {
            return "القالب المرتبط بهذا النوع غير معتمد من واتساب";
        }
        if (r.carriesFile() && !"DOCUMENT".equalsIgnoreCase(template.getHeaderFormat())) {
            return "هذه الرسالة ترسل ملفاً، والقالب المرتبط بها لا يحتوي على رأس ملف";
        }
        return null;
    }

    /**
     * The type-to-template mapping in force for an owner: their own rows, with
     * the platform's used for anything they have not overridden. One template
     * written once therefore serves every teacher until one of them needs their
     * own wording.
     */
    @Transactional(readOnly = true)
    public Map<String, WhatsappTypeTemplate> templateMapping(UUID owner) {
        Map<String, WhatsappTypeTemplate> out = new java.util.HashMap<>();
        for (WhatsappTypeTemplate row : typeTemplates.findByOwnerAdminId(PLATFORM)) {
            out.put(row.getCode(), row);
        }
        if (owner != null) {
            for (WhatsappTypeTemplate row : typeTemplates.findByOwnerAdminId(owner)) {
                out.put(row.getCode(), row);
            }
        }
        return out;
    }

    /** The owner sentinel a scope's type-template rows are stored under. */
    public static UUID scopeOf(UUID owner) {
        return owner == null ? PLATFORM : owner;
    }

    private static String displayName(WhatsappInstance w) {
        if (w.getLabel() != null && !w.getLabel().isBlank()) {
            return w.getLabel();
        }
        if (w.getDisplayName() != null && !w.getDisplayName().isBlank()) {
            return w.getDisplayName();
        }
        if (w.getPhone() != null && !w.getPhone().isBlank()) {
            return "+" + w.getPhone();
        }
        return w.getPhoneNumberId() == null ? "رقم واتساب" : w.getPhoneNumberId();
    }
}
