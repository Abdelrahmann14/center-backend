package com.center.account.dto;
import com.center.parent.entity.Parent;
import com.center.student.entity.Student;

import java.util.UUID;

import com.center.common.enums.Role;

/**
 * The unified account page, for every role.
 *
 * @param code  the account's own number - Parent Code for a parent, Student Code
 *              for a student; null for admin/assistant accounts
 * @param phone the account's own phone - the parent's trusted number, or the
 *              student's own number; null when the role has none
 */
public record AccountResponse(
        UUID id,
        String username,
        String email,
        Role role,
        Integer code,
        String phone) {
}
