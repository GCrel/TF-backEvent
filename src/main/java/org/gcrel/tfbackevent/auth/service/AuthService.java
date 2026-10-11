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

    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(InvalidCredentialsException::new);

        if (user.isEnabled()) {
            throw new AccountAlreadyVerifiedException();
        }

        sendVerificationEmail(user);
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
        User user = userRepository.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(InvalidCredentialsException::new);

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

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new InvalidTokenException();
        }

        String newAccessToken = jwtService.generateAccessToken(refreshToken.getUser());

        return new AuthResponse(newAccessToken, refreshToken.getToken());
    }

    public void logout(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.refreshToken())
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
