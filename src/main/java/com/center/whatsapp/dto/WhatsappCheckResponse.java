package com.center.whatsapp.dto;

/**
 * Result of a WhatsApp lookup for a phone number. {@code checked=false} means the
 * lookup could not be performed (Green API off or errored); callers should treat
 * that as "do not block" rather than "no WhatsApp".
 */
public record WhatsappCheckResponse(boolean existsWhatsapp, boolean checked) {
}
