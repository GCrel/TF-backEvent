package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.gcrel.tfbackevent.auth.controller.AuthController;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.AuthCookies;
import org.gcrel.tfbackevent.auth.service.AuthService;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthController.class, properties = {
        "cors.allowed-origins=http://localhost:4200, https://app.example.com",
        "cookie.secure=true", "cookie.same-site=Strict", "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000" })
@Import({ GlobalExceptionHandler.class, SecurityConfig.class, AuthCookies.class })
@AutoConfigureMockMvc
public class CorsConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void preflight_FromAllowedOrigin_ReturnsCorsHeaders() throws Exception {
        mockMvc.perform(
                options("/api/v1/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    // Defensa contra CSRF
    @Test
    void actualRequest_FromUnknownOrigin_IsRejectedEvenWithSessionCookies() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .cookie(new Cookie(AuthCookies.REFRESH, "123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(status().isForbidden());
        verify(authService, never()).logout(any());
    }

    @Test
    void actualRequest_FromAllowedOrigin_AllowsCredentials() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com")
                        .cookie(new Cookie(AuthCookies.REFRESH, "123e4567-e89b-12d3-a456-426614174000")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflight_FromUnknownOrigin_Returns403() throws Exception {
        mockMvc.perform(
                options("/api/v1/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
