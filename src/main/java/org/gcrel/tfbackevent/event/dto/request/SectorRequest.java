package org.gcrel.tfbackevent.event.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SectorRequest(
        // Con id se actualiza ese sector; sin id se crea uno nuevo.
        UUID id,
        @NotBlank @Size(max = 50) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(100_000) Integer capacity,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
        // Opcional: sin plano del recinto no hay sobre qué dibujar el sector.
        @Size(min = 3, max = 20) List<@NotNull @Valid SectorPointRequest> points) {
}
