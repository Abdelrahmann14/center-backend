package com.center.common.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.center.common.exception.BusinessRuleException;

/**
 * The Arabic font every generated PDF embeds, read from the jar once.
 *
 * <p>Each of the three PDF renderers used to open the classpath resource on
 * every single render. The file is 434 KB, so a run of report cards re-read and
 * re-parsed 434 KB per document, on a request thread, for a byte sequence that
 * cannot change while the process is alive. Holding the bytes and handing out a
 * fresh stream over them costs one copy of the array and removes the repeated
 * I/O and the repeated allocation churn.
 *
 * <p>A stream rather than the array itself, because openhtmltopdf consumes and
 * closes what it is given - callers must never share one.
 */
public final class PdfFont {

    public static final String FAMILY = "Noto Kufi Arabic";

    private static final String PATH = "/fonts/NotoKufiArabic.ttf";

    private static volatile byte[] bytes;

    private PdfFont() {
    }

    /** A fresh stream over the font. Throws when the resource is missing. */
    public static InputStream stream() {
        return new ByteArrayInputStream(load());
    }

    /**
     * The raw bytes, for callers that need them directly (the Java2D message
     * image renderer). Never mutate the returned array.
     */
    public static byte[] bytes() {
        return load();
    }

    private static byte[] load() {
        byte[] local = bytes;
        if (local != null) {
            return local;
        }
        // A benign race here loads the font twice and keeps one; a lock on every
        // render to avoid that would cost more than the duplicate ever could.
        try (InputStream in = PdfFont.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new BusinessRuleException("خط المستندات غير متوفر على الخادم");
            }
            local = in.readAllBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("تعذّر تحميل خط المستندات");
        }
        bytes = local;
        return local;
    }
}
