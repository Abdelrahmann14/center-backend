package com.center.whatsapp.cloud.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * What a person decides about a template: what to call it, which system variable
 * fills each of its numbered placeholders, and who may use it.
 *
 * @param label     a plain-Arabic name for the screens
 * @param headerVar the system variable filling a TEXT header, or null
 * @param varKeys   one key per placeholder, position 1 first. A blank or null
 *                  entry leaves that placeholder unmapped, which is allowed
 *                  while a mapping is being filled in - the send substitutes a
 *                  dash so the gap is visible rather than silent.
 * @param sharedAll true to let every account use it
 * @param adminIds  the accounts allowed to use it; ignored when shared
 */
public record CloudTemplateMappingRequest(
        @Size(max = 120) String label,
        @Size(max = 60) String headerVar,
        List<String> varKeys,
        boolean sharedAll,
        List<UUID> adminIds) {
}
