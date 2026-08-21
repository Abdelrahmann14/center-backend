package com.center.whatsapp.cloud.dto;

/**
 * What a test send produced.
 *
 * <p>{@code sent} means Meta ACCEPTED the message, not that it arrived - delivery
 * is reported later on the webhook. The distinction matters on this screen: a
 * pass here proves the token, the number and the template are all valid,
 * which is exactly what a test is for, and nothing more.
 *
 * @param messageId Meta's {@code wamid}, the handle every later report carries
 */
public record CloudSendResultResponse(boolean sent, String messageId, String failureReason) {
}
