package org.gcrel.tfbackevent.event.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.dto.request.SectorRequest;
import org.gcrel.tfbackevent.event.dto.response.SectorResponse;
import org.gcrel.tfbackevent.event.exception.SectorHasTicketsException;
import org.gcrel.tfbackevent.event.repository.SectorRepository;
import org.gcrel.tfbackevent.event.repository.dto.SectorTicketCount;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Sector;
import org.gcrel.tfbackevent.model.SectorPoint;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.springframework.stereotype.Service;

// Reglas de los sectores de un evento presencial. Quien llama ya validó al organizador y
// bloqueó el evento, así que estas operaciones no compiten con las compras.
@Service
public class SectorService {
    private static final List<TicketStatus> RESERVED_STATUSES = List.of(
            TicketStatus.PENDING, TicketStatus.ACTIVE, TicketStatus.USED);

    private final SectorRepository sectorRepository;
    private final TicketRepository ticketRepository;
    private final TicketOrderRepository orderRepository;

    public SectorService(SectorRepository sectorRepository, TicketRepository ticketRepository,
            TicketOrderRepository orderRepository) {
        this.sectorRepository = sectorRepository;
        this.ticketRepository = ticketRepository;
        this.orderRepository = orderRepository;
    }

    public List<SectorResponse> getSectors(InPersonEvent event) {
        Map<UUID, Long> reserved = reservedBySector(event);
        return sectorRepository.findByEventOrderByName(event).stream()
                .map(sector -> new SectorResponse(sector, reserved.getOrDefault(sector.getId(), 0L)))
                .toList();
    }

    public boolean hasSectors(InPersonEvent event) {
        return sectorRepository.existsByEvent(event);
    }

    // Precio más alto entre los sectores, o null si no tiene: decide si el evento es pago.
    public BigDecimal maxPrice(InPersonEvent event) {
        return sectorRepository.findByEventOrderByName(event).stream()
                .map(Sector::getPrice)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // Reemplaza la lista completa: con id se actualiza, sin id se crea, lo que falta se borra.
    public List<SectorResponse> replaceSectors(InPersonEvent event, List<SectorRequest> requests) {
        validateUniqueNames(requests);
        List<Sector> current = sectorRepository.findByEventOrderByName(event);
        Map<UUID, Sector> currentById = current.stream()
                .collect(Collectors.toMap(Sector::getId, sector -> sector));
        Map<UUID, Long> reserved = reservedBySector(event);

        // Las entradas vendidas sin sector (o con sector) no pueden cambiar de modo.
        if (current.isEmpty() != requests.isEmpty()
                && ticketRepository.countByEventAndStatusIn(event, RESERVED_STATUSES) > 0) {
            throw new SectorHasTicketsException(
                    "No se pueden agregar ni quitar todos los sectores de un evento con entradas vendidas.");
        }

        Set<UUID> keptIds = new HashSet<>();
        List<Sector> sectors = new ArrayList<>();
        for (SectorRequest request : requests) {
            Sector sector = request.id() == null
                    ? new Sector(event)
                    : existingSector(request, currentById, keptIds, reserved);
            apply(sector, request);
            sectors.add(sector);
        }

        List<Sector> removed = current.stream()
                .filter(sector -> !keptIds.contains(sector.getId()))
                .toList();
        for (Sector sector : removed) {
            if (reserved.getOrDefault(sector.getId(), 0L) > 0) {
                throw new SectorHasTicketsException("No se puede borrar el sector \"" + sector.getName()
                        + "\" porque tiene entradas vendidas.");
            }
        }
        delete(removed);
        sectorRepository.saveAll(sectors);

        if (!sectors.isEmpty()) {
            // Precio "desde" y cupo total del evento, para tarjetas, filtros y disponibilidad.
            event.setBasePrice(sectors.stream().map(Sector::getPrice).min(Comparator.naturalOrder()).orElseThrow());
            event.setCapacity(sectors.stream().mapToInt(Sector::getCapacity).sum());
        }
        return getSectors(event);
    }

    private Sector existingSector(SectorRequest request, Map<UUID, Sector> currentById,
            Set<UUID> keptIds, Map<UUID, Long> reserved) {
        Sector sector = currentById.get(request.id());
        if (sector == null || !keptIds.add(request.id())) {
            throw new BadRequestException("El sector " + request.id() + " no pertenece al evento o está repetido.");
        }
        long sold = reserved.getOrDefault(sector.getId(), 0L);
        if (sold > 0 && sector.getPrice().compareTo(request.price()) != 0) {
            throw new SectorHasTicketsException("No se puede cambiar el precio del sector \"" + sector.getName()
                    + "\" porque tiene entradas vendidas.");
        }
        if (request.capacity() < sold) {
            throw new SectorHasTicketsException("La capacidad del sector \"" + sector.getName()
                    + "\" no puede ser menor a sus " + sold + " entradas vendidas.");
        }
        return sector;
    }

    private void apply(Sector sector, SectorRequest request) {
        sector.setName(request.name().strip());
        sector.setPrice(request.price());
        sector.setCapacity(request.capacity());
        sector.setColor(request.color());
        sector.setPoints(request.points() == null ? null : request.points().stream()
                .map(point -> new SectorPoint(point.x(), point.y()))
                .toList());
    }

    private void validateUniqueNames(List<SectorRequest> requests) {
        Set<String> names = new HashSet<>();
        for (SectorRequest request : requests) {
            if (!names.add(request.name().strip().toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("El nombre de sector \"" + request.name().strip() + "\" está repetido.");
            }
        }
    }

    private void delete(List<Sector> sectors) {
        if (sectors.isEmpty()) {
            return;
        }
        // Solo se borran sectores sin entradas vigentes: sus órdenes, todas canceladas,
        // pierden la referencia para no violar la clave foránea.
        orderRepository.findBySectorIn(sectors).forEach(order -> order.setSector(null));
        sectorRepository.deleteAll(sectors);
    }

    private Map<UUID, Long> reservedBySector(InPersonEvent event) {
        Map<UUID, Long> reserved = new HashMap<>();
        for (SectorTicketCount count : sectorRepository.countTicketsBySector(event, RESERVED_STATUSES)) {
            reserved.put(count.sectorId(), count.count());
        }
        return reserved;
    }
}
