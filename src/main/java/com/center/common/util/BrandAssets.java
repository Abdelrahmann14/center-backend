package com.center.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * The platform brand mark, base64-encoded once for embedding in generated PDFs.
 * It is the same logo the web login page shows, copied into the backend as a
 * classpath resource so documents are self-contained (no external fetch).
 */
public final class BrandAssets {

    private static final String LOGO_PATH = "/images/center-logo.png";
    private static String logoBase64;

    private BrandAssets() {
    }

    /** The logo as a base64 PNG, or an empty string if the resource is missing. */
    public static synchronized String logoBase64() {
        if (logoBase64 == null) {
            try (InputStream in = BrandAssets.class.getResourceAsStream(LOGO_PATH)) {
                logoBase64 = in == null ? "" : Base64.getEncoder().encodeToString(in.readAllBytes());
            } catch (IOException ex) {
                logoBase64 = "";
            }
        }
        return logoBase64;
    }
}
