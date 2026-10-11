package org.gcrel.tfbackevent.payment.controller;

import java.net.URI;
import java.util.Optional;

import org.gcrel.tfbackevent.common.exception.AppException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.payment.dto.response.MpAuthorizationUrlResponse;
import org.gcrel.tfbackevent.payment.exception.InvalidMpOAuthStateException;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/mp")
public class MpController {

    private final MpOAuthService mpOAuthService;
    private final String frontendReturnUrl;

    public MpController(MpOAuthService mpOAuthService,
            @Value("${mercadopago.frontend-return-url}") String frontendReturnUrl) {
        this.mpOAuthService = mpOAuthService;
        this.frontendReturnUrl = frontendReturnUrl;
    }

    // Devuelve la URL de autorización; el frontend debe navegar a ella
    // (window.location.href), ya que una SPA no puede seguir un 302 vía XHR.
    @GetMapping("/connect")
    public ResponseEntity<MpAuthorizationUrlResponse> connect(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String authUrl = mpOAuthService.buildAuthorizationUrl(user.getId());
        return ResponseEntity.ok(new MpAuthorizationUrlResponse(authUrl));
    }

    // MercadoPago redirige aquí el navegador del organizador; respondemos
    // redirigiendo al frontend con el resultado en la query.
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null || code == null || state == null) {
            return redirectToFrontend("error", "cancelled");
        }
        try {
            mpOAuthService.linkAccount(code, state);
            return redirectToFrontend("linked", null);
        } catch (InvalidMpOAuthStateException e) {
            return redirectToFrontend("error", "invalid_state");
        } catch (AppException e) {
            log.warn("No se pudo vincular la cuenta de MercadoPago: {}", e.getMessage());
            return redirectToFrontend("error", "link_failed");
        }
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

    private ResponseEntity<Void> redirectToFrontend(String status, String reason) {
        URI location = UriComponentsBuilder.fromUriString(frontendReturnUrl)
                .queryParam("status", status)
                .queryParamIfPresent("reason", Optional.ofNullable(reason))
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
