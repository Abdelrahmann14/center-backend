package com.center.whatsapp.cloud.dto;

import java.util.List;
import java.util.UUID;

/**
 * One message template as mirrored from Meta, with everything this system adds
 * on top of it: a readable name, which system variable fills each placeholder,
 * and who is allowed to use it.
 *
 * @param status       only {@code APPROVED} can actually be sent
 * @param bodyParams   how many values a send must supply
 * @param headerFormat NONE | TEXT | IMAGE | DOCUMENT | VIDEO - a DOCUMENT header
 *                     is what carries the barcode card and the report PDF
 * @param headerText   the wording of a TEXT header; a {@code {{1}}} in it is what
 *                     makes {@code headerVar} required rather than decorative
 * @param hasUrlButton whether it has a button whose link takes a value per send
 * @param label        a plain-Arabic name for the screens; Meta's own is a slug
 * @param headerVar    the system variable filling a TEXT header, or null
 * @param varKeys      the system variable for each placeholder, position 1 first;
 *                     shorter than {@code bodyParams} while a mapping is unfinished
 * @param sharedAll    true when every account may use it
 * @param adminIds     the accounts allowed to use it, when it is not shared
 */
public record CloudTemplateResponse(
        UUID id,
        String metaTemplateId,
        String name,
        String language,
        String category,
        String status,
        String bodyText,
        String headerFormat,
        String headerText,
        int bodyParams,
        boolean hasUrlButton,
        String label,
        String headerVar,
        List<String> varKeys,
        boolean sharedAll,
        List<UUID> adminIds,
        String rejectedReason) {
}
