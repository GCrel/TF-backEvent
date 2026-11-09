package org.gcrel.tfbackevent.auth.controller;

import org.gcrel.tfbackevent.auth.dto.request.LoginRequest;
import org.gcrel.tfbackevent.auth.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.auth.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.auth.dto.request.ResendVerificationRequest;
import org.gcrel.tfbackevent.auth.dto.request.VerifyEmailRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.dto.response.UserResponse;
import org.gcrel.tfbackevent.auth.exception.InvalidTokenException;
import org.gcrel.tfbackevent.auth.service.AuthCookies;
import org.gcrel.tfbackevent.auth.service.AuthService;
import org.gcrel.tfbackevent.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookies authCookies;

    public AuthController(AuthService authService, AuthCookies authCookies) {
        this.authService = authService;
        this.authCookies = authCookies;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Validated @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        "Usuario registrado con éxito. Por favor revisa tu correo para verificar tu"
                                + " cuenta.");
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verifyEmail(@Validated @RequestBody VerifyEmailRequest request) {
        return sessionStarted(authService.verifyEmail(request.email(), request.code()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerificationCode(
            @Validated @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationCode(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Validated @RequestBody LoginRequest request) {
        return sessionStarted(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshToken(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidTokenException();
        }
        AuthResponse response = authService.refresh(new RefreshTokenRequest(refreshToken));
        return ResponseEntity.noContent().headers(authCookies.accessOnly(response.accessToken())).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().headers(authCookies.cleared()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        assert user != null;
        return ResponseEntity.ok(new UserResponse(user));
    }

    private ResponseEntity<Void> sessionStarted(AuthResponse tokens) {
        return ResponseEntity.noContent().headers(authCookies.session(tokens)).build();
    }
}
