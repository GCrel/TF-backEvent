package org.gcrel.tfbackevent.event.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.event.repository.dto.SectorTicketCount;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.Sector;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SectorRepository extends JpaRepository<Sector, UUID> {
    List<Sector> findByEventOrderByName(Event event);

    boolean existsByEvent(Event event);

    // Entradas que ocupan cupo en cada sector del evento; los sectores sin entradas no aparecen.
    @Query("SELECT new org.gcrel.tfbackevent.event.repository.dto.SectorTicketCount(t.order.sector.id, COUNT(t)) "
            + "FROM Ticket t WHERE t.event = :event AND t.order.sector IS NOT NULL AND t.status IN :statuses "
            + "GROUP BY t.order.sector.id")
    List<SectorTicketCount> countTicketsBySector(
            @Param("event") Event event,
            @Param("statuses") Collection<TicketStatus> statuses);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.order.sector = :sector AND t.status IN :statuses")
    long countTickets(@Param("sector") Sector sector, @Param("statuses") Collection<TicketStatus> statuses);
}
