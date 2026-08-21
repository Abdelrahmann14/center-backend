package com.center.auth.service;
import com.center.auth.dto.AuthenticatedUserResponse;
import com.center.common.enums.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.auth.dto.LoginRequest;
import com.center.auth.dto.LoginResponse;
import com.center.auth.dto.SwitchTargetResponse;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.auth.security.AuthenticatedUser;
import com.center.auth.security.JwtService;
import com.center.auth.service.PrincipalViewFactory;
import com.center.common.tenant.TenantContext;
import com.center.common.util.PhotoCodec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements com.center.auth.service.AuthService {

    private static final String BAD_CREDENTIALS = "البريد الإلكتروني أو كلمة المرور غير صحيحة";
    private static final String BAD_ADMIN_PASSWORD = "كلمة مرور المدرّس غير صحيحة";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalViewFactory principalViewFactory;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = resolveUser(request)
                .filter(this::loginAllowed)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS));

        String token = jwtService.issue(AuthenticatedUser.from(user));
        log.info("User {} signed in (workspace {})", user.getUsername(), user.getAdminId());
        return new LoginResponse(token, principalViewFactory.of(user));
    }

    /**
     * Resolves the user by their globally-unique email, matched case-insensitively.
     * Role, owning Admin and permissions are derived from the resolved account,
     * never from the address.
     */
    private Optional<User> resolveUser(LoginRequest request) {
        return userRepository.findByEmailIgnoreCase(request.email().strip());
    }

    @Override
    @Transactional(readOnly = true)
    public com.center.auth.dto.AuthenticatedUserResponse me(UUID userId) {
        // Restoring a stored session: the token proves identity, but role and
        // active state are re-read so a revoked account cannot resume.
        User user = userRepository.findById(userId)
                .filter(this::loginAllowed)
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS));
        return principalViewFactory.of(user);
    }

    /**
     * A user may sign in only if their own account is active and - for an
     * assistant/student - their owning Admin's workspace is active too. Returned
     * as a plain boolean so a disabled account fails with the same generic error
     * as a wrong password, never revealing which.
     */
    private boolean loginAllowed(User user) {
        if (!user.isActive()) {
            return false;
        }
        UUID owner = user.getAdminId();
        if (owner == null) {
            return true; // a root (admin / super admin) has no owning workspace
        }
        return userRepository.findById(owner).map(User::isActive).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyAdminPassword(String password) {
        // The gate confirms THIS workspace's admin - the tenant bound to the
        // request - not some arbitrary first admin now that many exist. The
        // tenant id is exactly the owning admin's user id.
        UUID adminId = TenantContext.get();
        boolean matches = adminId != null
                && userRepository.findById(adminId)
                        .map(admin -> passwordEncoder.matches(password, admin.getPasswordHash()))
                        .orElse(false);
        if (!matches) {
            throw new BadCredentialsException(BAD_ADMIN_PASSWORD);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SwitchTargetResponse> switchTargets(UUID callerId) {
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS));
        UUID workspace = workspaceOf(caller);
        // A super admin owns no workspace - only their own account is a target.
        if (workspace == null) {
            return List.of(toTarget(caller, callerId));
        }
        // Admin first (the workspace root), then their assistants, active only -
        // a disabled account can never be switched into, so it is not offered.
        List<SwitchTargetResponse> targets = new ArrayList<>();
        userRepository.findById(workspace)
                .filter(User::isActive)
                .ifPresent(admin -> targets.add(toTarget(admin, callerId)));
        userRepository.findByRoleAndAdminIdOrderByUsername(Role.USER, workspace).stream()
                .filter(User::isActive)
                .forEach(assistant -> targets.add(toTarget(assistant, callerId)));
        return targets;
    }

    @Override
    @Transactional
    public LoginResponse switchAccount(UUID callerId, UUID targetUserId, String password) {
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS));
        UUID workspace = workspaceOf(caller);

        // Every guard fails with the same generic error so a probe cannot tell a
        // wrong password from a target it is not allowed to reach.
        User target = userRepository.findById(targetUserId)
                // Only an admin or an assistant is ever a switch target.
                .filter(candidate -> candidate.getRole() == Role.ADMIN || candidate.getRole() == Role.USER)
                // Same workspace only: admin<->assistant and assistant<->sibling.
                .filter(candidate -> workspace != null && workspace.equals(workspaceOf(candidate)))
                .filter(this::loginAllowed)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS));

        String token = jwtService.issue(AuthenticatedUser.from(target));
        log.info("User {} switched to {} (workspace {})", caller.getUsername(), target.getUsername(), workspace);
        return new LoginResponse(token, principalViewFactory.of(target));
    }

    /** The workspace a user acts within: an admin is its root, others point at it. */
    private static UUID workspaceOf(User user) {
        return user.getRole() == Role.ADMIN ? user.getId() : user.getAdminId();
    }

    private static SwitchTargetResponse toTarget(User user, UUID callerId) {
        return new SwitchTargetResponse(
                user.getId(), user.getUsername(), user.getRole(), user.getId().equals(callerId),
                PhotoCodec.toDataUrl(user.getPhotoData(), user.getPhotoType()));
    }
}
