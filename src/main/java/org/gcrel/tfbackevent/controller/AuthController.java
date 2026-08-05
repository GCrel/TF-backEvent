package org.gcrel.tfbackevent.controller;

import org.gcrel.tfbackevent.dto.request.LoginRequest;
import org.gcrel.tfbackevent.dto.request.RefreshTokenRequest;
import org.gcrel.tfbackevent.dto.request.RegisterRequest;
import org.gcrel.tfbackevent.dto.response.AuthResponse;
import org.gcrel.tfbackevent.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(LoginRequest request) {
        AuthResponse response = authService.login(request.getEmail(),request.getPassword());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
