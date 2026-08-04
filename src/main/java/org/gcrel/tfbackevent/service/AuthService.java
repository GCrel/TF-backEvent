package org.gcrel.tfbackevent.service;

import org.gcrel.tfbackevent.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.dto.response.AuthResponse;
import org.gcrel.tfbackevent.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.exception.InvalidTokenException;
import org.gcrel.tfbackevent.model.RefreshToken;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.repository.RefreshTokenRepository;
import org.gcrel.tfbackevent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository, JwtService jwtService,  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User(
                request.getName(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getDateBirth()
        );

        userRepository.save(user);

        return generateTokenPair(user);
    }

    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // revoke the user's previous tokens
        refreshTokenRepository.deleteAllByUser(user);

        return generateTokenPair(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(InvalidTokenException::new);

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new InvalidTokenException();
        }

        String newAccessToken = jwtService.generateAccessToken(refreshToken.getUser());

        return new AuthResponse(newAccessToken, refreshToken.getToken());
    }

    public void logout(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(InvalidTokenException::new);

        refreshTokenRepository.delete(refreshToken);
    }

    // private methods
    private AuthResponse generateTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken(
                rawRefreshToken,
                jwtService.getRefreshTokenExpiresAt(),
                user
        );

        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken);
    }
}
