package com.center.google.client;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.center.common.config.ApplicationProperties;
import com.center.common.exception.BusinessRuleException;

import lombok.extern.slf4j.Slf4j;

/**
 * Google OAuth 2.0 (authorization-code) for connecting an admin's Google account:
 * build the consent URL, exchange the code for tokens, refresh the access token,
 * and read the account email. The client secret comes from {@code .env}.
 */
@Component
@Slf4j
public class GoogleOAuthClient {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    // openid+email to identify the account; contacts (read/write) for People API.
    private static final String SCOPE = "openid email https://www.googleapis.com/auth/contacts";

    private final ApplicationProperties.Google config;
    private final RestClient rest = RestClient.create();

    public GoogleOAuthClient(ApplicationProperties properties) {
        this.config = properties.google();
    }

    public boolean configured() {
        return config.configured();
    }

    /** The Google consent URL to redirect the admin to. */
    public String authUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {}

    /** Exchange the authorization code for access + refresh tokens. */
    @SuppressWarnings("unchecked")
    public Tokens exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("redirect_uri", config.redirectUri());
        form.add("grant_type", "authorization_code");
        try {
            Map<String, Object> res = rest.post().uri(TOKEN_URL)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            String access = str(res, "access_token");
            String refresh = str(res, "refresh_token");
            long expires = longOf(res, "expires_in");
            if (access == null || refresh == null) {
                throw new BusinessRuleException("لم تُرجِع Google رمز الوصول، حاول مرة أخرى");
            }
            return new Tokens(access, refresh, expires);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Google token exchange failed: {}", ex.getMessage());
            throw new BusinessRuleException("تعذّر ربط حساب Google، حاول مرة أخرى");
        }
    }

    /** Refresh an access token from a stored refresh token. */
    @SuppressWarnings("unchecked")
    public Tokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        try {
            Map<String, Object> res = rest.post().uri(TOKEN_URL)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return new Tokens(str(res, "access_token"), refreshToken, longOf(res, "expires_in"));
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Google token refresh failed: {}", ex.getMessage());
            throw new BusinessRuleException("انتهت صلاحية ربط حساب Google، أعد الربط");
        }
    }

    /** The email of the account that granted access. */
    @SuppressWarnings("unchecked")
    public String userEmail(String accessToken) {
        try {
            Map<String, Object> res = rest.get().uri(USERINFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            return str(res, "email");
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Google userinfo failed: {}", ex.getMessage());
            return null;
        }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static long longOf(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        return v instanceof Number n ? n.longValue() : 3600L;
    }
}
