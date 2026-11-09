package org.gcrel.tfbackevent.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.gcrel.tfbackevent.auth.exception.InvalidVerificationCodeException;
import org.gcrel.tfbackevent.auth.exception.VerificationCodeExpiredException;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.CodePurpose;

final class VerificationCode {
    private static final int MAX_ATTEMPTS = 5;
    private static final int EXPIRATION_MINUTES = 15;

    private VerificationCode() {
    }

    // No guarda al usuario: lo hace quien llama. Devuelve el código para enviarlo
    // por email.
    static String issue(User user, CodePurpose purpose, String pendingEmail) {
        String code = String.format("%06d", new SecureRandom().nextInt(900000) + 100000);
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES));
        user.setVerificationCodeFailedAttempts(0);
        user.setCodePurpose(purpose);
        user.setPendingEmail(pendingEmail);
        return code;
    }

    // Si el código no coincide guarda el intento fallido antes de lanzar, y a los 5
    // lo invalida.
    static void check(User user, String code, UserRepository userRepository) {
        if (user.getVerificationCode() == null
                || user.getVerificationCodeExpiresAt() == null
                || user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationCodeExpiredException();
        }
        if (!user.getVerificationCode().equals(code)) {
            int failedAttempts = user.getVerificationCodeFailedAttempts() + 1;
            user.setVerificationCodeFailedAttempts(failedAttempts);
            if (failedAttempts >= MAX_ATTEMPTS) {
                user.setVerificationCode(null);
                user.setVerificationCodeExpiresAt(null);
                user.setCodePurpose(null);
                user.setPendingEmail(null);
            }
            userRepository.save(user);
            throw new InvalidVerificationCodeException();
        }
    }

    static void clear(User user) {
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        user.setVerificationCodeFailedAttempts(0);
        user.setCodePurpose(null);
        user.setPendingEmail(null);
    }
}
