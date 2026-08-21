package com.center.whatsapp.cloud.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A one-off send from a specific number, to prove the account really works.
 *
 * <p>Deliberately takes the template by name rather than by id: what a send needs
 * is the name and the language pair, which is exactly what a person reads off the
 * templates screen.
 *
 * @param to           the recipient, any format - it is normalised before sending
 * @param templateName an APPROVED template on the account
 * @param language     the template's language code, e.g. {@code ar_EG}
 * @param params         values for the template's placeholders, in order
 * @param headerParam    the value for a TEXT header that carries a placeholder;
 *                       null for every other header, since Meta rejects a value
 *                       for a static header as readily as it rejects a missing
 *                       one for a dynamic header
 * @param urlButtonParam the value appended to a dynamic URL button, when the
 *                       template has one - for these templates, the WhatsApp
 *                       number the parent should reach the office on
 */
public record CloudTestSendRequest(
        @NotBlank(message = "مطلوب") @Size(max = 20) String to,
        @NotBlank(message = "مطلوب") String templateName,
        @NotBlank(message = "مطلوب") String language,
        List<String> params,
        String headerParam,
        String urlButtonParam) {
}
