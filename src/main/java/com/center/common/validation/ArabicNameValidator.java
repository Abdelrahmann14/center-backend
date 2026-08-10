package com.center.common.validation;

import java.util.regex.Pattern;

import com.center.common.constants.ValidationRules;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ArabicNameValidator implements ConstraintValidator<ArabicName, String> {

    private static final Pattern PATTERN = Pattern.compile(ValidationRules.ARABIC_NAME_PATTERN);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Emptiness is @NotBlank's job; reporting it twice would be noise.
        if (value == null || value.isBlank()) {
            return true;
        }
        return PATTERN.matcher(value.strip()).matches();
    }
}
