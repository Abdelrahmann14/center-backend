package com.center.whatsapp.dto;

import java.util.UUID;

/**
 * One WhatsApp number in an owner's pool, as a screen needs to read it.
 *
 * <p>Every number is on the official WhatsApp account, so nothing here says which
 * service carries it - there is only one. What a person needs instead is whether
 * it is finished being set up ({@code connected}) and how WhatsApp currently
 * rates it, because a falling quality rating is the early warning that the
 * account is heading for a send limit.
 *
 * @param id            the number's row id
 * @param label         friendly name, or null
 * @param connected     whether the number is registered and ready to send
 * @param state         pending | verified | authorized - where setup got to
 * @param phone         the line itself
 * @param displayName   the business name recipients see, once Meta approves it
 * @param qualityRating GREEN | YELLOW | RED, as Meta last rated it
 */
public record WhatsappStatusResponse(
        UUID id,
        String label,
        boolean connected,
        String state,
        String phone,
        String displayName,
        String qualityRating) {
}
