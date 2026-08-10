package com.center.auth.service;

import java.util.UUID;

import com.center.auth.dto.LoginRequest;
import com.center.auth.dto.AuthenticatedUserResponse;
import com.center.auth.dto.LoginResponse;

public interface AuthService {

    /**
     * Verifies credentials and issues a token.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         when the email or password is wrong
     */
    LoginResponse login(LoginRequest request);

    /**
     * The account behind a still-valid token - used by clients that persist the
     * session and restore it on launch.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         when the account no longer exists or has been deactivated
     */
    AuthenticatedUserResponse me(UUID userId);

    /**
     * Confirms the admin password, gating sensitive changes.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         when it does not match
     */
    void verifyAdminPassword(String password);
}
