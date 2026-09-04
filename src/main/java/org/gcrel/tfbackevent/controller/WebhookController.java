package org.gcrel.tfbackevent.controller;

import org.gcrel.tfbackevent.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {
    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> receivePaymentNotification(@RequestBody WebhookPaymentRequest request, @RequestHeader("x-signature") String xSignature, @RequestHeader("x-request-id") String xRequestId) {
        webhookService.processPaymentNotification(request, xSignature, xRequestId);
        return ResponseEntity.ok().build();
    }
}
