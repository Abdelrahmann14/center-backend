package com.center.registration.validation;

import java.util.regex.Pattern;

import com.center.common.exception.BusinessRuleException;

/**
 * Field rules for student self-registration, enforced server-side (the mobile
 * client mirrors them for UX, but the backend is the authority).
 */
public final class RegistrationValidation {

    /** One Arabic word: letters plus the common diacritic range. */
    private static final String ARABIC_WORD = "[\\u0600-\\u06FF\\u0750-\\u077F]+";
    /** Exactly four Arabic words separated by single spaces. */
    private static final Pattern FOUR_PART_ARABIC =
            Pattern.compile("^" + ARABIC_WORD + "( " + ARABIC_WORD + "){3}$");

    /** Exactly three Arabic words - a parent's full name. */
    private static final Pattern THREE_PART_ARABIC =
            Pattern.compile("^" + ARABIC_WORD + "( " + ARABIC_WORD + "){2}$");

    private static final Pattern ELEVEN_DIGITS = Pattern.compile("^\\d{11}$");

    private static final int PW_MIN = 8;
    private static final int PW_MAX = 16;
    /** ASCII letters, digits and specials - excludes space, '-' and any dash. */
    private static final Pattern PW_ALLOWED = Pattern.compile(
            "^[A-Za-z0-9!@#$%^&*()_+=\\[\\]{};:'\",.<>/?\\\\|~`]+$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private static final Pattern SIX_DIGITS = Pattern.compile("^\\d{6}$");

    private RegistrationValidation() {
    }

    /** Collapses inner whitespace and trims; the canonical stored/compared form. */
    public static String normaliseName(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ");
    }

    public static String requireFourPartArabicName(String name) {
        String cleaned = normaliseName(name);
        if (!FOUR_PART_ARABIC.matcher(cleaned).matches()) {
            throw new BusinessRuleException("الاسم يجب أن يكون رباعيًا بأحرف عربية فقط");
        }
        return cleaned;
    }

    public static String requireThreePartArabicName(String name) {
        String cleaned = normaliseName(name);
        if (!THREE_PART_ARABIC.matcher(cleaned).matches()) {
            throw new BusinessRuleException("الاسم يجب أن يكون ثلاثيًا بأحرف عربية فقط");
        }
        return cleaned;
    }

    /** Arabic words separated by single spaces - no Latin letters, no symbols. */
    private static final Pattern ARABIC_TEXT =
            Pattern.compile("^" + ARABIC_WORD + "( " + ARABIC_WORD + ")*$");

    public static final int SCHOOL_MIN = 5;
    public static final int SCHOOL_MAX = 25;

    public static String requireSchool(String school) {
        String cleaned = normaliseName(school);
        if (!ARABIC_TEXT.matcher(cleaned).matches()) {
            throw new BusinessRuleException("اسم المدرسة يجب أن يكون بأحرف عربية فقط");
        }
        if (cleaned.length() < SCHOOL_MIN || cleaned.length() > SCHOOL_MAX) {
            throw new BusinessRuleException(
                    "اسم المدرسة يجب أن يكون من " + SCHOOL_MIN + " إلى " + SCHOOL_MAX + " حرفًا");
        }
        return cleaned;
    }

    public static String requirePhone(String phone, String owner) {
        String digits = phone == null ? "" : phone.strip();
        if (!ELEVEN_DIGITS.matcher(digits).matches()) {
            throw new BusinessRuleException("رقم هاتف " + owner + " يجب أن يكون 11 رقمًا");
        }
        return digits;
    }

    public static void requireStrongPassword(String password) {
        String pw = password == null ? "" : password;
        if (pw.length() < PW_MIN || pw.length() > PW_MAX) {
            throw new BusinessRuleException("كلمة المرور يجب أن تكون بين 8 و16 خانة");
        }
        if (!PW_ALLOWED.matcher(pw).matches()) {
            throw new BusinessRuleException(
                    "كلمة المرور تسمح بأحرف إنجليزية وأرقام ورموز فقط، بدون مسافات أو شرطة أو أحرف عربية");
        }
        if (!HAS_LETTER.matcher(pw).find() || !HAS_DIGIT.matcher(pw).find()) {
            throw new BusinessRuleException("كلمة المرور يجب أن تحتوي على حرف ورقم على الأقل");
        }
    }

    public static boolean isSixDigitCode(String code) {
        return code != null && SIX_DIGITS.matcher(code).matches();
    }
}
