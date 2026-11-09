package org.gcrel.tfbackevent.auth.service;

import java.time.Duration;

import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final boolean secure;
    private final String sameSite;
    private final Duration accessMaxAge;
    private final Duration refreshMaxAge;

    public AuthCookies(
            @Value("${cookie.secure:true}") boolean secure,
            @Value("${cookie.same-site:Strict}") String sameSite,
            @Value("${jwt.access-token.expiration}") long accessExpirationMs,
            @Value("${jwt.refresh-token.expiration}") long refreshExpirationMs) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.accessMaxAge = Duration.ofMillis(accessExpirationMs);
        this.refreshMaxAge = Duration.ofMillis(refreshExpirationMs);
    }

    public HttpHeaders session(AuthResponse tokens) {
        HttpHeaders headers = accessOnly(tokens.accessToken());
        headers.add(HttpHeaders.SET_COOKIE, build(REFRESH, tokens.refreshToken(), REFRESH_PATH, refreshMaxAge));
        return headers;
    }

    public HttpHeaders accessOnly(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, build(ACCESS, accessToken, ACCESS_PATH, accessMaxAge));
        return headers;
    }

    public HttpHeaders cleared() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, build(ACCESS, "", ACCESS_PATH, Duration.ZERO));
        headers.add(HttpHeaders.SET_COOKIE, build(REFRESH, "", REFRESH_PATH, Duration.ZERO));
        return headers;
    }

    private String build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
