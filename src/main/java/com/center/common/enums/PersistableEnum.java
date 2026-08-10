package com.center.common.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An enum whose persisted/wire form is a fixed literal rather than its Java
 * name - the database and the Arabic UI both store the literal.
 */
public interface PersistableEnum {

    @JsonValue
    String getValue();

    /**
     * Resolves a literal back to its constant.
     *
     * @return the matching constant, or {@code null} when {@code value} is null
     *         or blank (these columns are nullable)
     * @throws IllegalArgumentException when the literal is not part of the domain
     */
    static <E extends Enum<E> & PersistableEnum> E fromValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.getValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown %s value: %s".formatted(type.getSimpleName(), value)));
    }
}
