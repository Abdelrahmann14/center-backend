package com.center.common.util;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.center.common.exception.BusinessRuleException;

/**
 * Encodes/decodes profile photos as base64 data URLs. Photos ride inside JSON
 * (no separate image endpoint), so uploads are size-capped.
 */
public final class PhotoCodec {

    private PhotoCodec() {}

    /** Max decoded bytes. Avatars are small; this keeps JSON payloads sane. */
    private static final int MAX_BYTES = 512 * 1024;

    private static final Pattern DATA_URL =
            Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    private static final String INVALID = "صورة غير صالحة";
    private static final String TOO_BIG = "حجم الصورة كبير جدًا";

    public record Decoded(byte[] bytes, String type) {}

    /** Parse a {@code data:image/...;base64,...} URL, capping the decoded size. */
    public static Decoded decode(String dataUrl) {
        if (dataUrl == null) {
            throw new BusinessRuleException(INVALID);
        }
        Matcher m = DATA_URL.matcher(dataUrl.strip());
        if (!m.matches()) {
            throw new BusinessRuleException(INVALID);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(INVALID);
        }
        if (bytes.length == 0) {
            throw new BusinessRuleException(INVALID);
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException(TOO_BIG);
        }
        return new Decoded(bytes, m.group(1));
    }

    /** Build a data URL from stored bytes, or null when there is no photo. */
    public static String toDataUrl(byte[] bytes, String type) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String mime = type == null || type.isBlank() ? "image/png" : type;
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
