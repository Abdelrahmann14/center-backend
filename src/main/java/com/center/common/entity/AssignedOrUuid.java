package com.center.common.entity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;

/**
 * Keep an id the caller already assigned; generate one only when it is absent.
 *
 * <p>Needed because an offline client mints the row's UUID itself: the row it
 * showed the user and the row the server stores have to be the SAME row. With a
 * plain {@code @GeneratedValue} the server minted a second id, the sync feed
 * brought that second row back down, and the client ended up holding the student
 * twice with no way to tell the copies apart.
 */
@IdGeneratorType(AssignedOrUuidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AssignedOrUuid {
}
