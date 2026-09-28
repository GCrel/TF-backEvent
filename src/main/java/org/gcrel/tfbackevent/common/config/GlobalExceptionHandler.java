package org.gcrel.tfbackevent.common.config;

import java.util.Map;

import org.gcrel.tfbackevent.auth.exception.AccountAlreadyVerifiedException;
import org.gcrel.tfbackevent.auth.exception.EmailAlreadyExistsException;
import org.gcrel.tfbackevent.auth.exception.EmailNotVerifiedException;
import org.gcrel.tfbackevent.auth.exception.InvalidCredentialsException;
import org.gcrel.tfbackevent.auth.exception.InvalidTokenException;
import org.gcrel.tfbackevent.auth.exception.InvalidVerificationCodeException;
import org.gcrel.tfbackevent.auth.exception.VerificationCodeExpiredException;
import org.gcrel.tfbackevent.event.exception.EventAlreadyPublishedException;
import org.gcrel.tfbackevent.event.exception.EventNotAvailableException;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.EventNotModifiableException;
import org.gcrel.tfbackevent.event.exception.InvalidEventTypeException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.payment.exception.MpAccountNotLinkedException;
import org.gcrel.tfbackevent.payment.exception.MpOAuthException;
import org.gcrel.tfbackevent.payment.exception.PaymentRejectedException;
import org.gcrel.tfbackevent.payment.exception.QRGenerationException;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.exception.UnauthorizedTicketAccessException;
import org.gcrel.tfbackevent.payment.exception.WebhookValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({
            AccountAlreadyVerifiedException.class,
            EmailAlreadyExistsException.class,
            EventNotModifiableException.class,
            EventNotAvailableException.class,
            TicketNotActiveException.class,
            EventAlreadyPublishedException.class,
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

    @ExceptionHandler(MpAccountNotLinkedException.class)
    public ResponseEntity<String> handleMpAccountNotLinked(MpAccountNotLinkedException e) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(e.getMessage());
    }

    @ExceptionHandler(MpOAuthException.class)
    public ResponseEntity<String> handleMpOAuth(MpOAuthException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(e.getMessage());
    }
}
