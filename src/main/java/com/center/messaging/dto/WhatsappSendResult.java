package com.center.messaging.dto;

/** Outcome of a manual send: how many messages went through, failed, in total. */
public record WhatsappSendResult(int sent, int failed, int total) {
}
