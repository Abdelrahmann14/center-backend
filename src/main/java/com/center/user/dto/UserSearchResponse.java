package com.center.user.dto;

import java.util.UUID;

import com.center.common.enums.Role;

/** One account in the super admin's name-search picker. */
public record UserSearchResponse(UUID id, String username, Role role, String photo) {
}
