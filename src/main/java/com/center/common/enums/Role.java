package com.center.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** users.role - constrained by a database check constraint. */
public enum Role implements PersistableEnum {

    /** The developer. Full cross-tenant control; owns no workspace data. */
    SUPER_ADMIN("super_admin"),
    /** A teacher. Root of one isolated workspace. */
    ADMIN("admin"),
    /** An assistant. Belongs to exactly one admin's workspace. */
    USER("user");

    /** Spring Security expects authorities to carry the ROLE_ prefix. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    @JsonCreator
    public static Role fromValue(String value) {
        return PersistableEnum.fromValue(Role.class, value);
    }
}
