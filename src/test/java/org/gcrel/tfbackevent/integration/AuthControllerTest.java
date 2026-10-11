package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@WebMvcTest(controllers = AuthController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
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
    void verifyEmail_Success_Returns200AndTokens() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest("123456", "test@example.com");
        AuthResponse authResponse = new AuthResponse("access_token", "refresh_token");

        when(authService.verifyEmail(anyString(), anyString())).thenReturn(authResponse);

        mockMvc.perform(
                post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh_token"));
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
    void login_Success_Returns200AndTokens() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password");
        AuthResponse authResponse = new AuthResponse("access_token", "refresh_token");

        when(authService.login(anyString(), anyString())).thenReturn(authResponse);

        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token"));
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
    void logout_Success_Returns204() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("123e4567-e89b-12d3-a456-426614174000");
        Authentication authentication = new UsernamePasswordAuthenticationToken(testUser, null,
                testUser.getAuthorities());

        doNothing().when(authService).logout(any(RefreshTokenRequest.class), any(User.class));

        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
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
