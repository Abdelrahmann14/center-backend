package com.center.whatsapp.dto;

import java.util.UUID;

/**
 * One message type from the catalog: which number was chosen for it, and which
 * number will actually carry it.
 *
 * <p>The two are not the same. A type with no explicit choice still gets sent -
 * through the first working number in the scope, then the platform's - so a
 * screen that only showed {@code instanceId} would print "غير محدد" next to a
 * type that has been sending happily for months. {@code effectiveInstanceId} is
 * what the backend would really use if a message went out now, and it is the
 * only honest answer to "من أي رقم تُرسَل؟".
 *
 * @param code                catalog code
 * @param label               Arabic label
 * @param description         what it does
 * @param carriesFile         whether it ships a PDF (needs a DOCUMENT template)
 * @param instanceId          the number explicitly chosen, or null
 * @param effectiveInstanceId the number that would carry it right now, or null
 *                            when nothing that works can
 * @param numberLabel         the effective number's display name
 * @param templateId          the approved template that carries it, or null
 * @param templateName        that template's name, for the screens
 * @param ready               whether a message of this type can be sent right now
 * @param blockedReason       why not, in Arabic, when {@code ready} is false
 */
public record WhatsappResponsibilityResponse(
        String code,
        String label,
        String description,
        boolean carriesFile,
        UUID instanceId,
        UUID effectiveInstanceId,
        String numberLabel,
        UUID templateId,
        String templateName,
        boolean ready,
        String blockedReason) {
}
