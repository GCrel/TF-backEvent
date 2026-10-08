package org.gcrel.tfbackevent.access.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidateQrRequest(
        @NotNull UUID eventId, 
        @NotBlank @Size (max = 64) 
        String qrCode) {
}
