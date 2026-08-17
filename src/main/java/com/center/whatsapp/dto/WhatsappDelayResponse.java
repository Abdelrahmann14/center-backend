package com.center.whatsapp.dto;

/**
 * The number's send delay - the pause Green API leaves between one outgoing
 * message and the next - expressed in whole seconds for the admin. Stored on the
 * Green API side in milliseconds; the service converts.
 */
public record WhatsappDelayResponse(int delaySeconds) {
}
