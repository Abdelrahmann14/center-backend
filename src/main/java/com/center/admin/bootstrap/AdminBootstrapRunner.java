package com.center.admin.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.center.user.entity.User;
import com.center.common.enums.Role;
import com.center.user.repository.UserRepository;
import com.center.common.validation.EmailPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the two accounts that cannot be created through the app itself: the
 * super admin (the developer, root of the whole platform) and an admin (a
 * teacher, root of one workspace). Replaces the old make_admin.py.
 *
 * <p>Run it deliberately - each account is only touched when its own pair of
 * arguments is given:
 *
 * <pre>
 *   # the developer account
 *   java -jar center-server.jar --spring.profiles.active=bootstrap \
 *        --bootstrap.superadmin.username=root --bootstrap.superadmin.password=SECRET
 *
 *   # a teacher account (or reset its password)
 *   java -jar center-server.jar --spring.profiles.active=bootstrap \
 *        --bootstrap.admin.username=admin --bootstrap.admin.password=SECRET
 * </pre>
 *
 * <p>Guarded by the {@code bootstrap} profile so a normal start can never
 * silently rewrite a password, and so the credentials are not part of the
 * day-to-day configuration.
 */
@Component
@Profile("bootstrap")
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String SUPERADMIN_USERNAME_ARG = "bootstrap.superadmin.username";
    private static final String SUPERADMIN_PASSWORD_ARG = "bootstrap.superadmin.password";
    private static final String ADMIN_USERNAME_ARG = "bootstrap.admin.username";
    private static final String ADMIN_PASSWORD_ARG = "bootstrap.admin.password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean did = upsert(args, SUPERADMIN_USERNAME_ARG, SUPERADMIN_PASSWORD_ARG, Role.SUPER_ADMIN);
        did |= upsert(args, ADMIN_USERNAME_ARG, ADMIN_PASSWORD_ARG, Role.ADMIN);

        if (!did) {
            log.error("Nothing to do. Provide --{}/--{} and/or --{}/--{}",
                    SUPERADMIN_USERNAME_ARG, SUPERADMIN_PASSWORD_ARG,
                    ADMIN_USERNAME_ARG, ADMIN_PASSWORD_ARG);
        }
    }

    /** Creates or resets one account. Returns false when its args are absent. */
    private boolean upsert(ApplicationArguments args, String usernameArg, String passwordArg, Role role) {
        String username = firstValue(args, usernameArg);
        String password = firstValue(args, passwordArg);
        if (username == null || password == null) {
            return false;
        }

        // Seeded accounts are found by their display name; the login email is
        // derived from it (letters and digits only, plus the role's domain).
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setEmail(EmailPolicy.build(username.replaceAll("[^A-Za-z0-9]", ""), role));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        // Super admins and admins are both workspace roots - they own no admin.
        user.setAdminId(null);
        userRepository.save(user);

        // The password itself is never logged.
        log.info("{} '{}' is ready", role.getValue(), username);
        return true;
    }

    private static String firstValue(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
