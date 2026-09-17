package org.gcrel.tfbackevent.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.service.MpOAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mp")
public class MpController {

    private final MpOAuthService mpOAuthService;

    public MpController(MpOAuthService mpOAuthService) {
        this.mpOAuthService = mpOAuthService;
    }

    @GetMapping("/connect")
    public ResponseEntity<Void> connect(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String authUrl = mpOAuthService.buildAuthorizationUrl(user.getId());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", authUrl)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<MpAccountStatusResponse> callback(@RequestParam String code, @RequestParam String state) {
        UUID userId = UUID.fromString(state);
        MpAccountStatusResponse response = mpOAuthService.linkAccount(code, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<MpAccountStatusResponse> status(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(mpOAuthService.getAccountStatus(user));
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        mpOAuthService.unlinkAccount(user);
        return ResponseEntity.noContent().build();
    }

}
