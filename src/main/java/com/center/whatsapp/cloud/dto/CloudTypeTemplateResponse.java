package com.center.whatsapp.cloud.dto;

import java.util.UUID;

/**
 * One message type and the template that carries it on the official account.
 *
 * @param inherited true when the mapping comes from the platform scope rather
 *                  than from this account's own row - the screens say so, since
 *                  "لم يُحدد" and "يستخدم قالب المنصة" mean very different things
 */
public record CloudTypeTemplateResponse(
        String code,
        String label,
        UUID templateId,
        String templateName,
        String templateStatus,
        String urlButtonValue,
        boolean inherited) {
}
