package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.gcrel.tfbackevent.auth.controller.AuthController;
import org.gcrel.tfbackevent.auth.dto.request.LoginRequest;
import org.gcrel.tfbackevent.auth.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.auth.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.auth.dto.request.VerifyEmailRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.EmailNotVerifiedException;
import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.exception.InvalidVerificationCodeException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.AuthCookies;
import org.gcrel.tfbackevent.auth.service.AuthService;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthController.class, properties = {
        "cookie.secure=true", "cookie.same-site=Strict", "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000" })
@Import({ GlobalExceptionHandler.class, SecurityConfig.class, AuthCookies.class })
@AutoConfigureMockMvc
public class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testUser = new User("John Doe", "test@example.com", "password", LocalDate.of(1990, 1, 1));
    }

    // ==========================================
    // /register
    // ==========================================

    @Test
    void register_Success_Returns201() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "John Doe", "test@example.com", "password", LocalDate.of(1990, 1, 1));

        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void register_EmailAlreadyExists_Returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("John Doe", "test@example.com", "password",
                LocalDate.of(1990, 1, 1));

        doThrow(new EmailAlreadyExistsException(request.email()))
                .when(authService)
                .register(any(RegisterRequest.class));

        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error")
                                .value("El email ya está registrado: " + request.email()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ==========================================
    // /verify
    // ==========================================

    @Test
    void verifyEmail_Success_Returns204AndSetsSessionCookies() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest("123456", "test@example.com");
        AuthResponse authResponse = new AuthResponse("access_token", "refresh_token");

        when(authService.verifyEmail(anyString(), anyString())).thenReturn(authResponse);

        mockMvc.perform(
                post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(sessionCookies());
    }

    @Test
    void verifyEmail_InvalidCode_Returns400() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest("test@example.com", "wrong_code");

        when(authService.verifyEmail(anyString(), anyString()))
                .thenThrow(new InvalidVerificationCodeException());

        mockMvc.perform(
                post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ==========================================
    // /login
    // ==========================================

    @Test
    void login_Success_Returns204AndSetsSessionCookies() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        AuthResponse authResponse = new AuthResponse("access_token", "refresh_token");

        when(authService.login(anyString(), anyString())).thenReturn(authResponse);

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(sessionCookies());
    }

    @Test
    void login_EmailNotVerified_Returns403() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password");

        when(authService.login(anyString(), anyString()))
                .thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.error")
                                .value("El correo electrónico no ha sido verificado."))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void login_InvalidCredentials_Returns401() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "wrong_password");

        when(authService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciales inválidas."))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ==========================================
    // Protected (/logout, /me)
    // ==========================================

    @Test
    void refresh_WithCookie_Returns204AndRenewsOnlyTheAccessCookie() throws Exception {
        when(authService.refresh(any(RefreshTokenRequest.class)))
                .thenReturn(new AuthResponse("new_access", "refresh_token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(AuthCookies.REFRESH, "123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(status().isNoContent())
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertEquals(1, cookies.size());
                    assertTrue(cookies.get(0).startsWith("access_token=new_access;"));
                });
        verify(authService).refresh(new RefreshTokenRequest("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void refresh_WithoutCookie_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).refresh(any());
    }

    @Test
    void logout_WithoutAccessToken_StillClearsCookies() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new Cookie(AuthCookies.REFRESH, "123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(status().isNoContent())
                .andExpect(result -> {
                    List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertEquals(2, cookies.size());
                    cookies.forEach(cookie -> assertTrue(cookie.contains("Max-Age=0"), cookie));
                });
        verify(authService).logout("123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    void logout_WithoutCookie_StillReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
        verify(authService).logout(null);
    }

    // access_token en /api y refresh_token solo en /api/v1/auth, los dos HttpOnly,
    // Secure y Strict.
    private static ResultMatcher sessionCookies() {
        return result -> {
            List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
            assertEquals(2, cookies.size());
            String access = cookies.stream().filter(c -> c.startsWith("access_token=access_token;")).findFirst()
                    .orElseThrow();
            String refresh = cookies.stream().filter(c -> c.startsWith("refresh_token=refresh_token;")).findFirst()
                    .orElseThrow();
            for (String cookie : cookies) {
                assertTrue(cookie.contains("HttpOnly"), cookie);
                assertTrue(cookie.contains("Secure"), cookie);
                assertTrue(cookie.contains("SameSite=Strict"), cookie);
            }
            assertTrue(access.contains("Path=/api;") && access.contains("Max-Age=1800"), access);
            assertTrue(refresh.contains("Path=/api/v1/auth;") && refresh.contains("Max-Age=604800"), refresh);
        };
    }

    @Test
    void me_Success_ReturnsUserDetails() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken(testUser, null,
                testUser.getAuthorities());

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }
}
