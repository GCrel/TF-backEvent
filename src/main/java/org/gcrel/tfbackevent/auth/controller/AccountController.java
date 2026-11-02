package org.gcrel.tfbackevent.auth.controller;

import org.gcrel.tfbackevent.auth.dto.request.ConfirmCodeRequest;
import org.gcrel.tfbackevent.auth.dto.request.ConfirmPasswordChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.RequestEmailChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.RequestPasswordChangeRequest;
import org.gcrel.tfbackevent.auth.dto.request.UpdateProfileRequest;
import org.gcrel.tfbackevent.auth.dto.response.AuthResponse;
import org.gcrel.tfbackevent.auth.dto.response.UserResponse;
import org.gcrel.tfbackevent.auth.service.AccountService;
import org.gcrel.tfbackevent.model.User;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth/me")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PatchMapping
    public ResponseEntity<UserResponse> updateProfile(
            @Validated @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return ResponseEntity.ok(accountService.updateProfile(user(authentication), request));
    }

    @PostMapping("/email/request")
    public ResponseEntity<Void> requestEmailChange(
            @Validated @RequestBody RequestEmailChangeRequest request, Authentication authentication) {
        accountService.requestEmailChange(user(authentication), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/confirm")
    public ResponseEntity<AuthResponse> confirmEmailChange(
            @Validated @RequestBody ConfirmCodeRequest request, Authentication authentication) {
        return ResponseEntity.ok(accountService.confirmEmailChange(user(authentication), request));
    }

    @PostMapping("/password/request")
    public ResponseEntity<Void> requestPasswordChange(
            @Validated @RequestBody RequestPasswordChangeRequest request, Authentication authentication) {
        accountService.requestPasswordChange(user(authentication), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/confirm")
    public ResponseEntity<AuthResponse> confirmPasswordChange(
            @Validated @RequestBody ConfirmPasswordChangeRequest request, Authentication authentication) {
        return ResponseEntity.ok(accountService.confirmPasswordChange(user(authentication), request));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> updateAvatar(
            @RequestParam("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.ok(accountService.updateAvatar(user(authentication), file));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> deleteAvatar(Authentication authentication) {
        accountService.deleteAvatar(user(authentication));
        return ResponseEntity.noContent().build();
    }

    private static User user(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
