package com.center.auth.service;

import java.util.List;
import java.util.UUID;

import com.center.auth.dto.LoginRequest;
import com.center.auth.dto.AuthenticatedUserResponse;
import com.center.auth.dto.LoginResponse;
import com.center.auth.dto.SwitchTargetResponse;

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

    /**
     * The accounts the caller may switch into: their own account, their owning
     * admin, and every sibling account in the same workspace. Active accounts
     * only - a disabled account cannot be a switch target.
     */
    List<SwitchTargetResponse> switchTargets(UUID callerId);

    /**
     * Issues a fresh token for another account in the caller's workspace after
     * confirming that account's own password. The old token stays valid until it
     * expires, so no sign-out is needed. Admin and assistant accounts of one
     * workspace can switch between each other; students/parents are never a
     * target.
     *
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         when the target is out of the workspace, disabled, not switchable,
     *         or the password is wrong (all reported the same, to reveal nothing)
     */
    LoginResponse switchAccount(UUID callerId, UUID targetUserId, String password);
}
