package org.gcrel.tfbackevent.event.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

// Coordenadas normalizadas: 0 a 1 respecto del ancho y alto del plano.
public record SectorPointRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y) {
}
