package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.gcrel.tfbackevent.auth.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.auth.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.EmailNotVerifiedException;
import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.exception.InvalidTokenException;
import org.gcrel.tfbackevent.auth.exception.VerificationCodeExpiredException;
import org.gcrel.tfbackevent.auth.repository.RefreshTokenRepository;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.AuthService;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.model.RefreshToken;
import org.gcrel.tfbackevent.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private final String TEST_EMAIL = "test@example.com";
    private final String TEST_PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        testUser = new User("John Doe", TEST_EMAIL, "encoded_password", LocalDate.of(1990, 1, 1));
    }

    // ==========================================
    // REGISTER
    // ==========================================
    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest(
                "John Doe", TEST_EMAIL, TEST_PASSWORD, LocalDate.of(1990, 1, 1));

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("encoded_password");

        authService.register(request);

        // We verify that the username is saved and the email is sent
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(emailService).sendVerificationEmail(eq(TEST_EMAIL), anyString());

        User savedUser = userCaptor.getValue();
        assertEquals(TEST_EMAIL, savedUser.getEmail());
        assertNotNull(savedUser.getVerificationCode());
    }

    @Test
    void register_ThrowsEmailAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest(
                "John Doe", TEST_EMAIL, TEST_PASSWORD, LocalDate.of(1990, 1, 1));

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // VERIFY EMAIL
    // ==========================================
    @Test
    void verifyEmail_Success() {
        testUser.setVerificationCode("123456");
        testUser.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
        testUser.setEnabled(false);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(testUser)).thenReturn("access_token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh_token");

        AuthResponse response = authService.verifyEmail(TEST_EMAIL, "123456");

        assertTrue(testUser.isEnabled());
        assertNull(testUser.getVerificationCode());
        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
        verify(userRepository).save(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void verifyEmail_ThrowsVerificationCodeExpiredException() {
        testUser.setVerificationCode("123456");
        testUser.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(5)); // Expired
        testUser.setEnabled(false);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));

        assertThrows(
                VerificationCodeExpiredException.class,
                () -> authService.verifyEmail(TEST_EMAIL, "123456"));
    }

    // ==========================================
    // LOGIN
    // ==========================================
    @Test
    void login_Success() {
        testUser.setEnabled(true);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(TEST_PASSWORD, testUser.getPassword())).thenReturn(true);
        when(jwtService.generateAccessToken(testUser)).thenReturn("access_token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh_token");

        AuthResponse response = authService.login(TEST_EMAIL, TEST_PASSWORD);

        assertNotNull(response);
        assertEquals("access_token", response.accessToken());
        verify(refreshTokenRepository)
                .deleteAllByUser(testUser); // Verify that it clears previous tokens
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // Save the new token
    }

    @Test
    void login_ThrowsEmailNotVerifiedException() {
        testUser.setEnabled(false); // As verified

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));

        assertThrows(
                EmailNotVerifiedException.class,
                () -> authService.login(TEST_EMAIL, TEST_PASSWORD));
    }

    @Test
    void login_ThrowsInvalidCredentialsException_BadPassword() {
        testUser.setEnabled(true);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", testUser.getPassword())).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(TEST_EMAIL, "wrong_password"));
    }

    // ==========================================
    // REFRESH
    // ==========================================
    @Test
    void refresh_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("old_refresh_token");
        RefreshToken token = new RefreshToken("old_refresh_token", LocalDateTime.now().plusDays(1), testUser);

        when(refreshTokenRepository.findByToken("old_refresh_token"))
                .thenReturn(Optional.of(token));
        when(jwtService.generateAccessToken(testUser)).thenReturn("new_access_token");

        AuthResponse response = authService.refresh(request);

        assertEquals("new_access_token", response.accessToken());
        assertEquals("old_refresh_token", response.refreshToken());
    }

    @Test
    void refresh_ThrowsInvalidTokenException_WhenExpired() {
        RefreshTokenRequest request = new RefreshTokenRequest("old_refresh_token");
        // Token has expired
        RefreshToken token = new RefreshToken("old_refresh_token", LocalDateTime.now().minusDays(1), testUser);

        when(refreshTokenRepository.findByToken("old_refresh_token"))
                .thenReturn(Optional.of(token));

        assertThrows(InvalidTokenException.class, () -> authService.refresh(request));
    }

    // ==========================================
    // LOGOUT
    // ==========================================
    @Test
    void logout_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");
        RefreshToken token = new RefreshToken("valid_refresh_token", LocalDateTime.now().plusDays(1), testUser);

        when(refreshTokenRepository.findByToken("valid_refresh_token"))
                .thenReturn(Optional.of(token));

        authService.logout(request);

        verify(refreshTokenRepository).delete(token);
    }
}
