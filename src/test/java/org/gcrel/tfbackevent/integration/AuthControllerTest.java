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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.gcrel.tfbackevent.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.config.SecurityConfig;
import org.gcrel.tfbackevent.controller.AuthController;
import org.gcrel.tfbackevent.dto.request.LoginRequest;
import org.gcrel.tfbackevent.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.dto.request.VerifyEmailRequest;
import org.gcrel.tfbackevent.dto.response.AuthResponse;
import org.gcrel.tfbackevent.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.exception.EmailNotVerifiedException;
import org.gcrel.tfbackevent.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.exception.InvalidVerificationCodeException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.repository.UserRepository;
import org.gcrel.tfbackevent.service.AuthService;
import org.gcrel.tfbackevent.service.JwtService;
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

import java.time.LocalDate;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@AutoConfigureMockMvc
public class AuthControllerTest {
    @Autowired private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    @MockitoBean private JwtService jwtService;

    @MockitoBean private UserDetailsService userDetailsService;

    @MockitoBean private UserRepository userRepository;

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
        RegisterRequest request =
                new RegisterRequest(
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
        RegisterRequest request =
                new RegisterRequest("John Doe", "test@example.com", "password", LocalDate.now());

        doThrow(new EmailAlreadyExistsException(request.getEmail()))
                .when(authService)
                .register(any(RegisterRequest.class));

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$['Error:']")
                                .value("El email ya está registrado: " + request.getEmail()));
    }

    // ==========================================
    // /verify
    // ==========================================

    @Test
    void verifyEmail_Success_Returns200AndTokens() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest("test@example.com", "123456");
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
                .andExpect(jsonPath("$['Error:']").value("Código incorrecto"));
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
                        jsonPath("$['Error:']")
                                .value("El correo electrónico no ha sido verificado."));
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
                .andExpect(jsonPath("$['Error:']").value("Credenciales inválidas."));
    }

    // ==========================================
    // Protected (/logout, /me)
    // ==========================================

    @Test
    void logout_Success_Returns204() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh_token_valid");
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());

        doNothing().when(authService).logout(any(RefreshTokenRequest.class));

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void me_Success_ReturnsUserDetails() throws Exception {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"));
    }
}
