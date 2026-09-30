package org.gcrel.tfbackevent.auth.controller;

import org.gcrel.tfbackevent.auth.dto.request.LoginRequest;
import org.gcrel.tfbackevent.auth.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.auth.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.auth.dto.request.ResendVerificationRequest;
import org.gcrel.tfbackevent.auth.dto.request.VerifyEmailRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.dto.response.UserResponse;
import org.gcrel.tfbackevent.auth.service.AuthService;
import org.gcrel.tfbackevent.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
    public ResponseEntity<AuthResponse> verifyEmail(@Validated @RequestBody VerifyEmailRequest request) {
        AuthResponse response = authService.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerificationCode(
            @Validated @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationCode(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Validated @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Validated @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Validated @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        assert user != null;
        return ResponseEntity.ok(new UserResponse(user));
    }
}
