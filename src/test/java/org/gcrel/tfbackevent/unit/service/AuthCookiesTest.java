package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.service.AuthCookies;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AuthCookiesTest {
    private final AuthResponse tokens = new AuthResponse("jwt", "refresh");

    @Test
    void session_SetsHttpOnlySecureStrictCookiesWithTheirPathAndLifetime() {
        List<String> cookies = new AuthCookies(true, "Strict", 1_800_000, 604_800_000)
                .session(tokens).get(HttpHeaders.SET_COOKIE);

        assertEquals(2, cookies.size());
        String access = cookies.get(0);
        String refresh = cookies.get(1);
        assertTrue(access.startsWith("access_token=jwt;") && access.contains("Path=/api;")
                && access.contains("Max-Age=1800"));
        assertTrue(refresh.startsWith("refresh_token=refresh;") && refresh.contains("Path=/api/v1/auth;")
                && refresh.contains("Max-Age=604800"));
        cookies.forEach(cookie -> {
            assertTrue(cookie.contains("HttpOnly"), cookie);
            assertTrue(cookie.contains("Secure"), cookie);
            assertTrue(cookie.contains("SameSite=Strict"), cookie);
        });
    }

    @Test
    void session_HonorsConfiguredSecureAndSameSite() {
        List<String> cookies = new AuthCookies(false, "None", 1_000, 1_000)
                .session(tokens).get(HttpHeaders.SET_COOKIE);

        cookies.forEach(cookie -> {
            assertFalse(cookie.contains("Secure"), cookie);
            assertTrue(cookie.contains("SameSite=None"), cookie);
            assertTrue(cookie.contains("HttpOnly"), cookie);
        });
    }

    @Test
    void cleared_ExpiresBothCookiesOnTheSamePaths() {
        List<String> cookies = new AuthCookies(true, "Strict", 1_800_000, 604_800_000)
                .cleared().get(HttpHeaders.SET_COOKIE);

        assertEquals(2, cookies.size());
        assertTrue(cookies.get(0).startsWith("access_token=;") && cookies.get(0).contains("Path=/api;"));
        assertTrue(cookies.get(1).startsWith("refresh_token=;") && cookies.get(1).contains("Path=/api/v1/auth;"));
        cookies.forEach(cookie -> assertTrue(cookie.contains("Max-Age=0"), cookie));
    }
}
