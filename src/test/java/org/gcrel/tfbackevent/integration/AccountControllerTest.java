package org.gcrel.tfbackevent.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.controller.AccountController;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.dto.response.UserResponse;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.AccountService;
import org.gcrel.tfbackevent.auth.service.AuthCookies;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class, properties = {
        "cookie.secure=true", "cookie.same-site=Strict", "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000"})
@Import({ GlobalExceptionHandler.class, SecurityConfig.class, AuthCookies.class })
@AutoConfigureMockMvc
class AccountControllerTest {
    private static final String BASE = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository userRepository;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        User user = new User("Ana", "ana@example.com", "encoded", LocalDate.of(1990, 1, 1));
        authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void endpoints_WithoutToken_Return401() throws Exception {
        mockMvc.perform(patch(BASE).contentType(JSON).content("{\"name\":\"Ana\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/email/request").contentType(JSON)
                .content("{\"newEmail\":\"a@b.com\",\"currentPassword\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/avatar")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_Returns200() throws Exception {
        when(accountService.updateProfile(any(User.class), any())).thenReturn(new UserResponse(
                UUID.randomUUID(), "Ana María", "ana@example.com", SystemRole.USER,
                LocalDate.of(1990, 1, 1), null));

        mockMvc.perform(patch(BASE).with(authentication(authentication))
                .contentType(JSON).content("{\"name\":\"Ana María\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana María"));
    }

    @Test
    void updateProfile_WithBlankName_Returns400() throws Exception {
        mockMvc.perform(patch(BASE).with(authentication(authentication))
                .contentType(JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void requestEmailChange_Returns204() throws Exception {
        doNothing().when(accountService).requestEmailChange(any(User.class), any());

        mockMvc.perform(post(BASE + "/email/request").with(authentication(authentication))
                .contentType(JSON).content("{\"newEmail\":\"nuevo@example.com\",\"currentPassword\":\"secret\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void requestEmailChange_WithInvalidEmail_Returns400() throws Exception {
        mockMvc.perform(post(BASE + "/email/request").with(authentication(authentication))
                .contentType(JSON).content("{\"newEmail\":\"no-es-email\",\"currentPassword\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newEmail").exists());
    }

    @Test
    void requestEmailChange_WhenWrongPassword_Returns401() throws Exception {
        doThrow(new InvalidCredentialsException()).when(accountService)
                .requestEmailChange(any(User.class), any());

        mockMvc.perform(post(BASE + "/email/request").with(authentication(authentication))
                .contentType(JSON).content("{\"newEmail\":\"nuevo@example.com\",\"currentPassword\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestEmailChange_WhenEmailTaken_Returns409() throws Exception {
        doThrow(new EmailAlreadyExistsException("nuevo@example.com")).when(accountService)
                .requestEmailChange(any(User.class), any());

        mockMvc.perform(post(BASE + "/email/request").with(authentication(authentication))
                .contentType(JSON).content("{\"newEmail\":\"nuevo@example.com\",\"currentPassword\":\"secret\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmEmailChange_Returns204AndSetsNewSessionCookies() throws Exception {
        when(accountService.confirmEmailChange(any(User.class), any()))
                .thenReturn(new AuthResponse("access", "refresh"));

        mockMvc.perform(post(BASE + "/email/confirm").with(authentication(authentication))
                .contentType(JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertEquals(2, result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).size()));
    }

    @Test
    void confirmEmailChange_WithMalformedCode_Returns400() throws Exception {
        mockMvc.perform(post(BASE + "/email/confirm").with(authentication(authentication))
                .contentType(JSON).content("{\"code\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").exists());
    }

    @Test
    void requestPasswordChange_Returns204() throws Exception {
        doNothing().when(accountService).requestPasswordChange(any(User.class), any());

        mockMvc.perform(post(BASE + "/password/request").with(authentication(authentication))
                .contentType(JSON).content("{\"currentPassword\":\"secret\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void confirmPasswordChange_Returns204AndSetsNewSessionCookies() throws Exception {
        when(accountService.confirmPasswordChange(any(User.class), any()))
                .thenReturn(new AuthResponse("access", "refresh"));

        mockMvc.perform(post(BASE + "/password/confirm").with(authentication(authentication))
                .contentType(JSON).content("{\"code\":\"123456\",\"newPassword\":\"nueva-clave\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertEquals(2, result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).size()));
    }

    @Test
    void confirmPasswordChange_WithShortPassword_Returns400() throws Exception {
        mockMvc.perform(post(BASE + "/password/confirm").with(authentication(authentication))
                .contentType(JSON).content("{\"code\":\"123456\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void updateAvatar_ReturnsUserWithAvatarUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.webp", "image/webp", new byte[] { 1, 2, 3 });
        when(accountService.updateAvatar(any(User.class), any())).thenReturn(new UserResponse(
                UUID.randomUUID(), "Ana", "ana@example.com", SystemRole.USER,
                LocalDate.of(1990, 1, 1), "/api/v1/images/abc.webp"));

        mockMvc.perform(multipart(HttpMethod.PUT, BASE + "/avatar").file(file)
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/api/v1/images/abc.webp"));
    }

    @Test
    void deleteAvatar_Returns204() throws Exception {
        doNothing().when(accountService).deleteAvatar(any(User.class));

        mockMvc.perform(delete(BASE + "/avatar").with(authentication(authentication)))
                .andExpect(status().isNoContent());
    }
}
