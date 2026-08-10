package com.center.auth.service;
import com.center.auth.dto.AuthenticatedUserResponse;
import com.center.common.enums.Role;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.auth.dto.LoginRequest;
import com.center.auth.dto.LoginResponse;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.auth.security.AuthenticatedUser;
import com.center.auth.security.JwtService;
import com.center.auth.service.PrincipalViewFactory;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements com.center.auth.service.AuthService {

    private static final String BAD_CREDENTIALS = "البريد الإلكتروني أو كلمة المرور غير صحيحة";
    private static final String BAD_ADMIN_PASSWORD = "كلمة مرور المدير غير صحيحة";

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
}
