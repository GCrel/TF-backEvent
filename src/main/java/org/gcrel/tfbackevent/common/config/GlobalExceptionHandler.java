package org.gcrel.tfbackevent.common.config;

import java.time.LocalDateTime;

import org.gcrel.tfbackevent.common.dto.ErrorResponse;
import org.gcrel.tfbackevent.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Error del servidor en la aplicación: {}", ex.getMessage(), ex);
        } else {
            log.warn("Excepción de negocio - Status {}: {}", ex.getStatus().value(), ex.getMessage());
        }

        ErrorResponse response = new ErrorResponse(
                ex.getMessage(),
                ex.getStatus().value(),
                LocalDateTime.now());

        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Ocurrió un error inesperado no controlado: ", ex);
        ErrorResponse response = new ErrorResponse(
                "Ocurrió un error interno en el servidor.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
