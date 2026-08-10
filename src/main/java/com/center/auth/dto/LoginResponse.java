package com.center.auth.dto;

/** The issued token plus the account it belongs to. */
public record LoginResponse(String token, AuthenticatedUserResponse user) {
}
