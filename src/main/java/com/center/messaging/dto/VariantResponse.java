package com.center.messaging.dto;

import java.util.UUID;

/** One editable alternative wording of an automated message, with its own image flag. */
public record VariantResponse(UUID id, String body, boolean sendAsImage) {
}
