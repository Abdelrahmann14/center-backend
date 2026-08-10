package com.center.account.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.account.dto.EmailAvailabilityResponse;
import com.center.common.enums.Role;
import com.center.user.repository.UserRepository;
import com.center.common.validation.EmailPolicy;

import lombok.RequiredArgsConstructor;

/**
 * Tells a signup/creation form whether a login name is free, and offers valid
 * alternatives when it is not - so the clash is shown while typing instead of
 * after a failed submit.
 */
@Service
@RequiredArgsConstructor
public class AccountEmailService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse check(String localPart, Role role) {
        if (!EmailPolicy.isValidLocalPart(localPart)) {
            // Invalid input still gets suggestions, built from whatever letters
            // and digits it did contain (e.g. "abdel rahman" -> "abdelrahman1").
            return new EmailAvailabilityResponse(false, null, false, suggestions(localPart, role));
        }
        String email = EmailPolicy.build(localPart, role);
        boolean taken = userRepository.existsByEmailIgnoreCase(email);
        return new EmailAvailabilityResponse(
                !taken, email, true, taken ? suggestions(localPart, role) : List.of());
    }

    private List<String> suggestions(String localPart, Role role) {
        return EmailPolicy.suggestions(localPart, role, userRepository::existsByEmailIgnoreCase);
    }
}
