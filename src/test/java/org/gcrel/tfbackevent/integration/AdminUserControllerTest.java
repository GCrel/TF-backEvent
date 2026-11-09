package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.controller.AdminUserController;
import org.gcrel.tfbackevent.admin.dto.response.UserAdminResponse;
import org.gcrel.tfbackevent.admin.exception.AdminConflictException;
import org.gcrel.tfbackevent.admin.exception.UserNotFoundException;
import org.gcrel.tfbackevent.admin.service.AdminUserService;
import org.gcrel.tfbackevent.auth.exception.IncorrectPasswordException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminUserController.class, properties = {
        "cookie.secure=true", "cookie.same-site=Strict",
        "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000" })
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
class AdminUserControllerTest {
    private static final String BASE = "/api/v1/admin/users";
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private UserRepository userRepository;

    private User admin;
    private Authentication adminAuth;
    private Authentication userAuth;
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        admin = new User("Ana", "ana@example.com", "hash", LocalDate.of(1990, 1, 1));
        admin.setSystemRole(SystemRole.ADMIN);
        adminAuth = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        User regular = new User("Bruno", "bruno@example.com", "hash", LocalDate.of(1995, 1, 1));
        userAuth = new UsernamePasswordAuthenticationToken(regular, null, regular.getAuthorities());
    }

    private UserAdminResponse response(boolean blocked, SystemRole role) {
        return new UserAdminResponse(targetId, "Bruno", "bruno@example.com", role, true, blocked,
                blocked ? "Spam" : null, null, LocalDate.of(1995, 1, 1), null);
    }

    @Test
    void everyEndpoint_WithoutSession_Returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/{id}/role", targetId).contentType(JSON)
                .content("{\"role\":\"ADMIN\",\"password\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyEndpoint_WithRegularUser_Returns403AndNeverReachesTheService() throws Exception {
        mockMvc.perform(get(BASE).with(authentication(userAuth))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/{id}", targetId).with(authentication(userAuth))).andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/{id}/block", targetId).with(authentication(userAuth))
                .contentType(JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/{id}/role", targetId).with(authentication(userAuth))
                .contentType(JSON).content("{\"role\":\"ADMIN\",\"password\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/{id}/revoke-sessions", targetId).with(authentication(userAuth)))
                .andExpect(status().isForbidden());
        verify(adminUserService, never()).changeRole(any(), any(), any(), any());
    }

    @Test
    void search_PassesTheFiltersAndReturnsAPage() throws Exception {
        when(adminUserService.search(eq("bru"), eq(SystemRole.USER), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(response(true, SystemRole.USER)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE).param("q", "bru").param("role", "USER").param("blocked", "true")
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("bruno@example.com"))
                .andExpect(jsonPath("$.content[0].blocked").value(true));
    }

    @Test
    void get_WhenMissing_Returns404() throws Exception {
        when(adminUserService.get(targetId)).thenThrow(new UserNotFoundException(targetId));
        mockMvc.perform(get(BASE + "/{id}", targetId).with(authentication(adminAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    void block_Returns200() throws Exception {
        when(adminUserService.block(any(User.class), eq(targetId), eq("Spam")))
                .thenReturn(response(true, SystemRole.USER));

        mockMvc.perform(patch(BASE + "/{id}/block", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedReason").value("Spam"));
    }

    @Test
    void block_WithoutReason_Returns400() throws Exception {
        mockMvc.perform(patch(BASE + "/{id}/block", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());
    }

    @Test
    void block_WhenAlreadyBlocked_Returns409() throws Exception {
        when(adminUserService.block(any(User.class), eq(targetId), any()))
                .thenThrow(new AdminConflictException("El usuario ya está bloqueado."));

        mockMvc.perform(patch(BASE + "/{id}/block", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unblock_Returns200() throws Exception {
        when(adminUserService.unblock(any(User.class), eq(targetId))).thenReturn(response(false, SystemRole.USER));
        mockMvc.perform(patch(BASE + "/{id}/unblock", targetId).with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void revokeSessions_Returns204() throws Exception {
        mockMvc.perform(post(BASE + "/{id}/revoke-sessions", targetId).with(authentication(adminAuth)))
                .andExpect(status().isNoContent());
        verify(adminUserService).revokeSessions(any(User.class), eq(targetId));
    }

    @Test
    void changeRole_Returns200() throws Exception {
        when(adminUserService.changeRole(any(User.class), eq(targetId), eq(SystemRole.ADMIN), eq("secret")))
                .thenReturn(response(false, SystemRole.ADMIN));

        mockMvc.perform(put(BASE + "/{id}/role", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"role\":\"ADMIN\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemRole").value("ADMIN"));
    }

    @Test
    void changeRole_WithWrongPassword_Returns403() throws Exception {
        when(adminUserService.changeRole(any(User.class), eq(targetId), any(), any()))
                .thenThrow(new IncorrectPasswordException());

        mockMvc.perform(put(BASE + "/{id}/role", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"role\":\"ADMIN\",\"password\":\"bad\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("La contraseña es incorrecta."));
    }

    @Test
    void changeRole_WithoutPasswordOrRole_Returns400() throws Exception {
        mockMvc.perform(put(BASE + "/{id}/role", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
        mockMvc.perform(put(BASE + "/{id}/role", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").exists());
    }

    @Test
    void changeRole_WithUnknownRole_Returns400() throws Exception {
        mockMvc.perform(put(BASE + "/{id}/role", targetId).with(authentication(adminAuth))
                .contentType(JSON).content("{\"role\":\"ROOT\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
