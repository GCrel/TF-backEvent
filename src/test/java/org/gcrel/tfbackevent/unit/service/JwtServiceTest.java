package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.JwtException;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.gcrel.tfbackevent.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

class JwtServiceTest {

    private JwtService jwtService;
    private User mockUser;

    // Secret key
    private final String TEST_SECRET =
            "38296b4ba0ba14cede158bdeb41bd35d3448b9900689481573c7b130b1a0392573548f";
    private final long ACCESS_TOKEN_EXPIRATION = 3600000; // 1h in ms
    private final long REFRESH_TOKEN_EXPIRATION = 86400000; // 1d in ms

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        // Inject Values
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", ACCESS_TOKEN_EXPIRATION);
        ReflectionTestUtils.setField(
                jwtService, "refreshTokenExpiration", REFRESH_TOKEN_EXPIRATION);

        mockUser = mock(User.class);
        when(mockUser.getEmail()).thenReturn("test@example.com");
        when(mockUser.getSystemRole()).thenReturn(SystemRole.USER);
    }

    // ==========================================
    // ACCESS TOKEN Test
    // ==========================================

    @Test
    void generateAccessToken_ReturnsValidJwt() {
        String token = jwtService.generateAccessToken(mockUser);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        // valid JWT
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void isAccessTokenValid_WithValidToken_ReturnsTrue() {
        String token = jwtService.generateAccessToken(mockUser);

        boolean isValid = jwtService.isAccessTokenValid(token);

        assertTrue(isValid);
    }

    @Test
    void isAccessTokenValid_WithInvalidToken_ReturnsFalse() {
        String invalidToken = "este.es.un.token.invalido";

        boolean isValid = jwtService.isAccessTokenValid(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void isAccessTokenValid_WithExpiredToken_ReturnsFalse() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", -1000L);
        String expiredToken = jwtService.generateAccessToken(mockUser);

        boolean isValid = jwtService.isAccessTokenValid(expiredToken);

        assertFalse(isValid);
    }

    // ==========================================
    // Extract CLAIMS
    // ==========================================

    @Test
    void extractEmail_ReturnsCorrectEmail() {
        String token = jwtService.generateAccessToken(mockUser);

        String extractedEmail = jwtService.extractEmail(token);

        assertEquals("test@example.com", extractedEmail);
    }

    @Test
    void extractRole_ReturnsCorrectRole() {
        String token = jwtService.generateAccessToken(mockUser);

        SystemRole extractedRole = jwtService.extractRole(token);

        assertEquals(SystemRole.USER, extractedRole);
    }

    @Test
    void extractEmail_WithMalformedToken_ThrowsException() {
        String malformedToken = "token_mal_formado";

        assertThrows(JwtException.class, () -> jwtService.extractEmail(malformedToken));
    }

    // ==========================================
    // REFRESH TOKEN Test
    // ==========================================

    @Test
    void generateRefreshToken_ReturnsValidUUID() {
        String refreshToken = jwtService.generateRefreshToken();

        assertNotNull(refreshToken);
        assertDoesNotThrow(() -> UUID.fromString(refreshToken));
    }

    @Test
    void getRefreshTokenExpiresAt_ReturnsFutureDate() {
        LocalDateTime expectedMinTime =
                LocalDateTime.now().plusSeconds((REFRESH_TOKEN_EXPIRATION / 1000) - 1);
        LocalDateTime expectedMaxTime =
                LocalDateTime.now().plusSeconds((REFRESH_TOKEN_EXPIRATION / 1000) + 1);

        LocalDateTime expiresAt = jwtService.getRefreshTokenExpiresAt();

        assertNotNull(expiresAt);
        assertTrue(expiresAt.isAfter(expectedMinTime));
        assertTrue(expiresAt.isBefore(expectedMaxTime));
    }
}
