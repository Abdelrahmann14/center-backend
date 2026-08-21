package com.center.whatsapp.cloud.dto;

import jakarta.validation.constraints.Pattern;

/**
 * How Meta should deliver the verification code to the number being provisioned.
 *
 * @param method {@code SMS} or {@code VOICE}. A number that cannot receive SMS -
 *               a landline, or a line the operator blocks short codes on - still
 *               works over VOICE, which is the whole reason this is a choice.
 */
public record CloudCodeRequest(
        @Pattern(regexp = "SMS|VOICE", message = "SMS أو VOICE فقط") String method) {

    public String methodOrDefault() {
        return method == null || method.isBlank() ? "SMS" : method;
    }
}
