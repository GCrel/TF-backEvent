package org.gcrel.tfbackevent.auth.service;

import org.gcrel.tfbackevent.auth.dto.request.ConfirmCodeRequest;
import org.gcrel.tfbackevent.auth.dto.request.ConfirmPasswordChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.RequestEmailChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.RequestPasswordChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.UpdateProfileRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.dto.response.UserResponse;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.exception.InvalidTokenException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.service.DeferredImageDeletion;
import org.gcrel.tfbackevent.event.service.ImageStorageService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.CodePurpose;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// Los cambios de email y contraseña no son @Transactional a propósito: el intento fallido de
// código debe guardarse aunque la operación termine lanzando una excepción.
@Service
public class AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthService authService;
    private final ImageStorageService imageStorageService;

    public AccountService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuthService authService,
            ImageStorageService imageStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.authService = authService;
        this.imageStorageService = imageStorageService;
    }

    public UserResponse updateProfile(User principal, UpdateProfileRequest request) {
        User user = load(principal);
        user.setName(request.name().trim());
        return new UserResponse(userRepository.save(user));
    }

    // El código va al email nuevo: así se comprueba que la persona tiene acceso a
    // él.
    public void requestEmailChange(User principal, RequestEmailChangeRequest request) {
        User user = load(principal);
        requirePassword(user, request.currentPassword());
        String newEmail = EmailNormalizer.normalize(request.newEmail());
        if (newEmail.equals(user.getEmail())) {
            throw new BadRequestException("El nuevo email es igual al actual.");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyExistsException(newEmail);
        }

        String code = VerificationCode.issue(user, CodePurpose.EMAIL_CHANGE, newEmail);
        userRepository.save(user);
        emailService.sendEmailChangeCode(newEmail, code);
    }

    // El JWT lleva el email como subject: al cambiarlo, los tokens anteriores dejan
    // de servir, así que se devuelve una sesión nueva.
    public AuthResponse confirmEmailChange(User principal, ConfirmCodeRequest request) {
        User user = load(principal);
        requirePending(user, CodePurpose.EMAIL_CHANGE);
        VerificationCode.check(user, request.code(), userRepository);

        String newEmail = user.getPendingEmail();
        user.setEmail(newEmail);
        VerificationCode.clear(user);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Otra cuenta tomó el email entre el pedido y la confirmación.
            throw new EmailAlreadyExistsException(newEmail);
        }
        return authService.startSession(user);
    }

    public void requestPasswordChange(User principal, RequestPasswordChangeRequest request) {
        User user = load(principal);
        requirePassword(user, request.currentPassword());

        String code = VerificationCode.issue(user, CodePurpose.PASSWORD_CHANGE, null);
        userRepository.save(user);
        emailService.sendPasswordChangeCode(user.getEmail(), code);
    }

    public AuthResponse confirmPasswordChange(User principal, ConfirmPasswordChangeRequest request) {
        User user = load(principal);
        requirePending(user, CodePurpose.PASSWORD_CHANGE);
        VerificationCode.check(user, request.code(), userRepository);
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("La nueva contraseña debe ser distinta de la actual.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        VerificationCode.clear(user);
        userRepository.save(user);
        return authService.startSession(user);
    }

    @Transactional
    public UserResponse updateAvatar(User principal, MultipartFile file) {
        User user = load(principal);
        String previousKey = user.getAvatarKey();
        String newKey = imageStorageService.store(file);
        DeferredImageDeletion.afterCompletion(imageStorageService, previousKey, newKey);

        user.setAvatarKey(newKey);
        return new UserResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteAvatar(User principal) {
        User user = load(principal);
        String previousKey = user.getAvatarKey();
        if (previousKey == null) {
            return;
        }
        DeferredImageDeletion.afterCompletion(imageStorageService, previousKey, null);

        user.setAvatarKey(null);
        userRepository.save(user);
    }

    // El principal viene del filtro JWT y puede estar desactualizado.
    private User load(User principal) {
        return userRepository.findById(principal.getId()).orElseThrow(InvalidTokenException::new);
    }

    private void requirePassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
    }

    private void requirePending(User user, CodePurpose purpose) {
        if (user.getCodePurpose() != purpose
                || (purpose == CodePurpose.EMAIL_CHANGE && user.getPendingEmail() == null)) {
            throw new BadRequestException("No hay un cambio pendiente de confirmar.");
        }
    }
}
