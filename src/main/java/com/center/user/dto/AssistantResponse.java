package com.center.user.dto;

import java.util.UUID;

/** Minimal assistant projection for the selects that list assistants. */
public record AssistantResponse(UUID id, String username) {
}
