package org.gcrel.tfbackevent.event.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Sector;
import org.gcrel.tfbackevent.model.SectorPoint;

public record SectorResponse(
        UUID id,
        String name,
        BigDecimal price,
        int capacity,
        String color,
        List<SectorPoint> points,
        int availableTickets) {

    public SectorResponse(Sector sector, long reservedTickets) {
        this(
                sector.getId(),
                sector.getName(),
                sector.getPrice(),
                sector.getCapacity(),
                sector.getColor(),
                sector.getPoints(),
                (int) Math.max(0, sector.getCapacity() - reservedTickets));
    }
}
