package org.gcrel.tfbackevent.event.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Lista completa de sectores del evento: los que no vienen se borran.
public record UpdateSectorsRequest(
        @NotNull @Size(max = 30) List<@NotNull @Valid SectorRequest> sectors) {
}
