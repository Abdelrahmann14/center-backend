package com.center.common.validation;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;

/**
 * Every account signs in with an email. The user only ever types the part BEFORE
 * the domain; the domain is derived from their role and appended by the server,
 * so a student can never mint an admin address.
 */
public final class EmailPolicy {

    public static final String STUDENT_DOMAIN = "@center.student.com";
    public static final String ADMIN_DOMAIN = "@center.admin.com";
    public static final String ASSISTANT_DOMAIN = "@center.assistant.com";
    public static final String PARENT_DOMAIN = "@center.parent.com";

    /** English letters and digits only - no spaces, no underscores, no symbols. */
    private static final Pattern LOCAL_PART = Pattern.compile("^[A-Za-z0-9]+$");
    /** Both kinds must appear: never all letters, never all digits. */
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");
    /** Length limits apply to the typed part only, never to the domain. */
    public static final int LOCAL_PART_MIN = 5;
    public static final int LOCAL_PART_MAX = 20;

    private static final String INVALID =
            "البريد الإلكتروني: من 5 إلى 20 خانة، ويجب أن يجمع بين أحرف إنجليزية وأرقام، بدون مسافات أو رموز";

    private EmailPolicy() {
    }

    public static String domainFor(Role role) {
        return switch (role) {
            case STUDENT -> STUDENT_DOMAIN;
            case PARENT -> PARENT_DOMAIN;
            case USER -> ASSISTANT_DOMAIN;
            case ADMIN, SUPER_ADMIN -> ADMIN_DOMAIN;
        };
    }

    /** Validates the user-typed part and returns it stripped. */
    public static String requireLocalPart(String raw) {
        String local = raw == null ? "" : raw.strip();
        if (!isValidLocalPart(local)) {
            throw new BusinessRuleException(INVALID);
        }
        return local;
    }

    public static boolean isValidLocalPart(String raw) {
        String local = raw == null ? "" : raw.strip();
        return local.length() >= LOCAL_PART_MIN
                && local.length() <= LOCAL_PART_MAX
                && LOCAL_PART.matcher(local).matches()
                && HAS_LETTER.matcher(local).matches()
                && HAS_DIGIT.matcher(local).matches();
    }

    /** Builds the full address a role's account signs in with. */
    public static String build(String localPart, Role role) {
        return requireLocalPart(localPart) + domainFor(role);
    }

    /** The part before '@' - what the UI shows and lets the user edit. */
    public static String localPartOf(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    /**
     * Alternatives for a taken name, e.g. "abdelrahman123" ->
     * "abdelrahman1231", "abdelrahman1232026", "abdelrahman12388".
     *
     * <p>Only candidates that satisfy the same rules AND are actually free are
     * returned - {@code taken} is asked about the FULL address, so a name free in
     * one role's domain is still offered even if used in another.
     */
    public static List<String> suggestions(String rawLocal, Role role, Predicate<String> taken) {
        String base = rawLocal == null ? "" : rawLocal.strip().replaceAll("[^A-Za-z0-9]", "");
        if (base.isEmpty()) {
            return List.of();
        }
        int year = Year.now().getValue();

        // Ordered, de-duplicated pool: short numeric tails first, then the year,
        // then a widening counter so we never run out.
        Set<String> pool = new LinkedHashSet<>();
        pool.add(base + "1");
        pool.add(base + year);
        pool.add(base + "01");
        pool.add(base + "88");
        pool.add(base + "123");
        for (int i = 2; i <= 40; i++) {
            pool.add(base + i);
        }

        List<String> free = new ArrayList<>();
        for (String candidate : pool) {
            if (free.size() == 4) {
                break;
            }
            if (isValidLocalPart(candidate) && !taken.test(candidate + domainFor(role))) {
                free.add(candidate);
            }
        }
        return List.copyOf(free);
    }
}
