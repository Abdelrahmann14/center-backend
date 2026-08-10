package com.center.push.dto;

import jakarta.validation.constraints.NotBlank;

/** A device registering its Expo push token for the signed-in account. */
public record RegisterTokenRequest(@NotBlank String token, String platform) {
}
