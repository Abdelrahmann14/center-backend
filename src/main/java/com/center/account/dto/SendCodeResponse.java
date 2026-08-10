package com.center.account.dto;

/** Result of requesting a verification code - never echoes the code itself. */
public record SendCodeResponse(String phoneHint, int expiresInSeconds) {
}
