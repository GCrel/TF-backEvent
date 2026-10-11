package org.gcrel.tfbackevent.auth.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.gcrel.tfbackevent.auth.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.auth.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.exception.AccountAlreadyVerifiedException;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.EmailNotVerifiedException;
import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.exception.InvalidTokenException;
import org.gcrel.tfbackevent.auth.exception.InvalidVerificationCodeException;
import org.gcrel.tfbackevent.auth.exception.VerificationCodeExpiredException;
import org.gcrel.tfbackevent.auth.repository.RefreshTokenRepository;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.model.RefreshToken;
import org.gcrel.tfbackevent.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final int MAX_VERIFICATION_CODE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    // Hash de referencia para comparar cuando el email no existe, de modo que el
    // login
    // tarde lo mismo con o sin cuenta y no permita deducir qué emails están
    // registrados.
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public void register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User(
                request.name(),
                email,
                passwordEncoder.encode(request.password()),
                request.dateBirth());

        sendVerificationEmail(user);
    }

    // Responde igual exista o no la cuenta (y esté o no verificada) para no revelar
    // qué emails están registrados; solo envía el código si hay una cuenta
    // pendiente.
    public void resendVerificationCode(String email) {
        userRepository.findByEmail(EmailNormalizer.normalize(email))
                .filter(user -> !user.isEnabled())
                .ifPresent(this::sendVerificationEmail);
    }

    public AuthResponse verifyEmail(String email, String code) {
        User user = userRepository.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(InvalidCredentialsException::new);

        if (user.isEnabled()) {
            throw new AccountAlreadyVerifiedException();
        }
        if (user.getVerificationCode() == null
                || user.getVerificationCodeExpiresAt() == null
                || user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationCodeExpiredException();
        }
        if (!user.getVerificationCode().equals(code)) {
            int failedAttempts = user.getVerificationCodeFailedAttempts() + 1;
            user.setVerificationCodeFailedAttempts(failedAttempts);
            if (failedAttempts >= MAX_VERIFICATION_CODE_ATTEMPTS) {
                user.setVerificationCode(null);
                user.setVerificationCodeExpiresAt(null);
            }
            userRepository.save(user);
            throw new InvalidVerificationCodeException();
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        user.setVerificationCodeFailedAttempts(0);
        userRepository.save(user);
        return generateTokenPair(user);
    }

    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(EmailNormalizer.normalize(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException();
        }

        refreshTokenRepository.deleteAllByUser(user);
        return generateTokenPair(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.refreshToken())
                .orElseThrow(InvalidTokenException::new);

        if (refreshToken.isExpired()) {
            throw new InvalidTokenException();
        }

        String newAccessToken = jwtService.generateAccessToken(refreshToken.getUser());

        return new AuthResponse(newAccessToken, refreshToken.getToken());
    }

    public void logout(RefreshTokenRequest request, User user) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.refreshToken())
                // Un token ajeno se trata como inexistente para no confirmar que es válido.
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .orElseThrow(InvalidTokenException::new);

        refreshTokenRepository.delete(refreshToken);
    }

    // private methods
    private AuthResponse generateTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken(rawRefreshToken, jwtService.getRefreshTokenExpiresAt(), user);

        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken);
    }

    private void sendVerificationEmail(User user) {
        // Generate Code
        String code = String.format("%06d", new SecureRandom().nextInt(900000) + 100000);

        // assign code and expiration to user
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        user.setVerificationCodeFailedAttempts(0);

        userRepository.save(user);

        // Send mail
        emailService.sendVerificationEmail(user.getEmail(), code);
    }
}
