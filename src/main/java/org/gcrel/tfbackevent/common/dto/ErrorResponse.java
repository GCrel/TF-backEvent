package org.gcrel.tfbackevent.common.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        int status,
        LocalDateTime timestamp,
        Map<String, String> fields,
        Map<String, Object> details) {
    public ErrorResponse(String error, int status, LocalDateTime timestamp) {
        this(error, status, timestamp, null, null);
    }

    public ErrorResponse(String error, int status, LocalDateTime timestamp, Map<String, String> fields) {
        this(error, status, timestamp, fields, null);
    }
}
