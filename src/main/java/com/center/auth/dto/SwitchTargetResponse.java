package com.center.auth.dto;

import java.util.UUID;

import com.center.common.enums.Role;

/**
 * One account the signed-in user may switch into: their own account, their
 * owning admin, or a sibling account in the same workspace. Never carries a
 * secret - switching still requires that account's password.
 */
public record SwitchTargetResponse(UUID id, String username, Role role, boolean current, String photo) {
}
