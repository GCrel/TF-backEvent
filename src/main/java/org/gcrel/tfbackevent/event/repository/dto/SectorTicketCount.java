package org.gcrel.tfbackevent.event.repository.dto;

import java.util.UUID;

public record SectorTicketCount(UUID sectorId, long count) {
}
