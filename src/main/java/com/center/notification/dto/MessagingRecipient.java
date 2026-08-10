package com.center.notification.dto;

import java.util.UUID;

/** A pickable recipient (student or parent) in the admin notification composer. */
public record MessagingRecipient(UUID id, String name, String detail) {
}
