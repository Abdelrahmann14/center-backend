package com.center.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.user.entity.User;
import com.center.common.enums.Role;

public interface UserRepository extends JpaRepository<User, UUID> {

    // --- Authentication + global email uniqueness --------------------------
    // Email is the globally-unique login identifier for every account (V21).
    // Ownership lives in admin_id, resolved independently of the address.

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    // Display-name lookups. The username no longer authenticates, but student
    // display names still must not collide (they double as the student record's
    // name), and the bootstrap runner finds seeded accounts by it.

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    /** Active accounts of a role. */
    List<User> findByRoleAndActiveTrue(Role role);

    // --- Workspace-scoped (multi-tenant) lookups ---------------------------
    // Users are not @TenantId (login runs before a tenant is known), so their
    // workspace scoping is expressed explicitly via admin_id.

    /** A user by id, but only within the given workspace. */
    Optional<User> findByIdAndAdminId(UUID id, UUID adminId);

    /** Every user (assistant) owned by one admin. */
    List<User> findByAdminIdOrderByCreatedAtAsc(UUID adminId);

    /** Assistants of one admin, by role. */
    List<User> findByRoleAndAdminIdOrderByUsername(Role role, UUID adminId);

    /** Count of one admin's users of a role (e.g. their assistants). */
    long countByRoleAndAdminId(Role role, UUID adminId);

}
