package org.gcrel.tfbackevent.config;

import org.gcrel.tfbackevent.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({
            AccountAlreadyVerifiedException.class,
            EmailAlreadyExistsException.class,
            EventNotModifiableException.class,
            EventNotAvailableException.class,
            TicketNotActiveException.class,
    })
    public ResponseEntity<Map<String, String>> handleConflictRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({
            EmailNotVerifiedException.class,
            UnauthorizedEventAccessException.class,
            UnauthorizedTicketAccessException.class
    })
    public ResponseEntity<Map<String, String>> handleForbiddenRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({
            InvalidTokenException.class,
            InvalidCredentialsException.class,
            WebhookValidationException.class
    })
    public ResponseEntity<Map<String, String>> handleUnauthorizedRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({
            InvalidVerificationCodeException.class,
            VerificationCodeExpiredException.class,
            InvalidEventTypeException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({ EventNotFoundException.class, TicketNotFoundException.class })
    public ResponseEntity<Map<String, String>> handleNotFoundRequest(EventNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("Error:", e.getMessage()));
    }

    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<Map<String, String>> handlePaymentRejected(PaymentRejectedException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("Error:", e.getMessage()));
    }

    @ExceptionHandler({ Exception.class, QRGenerationException.class })
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("Error:", ex.getMessage()));
    }
}
