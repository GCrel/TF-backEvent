package org.gcrel.tfbackevent.config;

import org.gcrel.tfbackevent.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({AccountAlreadyVerifiedException.class, EmailAlreadyExistsException.class})
    public ResponseEntity<Map<String, String>> handleConflictRequest(RuntimeException ex) {
        return  ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({EmailNotVerifiedException.class})
    public ResponseEntity<Map<String, String>> handleForbiddenRequest(RuntimeException ex) {
        return  ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({InvalidTokenException.class, InvalidCredentialsException.class})
    public ResponseEntity<Map<String, String>> handleUnauthorizedRequest(RuntimeException ex) {
        return  ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("Error:", ex.getMessage()));
    }

    @ExceptionHandler({InvalidVerificationCodeException.class, VerificationCodeExpiredException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException ex) {
        return  ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("Error:", ex.getMessage()));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        return  ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("Error:", ex.getMessage()));
    }
}
