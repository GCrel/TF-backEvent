package org.gcrel.tfbackevent.service;

import org.gcrel.tfbackevent.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.dto.response.AuthResponse;
import org.gcrel.tfbackevent.exception.*;
import org.gcrel.tfbackevent.model.RefreshToken;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.repository.RefreshTokenRepository;
import org.gcrel.tfbackevent.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class AuthService {
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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user =
                new User(
                        request.getName(),
                        request.getEmail(),
                        passwordEncoder.encode(request.getPassword()),
                        request.getDateBirth());

        sendVerificationEmail(user);
    }

    public void resendVerificationCode(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        if (user.isEnabled()) {
            throw new AccountAlreadyVerifiedException();
        }

        sendVerificationEmail(user);
    }

    public AuthResponse verifyEmail(String email, String code) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        if (user.isEnabled()) {
            throw new AccountAlreadyVerifiedException();
        }
        if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationCodeExpiredException();
        }
        if (!user.getVerificationCode().equals(code)) {
            throw new InvalidVerificationCodeException();
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);
        return generateTokenPair(user);
    }

    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException();
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        refreshTokenRepository.deleteAllByUser(user);
        return generateTokenPair(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken =
                refreshTokenRepository
                        .findByToken(request.getRefreshToken())
                        .orElseThrow(InvalidTokenException::new);

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new InvalidTokenException();
        }

        String newAccessToken = jwtService.generateAccessToken(refreshToken.getUser());

        return new AuthResponse(newAccessToken, refreshToken.getToken());
    }

    public void logout(RefreshTokenRequest request) {
        RefreshToken refreshToken =
                refreshTokenRepository
                        .findByToken(request.getRefreshToken())
                        .orElseThrow(InvalidTokenException::new);

        refreshTokenRepository.delete(refreshToken);
    }

    // private methods
    private AuthResponse generateTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken =
                new RefreshToken(rawRefreshToken, jwtService.getRefreshTokenExpiresAt(), user);

        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken);
    }

    private void sendVerificationEmail(User user) {
        // Generate Code
        String code = String.format("%06d", new SecureRandom().nextInt(900000) + 100000);

        // assign code and expiration to user
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);

        // Send mail
        emailService.sendVerificationEmail(user.getEmail(), code);
    }
}
