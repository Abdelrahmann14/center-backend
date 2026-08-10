package com.center.notification.dto;

/** Outcome of a broadcast: in-app recipients reached and WhatsApp messages sent. */
public record BroadcastResult(int sent, int whatsappSent) {
}
