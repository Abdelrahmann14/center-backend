package com.center.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Arabic-letter words separated by single spaces. Null/blank is left to @NotBlank. */
@Documented
@Constraint(validatedBy = ArabicNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ArabicName {

    String message() default "الاسم يجب أن يحتوي على حروف عربية فقط";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
