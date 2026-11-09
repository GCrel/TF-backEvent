package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.gcrel.tfbackevent.auth.dto.request.ConfirmCodeRequest;
import org.gcrel.tfbackevent.auth.dto.request.ConfirmPasswordChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.RequestEmailChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.RequestPasswordChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.UpdateProfileRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.IncorrectPasswordException;
import org.gcrel.tfbackevent.auth.exception.InvalidVerificationCodeException;
import org.gcrel.tfbackevent.auth.exception.VerificationCodeExpiredException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.AccountService;
import org.gcrel.tfbackevent.auth.service.AuthService;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.service.ImageStorageService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.CodePurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuthService authService;
    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private AccountService accountService;

    private User user;
    private final AuthResponse session = new AuthResponse("access", "refresh");

    @BeforeEach
    void setUp() {
        user = new User("Ana", "ana@example.com", "encoded", LocalDate.of(1990, 1, 1));
        user.setId(UUID.randomUUID());
        user.setEnabled(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void stubSave() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void pendingCode(CodePurpose purpose, String pendingEmail) {
        user.setCodePurpose(purpose);
        user.setPendingEmail(pendingEmail);
        user.setVerificationCode("123456");
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(10));
    }

    // ==========================================
    // updateProfile
    // ==========================================

    @Test
    void updateProfile_TrimsAndSavesName() {
        stubSave();
        var response = accountService.updateProfile(user, new UpdateProfileRequest("  Ana María "));
        assertEquals("Ana María", response.name());
        verify(userRepository).save(user);
    }

    // ==========================================
    // Cambio de email
    // ==========================================

    @Test
    void requestEmailChange_StoresPendingEmailAndSendsCodeToTheNewOne() {
        stubSave();
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(userRepository.existsByEmail("nuevo@example.com")).thenReturn(false);

        accountService.requestEmailChange(user, new RequestEmailChangeRequest("  NUEVO@Example.com ", "secret"));

        assertEquals(CodePurpose.EMAIL_CHANGE, user.getCodePurpose());
        assertEquals("nuevo@example.com", user.getPendingEmail());
        assertEquals("ana@example.com", user.getEmail());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailChangeCode(eq("nuevo@example.com"), code.capture());
        assertEquals(user.getVerificationCode(), code.getValue());
    }

    @Test
    void requestEmailChange_WhenPasswordIsWrong_ThrowsAndSendsNothing() {
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);
        assertThrows(IncorrectPasswordException.class, () -> accountService.requestEmailChange(
                user, new RequestEmailChangeRequest("nuevo@example.com", "bad")));
        verify(emailService, never()).sendEmailChangeCode(any(), any());
    }

    @Test
    void requestEmailChange_WhenSameAsCurrent_ThrowsBadRequest() {
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> accountService.requestEmailChange(
                user, new RequestEmailChangeRequest("ANA@example.com", "secret")));
    }

    @Test
    void requestEmailChange_WhenEmailTaken_ThrowsConflict() {
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(userRepository.existsByEmail("otro@example.com")).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class, () -> accountService.requestEmailChange(
                user, new RequestEmailChangeRequest("otro@example.com", "secret")));
        verify(emailService, never()).sendEmailChangeCode(any(), any());
    }

    @Test
    void confirmEmailChange_AppliesEmailAndStartsNewSession() {
        stubSave();
        pendingCode(CodePurpose.EMAIL_CHANGE, "nuevo@example.com");
        when(authService.startSession(user)).thenReturn(session);

        AuthResponse response = accountService.confirmEmailChange(user, new ConfirmCodeRequest("123456"));

        assertEquals(session, response);
        assertEquals("nuevo@example.com", user.getEmail());
        assertNull(user.getPendingEmail());
        assertNull(user.getVerificationCode());
        assertNull(user.getCodePurpose());
    }

    @Test
    void confirmEmailChange_WhenCodeIsWrong_CountsTheAttemptAndKeepsEmail() {
        stubSave();
        pendingCode(CodePurpose.EMAIL_CHANGE, "nuevo@example.com");

        assertThrows(InvalidVerificationCodeException.class,
                () -> accountService.confirmEmailChange(user, new ConfirmCodeRequest("000000")));

        assertEquals("ana@example.com", user.getEmail());
        assertEquals(1, user.getVerificationCodeFailedAttempts());
        verify(userRepository).save(user);
        verify(authService, never()).startSession(any());
    }

    @Test
    void confirmEmailChange_AfterFiveWrongCodes_InvalidatesIt() {
        stubSave();
        pendingCode(CodePurpose.EMAIL_CHANGE, "nuevo@example.com");
        user.setVerificationCodeFailedAttempts(4);

        assertThrows(InvalidVerificationCodeException.class,
                () -> accountService.confirmEmailChange(user, new ConfirmCodeRequest("000000")));

        assertNull(user.getVerificationCode());
        assertNull(user.getPendingEmail());
    }

    @Test
    void confirmEmailChange_WhenCodeExpired_Throws() {
        pendingCode(CodePurpose.EMAIL_CHANGE, "nuevo@example.com");
        user.setVerificationCodeExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(VerificationCodeExpiredException.class,
                () -> accountService.confirmEmailChange(user, new ConfirmCodeRequest("123456")));
    }

    @Test
    void confirmEmailChange_WhenCodeWasIssuedForPasswordChange_Throws() {
        pendingCode(CodePurpose.PASSWORD_CHANGE, null);
        assertThrows(BadRequestException.class,
                () -> accountService.confirmEmailChange(user, new ConfirmCodeRequest("123456")));
        assertEquals("ana@example.com", user.getEmail());
    }

    @Test
    void confirmEmailChange_WhenNothingPending_Throws() {
        assertThrows(BadRequestException.class,
                () -> accountService.confirmEmailChange(user, new ConfirmCodeRequest("123456")));
    }

    @Test
    void confirmEmailChange_WhenEmailWasTakenMeanwhile_ThrowsConflict() {
        pendingCode(CodePurpose.EMAIL_CHANGE, "nuevo@example.com");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("unique"));
        assertThrows(EmailAlreadyExistsException.class,
                () -> accountService.confirmEmailChange(user, new ConfirmCodeRequest("123456")));
        verify(authService, never()).startSession(any());
    }

    // ==========================================
    // Cambio de contraseña
    // ==========================================

    @Test
    void requestPasswordChange_SendsCodeToCurrentEmail() {
        stubSave();
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        accountService.requestPasswordChange(user, new RequestPasswordChangeRequest("secret"));

        assertEquals(CodePurpose.PASSWORD_CHANGE, user.getCodePurpose());
        verify(emailService).sendPasswordChangeCode(eq("ana@example.com"), any());
    }

    @Test
    void requestPasswordChange_WhenPasswordIsWrong_Throws() {
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);
        assertThrows(IncorrectPasswordException.class,
                () -> accountService.requestPasswordChange(user, new RequestPasswordChangeRequest("bad")));
        verify(emailService, never()).sendPasswordChangeCode(any(), any());
    }

    @Test
    void confirmPasswordChange_EncodesNewPasswordAndStartsNewSession() {
        stubSave();
        pendingCode(CodePurpose.PASSWORD_CHANGE, null);
        when(passwordEncoder.matches("nueva-clave", "encoded")).thenReturn(false);
        when(passwordEncoder.encode("nueva-clave")).thenReturn("encoded-new");
        when(authService.startSession(user)).thenReturn(session);

        AuthResponse response = accountService.confirmPasswordChange(
                user, new ConfirmPasswordChangeRequest("123456", "nueva-clave"));

        assertEquals(session, response);
        assertEquals("encoded-new", user.getPassword());
        assertNull(user.getVerificationCode());
        assertNull(user.getCodePurpose());
    }

    @Test
    void confirmPasswordChange_WhenSameAsCurrent_ThrowsAndKeepsPassword() {
        pendingCode(CodePurpose.PASSWORD_CHANGE, null);
        when(passwordEncoder.matches("misma-clave", "encoded")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> accountService.confirmPasswordChange(
                user, new ConfirmPasswordChangeRequest("123456", "misma-clave")));

        assertEquals("encoded", user.getPassword());
        verify(authService, never()).startSession(any());
    }

    @Test
    void confirmPasswordChange_WhenCodeWasIssuedForEmailChange_Throws() {
        pendingCode(CodePurpose.EMAIL_CHANGE, "nuevo@example.com");
        assertThrows(BadRequestException.class, () -> accountService.confirmPasswordChange(
                user, new ConfirmPasswordChangeRequest("123456", "nueva-clave")));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void confirmPasswordChange_WhenCodeIsWrong_Throws() {
        stubSave();
        pendingCode(CodePurpose.PASSWORD_CHANGE, null);
        assertThrows(InvalidVerificationCodeException.class, () -> accountService.confirmPasswordChange(
                user, new ConfirmPasswordChangeRequest("000000", "nueva-clave")));
        assertEquals("encoded", user.getPassword());
    }

    // ==========================================
    // Avatar
    // ==========================================

    private final MockMultipartFile file = new MockMultipartFile(
            "file", "foto.webp", "image/webp", new byte[] { 1, 2, 3 });

    @Test
    void updateAvatar_WhenCommitted_DeletesPreviousFile() {
        stubSave();
        user.setAvatarKey("old.png");
        when(imageStorageService.store(file)).thenReturn("new.webp");

        var response = inTransaction(TransactionSynchronization.STATUS_COMMITTED,
                () -> accountService.updateAvatar(user, file));

        assertEquals("/api/v1/images/new.webp", response.avatarUrl());
        verify(imageStorageService).delete("old.png");
        verify(imageStorageService, never()).delete("new.webp");
    }

    @Test
    void updateAvatar_WhenRolledBack_DeletesNewFile() {
        stubSave();
        user.setAvatarKey("old.png");
        when(imageStorageService.store(file)).thenReturn("new.webp");

        inTransaction(TransactionSynchronization.STATUS_ROLLED_BACK,
                () -> accountService.updateAvatar(user, file));

        verify(imageStorageService).delete("new.webp");
        verify(imageStorageService, never()).delete("old.png");
    }

    @Test
    void deleteAvatar_ClearsKeyAndDeletesFileOnCommit() {
        stubSave();
        user.setAvatarKey("old.png");

        inTransaction(TransactionSynchronization.STATUS_COMMITTED, () -> {
            accountService.deleteAvatar(user);
            return null;
        });

        assertNull(user.getAvatarKey());
        verify(imageStorageService).delete("old.png");
    }

    @Test
    void deleteAvatar_WhenThereIsNone_DoesNothing() {
        accountService.deleteAvatar(user);
        verify(userRepository, never()).save(any());
        verify(imageStorageService, never()).delete(any());
    }

    // Simula el cierre de la transacción que en producción abre @Transactional.
    private <T> T inTransaction(int completionStatus, Supplier<T> action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            T result = action.get();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(completionStatus));
            return result;
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
