package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.exception.AdminConflictException;
import org.gcrel.tfbackevent.admin.exception.UserNotFoundException;
import org.gcrel.tfbackevent.admin.service.AdminUserService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.auth.exception.IncorrectPasswordException;
import org.gcrel.tfbackevent.auth.repository.RefreshTokenRepository;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.common.exception.ForbiddenException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AdminUserService service;

    private User actor;
    private User target;

    @BeforeEach
    void setUp() {
        actor = user("Ana", "ana@example.com", SystemRole.ADMIN);
        target = user("Bruno", "bruno@example.com", SystemRole.USER);
    }

    private static User user(String name, String email, SystemRole role) {
        User user = new User(name, email, "hash-" + name, LocalDate.of(1990, 1, 1));
        user.setId(UUID.randomUUID());
        user.setEnabled(true);
        user.setSystemRole(role);
        return user;
    }

    private void stubTarget() {
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
    }

    // ==========================================
    // Bloqueo
    // ==========================================

    @Test
    void block_BlocksRevokesSessionsAuditsAndNotifies() {
        stubTarget();

        var response = service.block(actor, target.getId(), "  Spam  ");

        assertTrue(target.isBlocked());
        assertEquals("Spam", target.getBlockedReason());
        assertTrue(response.blocked());
        verify(userRepository).save(target);
        verify(refreshTokenRepository).deleteAllByUser(target);
        verify(auditService).record(actor, AuditAction.USER_BLOCKED, AuditTargetType.USER, target.getId(), "Spam");
        verify(emailService).sendNotice(eq("bruno@example.com"), anyString(), anyString());
    }

    @Test
    void block_WhenSelf_ThrowsBadRequest() {
        when(userRepository.findByIdForUpdate(actor.getId())).thenReturn(Optional.of(actor));
        assertThrows(BadRequestException.class, () -> service.block(actor, actor.getId(), "x"));
        assertFalse(actor.isBlocked());
    }

    @Test
    void block_WhenTargetIsAdmin_ThrowsConflict() {
        target.setSystemRole(SystemRole.ADMIN);
        stubTarget();
        assertThrows(AdminConflictException.class, () -> service.block(actor, target.getId(), "x"));
        assertFalse(target.isBlocked());
        verify(refreshTokenRepository, never()).deleteAllByUser(any());
    }

    @Test
    void block_WhenAlreadyBlocked_ThrowsConflict() {
        target.block("antes", LocalDateTime.now());
        stubTarget();
        assertThrows(AdminConflictException.class, () -> service.block(actor, target.getId(), "x"));
    }

    @Test
    void block_WhenUserDoesNotExist_ThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.block(actor, id, "x"));
    }

    @Test
    void unblock_ClearsBlockAuditsAndNotifies() {
        target.block("Spam", LocalDateTime.now());
        stubTarget();

        var response = service.unblock(actor, target.getId());

        assertFalse(target.isBlocked());
        assertNull(target.getBlockedReason());
        assertFalse(response.blocked());
        verify(auditService).record(actor, AuditAction.USER_UNBLOCKED, AuditTargetType.USER, target.getId(), null);
        verify(emailService).sendNotice(eq("bruno@example.com"), anyString(), anyString());
    }

    @Test
    void unblock_WhenNotBlocked_ThrowsConflict() {
        stubTarget();
        assertThrows(AdminConflictException.class, () -> service.unblock(actor, target.getId()));
    }

    @Test
    void revokeSessions_DeletesRefreshTokensAndAudits() {
        stubTarget();

        service.revokeSessions(actor, target.getId());

        verify(refreshTokenRepository).deleteAllByUser(target);
        verify(auditService).record(actor, AuditAction.USER_SESSIONS_REVOKED, AuditTargetType.USER, target.getId(), null);
        verify(emailService).sendNotice(eq("bruno@example.com"), anyString(), anyString());
    }

    // ==========================================
    // Cambio de rol
    // ==========================================

    private void stubAdmins(User... admins) {
        when(userRepository.findBySystemRoleForUpdate(SystemRole.ADMIN)).thenReturn(List.of(admins));
    }

    @Test
    void changeRole_PromotesRevokesSessionsAuditsAndNotifies() {
        stubAdmins(actor);
        stubTarget();
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);

        var response = service.changeRole(actor, target.getId(), SystemRole.ADMIN, "secret");

        assertEquals(SystemRole.ADMIN, target.getSystemRole());
        assertEquals(SystemRole.ADMIN, response.systemRole());
        verify(refreshTokenRepository).deleteAllByUser(target);
        verify(auditService).record(actor, AuditAction.ROLE_GRANTED, AuditTargetType.USER, target.getId(), "Rol: ADMIN");
        verify(emailService).sendNotice(eq("bruno@example.com"), anyString(), anyString());
    }

    @Test
    void changeRole_DemotesAnotherAdmin() {
        target.setSystemRole(SystemRole.ADMIN);
        stubAdmins(actor, target);
        stubTarget();
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);

        service.changeRole(actor, target.getId(), SystemRole.USER, "secret");

        assertEquals(SystemRole.USER, target.getSystemRole());
        verify(refreshTokenRepository).deleteAllByUser(target);
        verify(auditService).record(actor, AuditAction.ROLE_REVOKED, AuditTargetType.USER, target.getId(), "Rol: USER");
    }

    @Test
    void changeRole_WithWrongPassword_ThrowsAndChangesNothing() {
        stubAdmins(actor);
        when(passwordEncoder.matches("bad", "hash-Ana")).thenReturn(false);

        assertThrows(IncorrectPasswordException.class,
                () -> service.changeRole(actor, target.getId(), SystemRole.ADMIN, "bad"));

        assertEquals(SystemRole.USER, target.getSystemRole());
        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
        verify(emailService, never()).sendNotice(any(), any(), any());
    }

    // Un admin degradado por otro mientras tanto ya no figura entre los bloqueados.
    @Test
    void changeRole_WhenActorIsNoLongerAdmin_ThrowsForbidden() {
        stubAdmins(target);
        assertThrows(ForbiddenException.class,
                () -> service.changeRole(actor, UUID.randomUUID(), SystemRole.ADMIN, "secret"));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void changeRole_WhenSelf_ThrowsBadRequest() {
        stubAdmins(actor);
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);
        assertThrows(BadRequestException.class,
                () -> service.changeRole(actor, actor.getId(), SystemRole.USER, "secret"));
        assertEquals(SystemRole.ADMIN, actor.getSystemRole());
    }

    @Test
    void changeRole_WhenRoleIsAlreadyApplied_ThrowsConflict() {
        stubAdmins(actor);
        stubTarget();
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);
        assertThrows(AdminConflictException.class,
                () -> service.changeRole(actor, target.getId(), SystemRole.USER, "secret"));
    }

    @Test
    void changeRole_PromotingAnUnverifiedUser_ThrowsConflict() {
        target.setEnabled(false);
        stubAdmins(actor);
        stubTarget();
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);
        assertThrows(AdminConflictException.class,
                () -> service.changeRole(actor, target.getId(), SystemRole.ADMIN, "secret"));
        assertEquals(SystemRole.USER, target.getSystemRole());
    }

    @Test
    void changeRole_PromotingABlockedUser_ThrowsConflict() {
        target.block("Spam", LocalDateTime.now());
        stubAdmins(actor);
        stubTarget();
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);
        assertThrows(AdminConflictException.class,
                () -> service.changeRole(actor, target.getId(), SystemRole.ADMIN, "secret"));
    }

    @Test
    void changeRole_NeverLeavesTheSystemWithoutSuperusers() {
        target.setSystemRole(SystemRole.ADMIN);
        stubAdmins(actor);
        stubTarget();
        when(passwordEncoder.matches("secret", "hash-Ana")).thenReturn(true);

        assertThrows(AdminConflictException.class,
                () -> service.changeRole(actor, target.getId(), SystemRole.USER, "secret"));
        assertEquals(SystemRole.ADMIN, target.getSystemRole());
    }
}
