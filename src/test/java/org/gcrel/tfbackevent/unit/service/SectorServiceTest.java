package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.event.dto.request.SectorPointRequest;
import org.gcrel.tfbackevent.event.dto.request.SectorRequest;
import org.gcrel.tfbackevent.event.exception.SectorHasTicketsException;
import org.gcrel.tfbackevent.event.repository.SectorRepository;
import org.gcrel.tfbackevent.event.repository.dto.SectorTicketCount;
import org.gcrel.tfbackevent.event.service.SectorService;
import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.Sector;
import org.gcrel.tfbackevent.model.SectorPoint;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.payment.repository.TicketOrderRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SectorServiceTest {
    private static final List<SectorPointRequest> TRIANGLE = List.of(
            new SectorPointRequest(0.1, 0.1), new SectorPointRequest(0.5, 0.1), new SectorPointRequest(0.5, 0.5));

    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketOrderRepository orderRepository;

    @InjectMocks
    private SectorService sectorService;

    private InPersonEvent event;

    @BeforeEach
    void setUp() {
        event = new InPersonEvent("Recital", "Description", LocalDateTime.now().plusDays(10),
                new BigDecimal("100.00"), "Calle 123", new BigDecimal("-34.6"), new BigDecimal("-58.4"), 500);
        event.setId(UUID.randomUUID());
        lenient().when(sectorRepository.countTicketsBySector(any(), any())).thenReturn(List.of());
    }

    @Test
    void replaceSectors_CreatesUpdatesAndDeletesAndDerivesPriceAndCapacity() {
        Sector kept = existing("VIP", "15000.00", 50);
        Sector removed = existing("Palco", "20000.00", 10);
        when(sectorRepository.findByEventOrderByName(event)).thenReturn(List.of(kept, removed));

        sectorService.replaceSectors(event, List.of(
                request(kept.getId(), "VIP Gold", "15000.00", 60),
                request(null, "General", "5000.00", 200)));

        assertEquals("VIP Gold", kept.getName());
        assertEquals(60, kept.getCapacity());
        assertEquals(List.of(new SectorPoint(0.1, 0.1), new SectorPoint(0.5, 0.1), new SectorPoint(0.5, 0.5)),
                kept.getPoints());
        verify(sectorRepository).deleteAll(List.of(removed));
        // Precio "desde" = mínimo; cupo del evento = suma de sectores.
        assertEquals(new BigDecimal("5000.00"), event.getBasePrice());
        assertEquals(260, event.getCapacity());
    }

    @Test
    void replaceSectors_DetachesCancelledOrdersBeforeDeletingASector() {
        Sector removed = existing("Palco", "20000.00", 10);
        TicketOrder cancelledOrder = org.mockito.Mockito.mock(TicketOrder.class);
        when(sectorRepository.findByEventOrderByName(event)).thenReturn(List.of(removed));
        when(orderRepository.findBySectorIn(List.of(removed))).thenReturn(List.of(cancelledOrder));

        sectorService.replaceSectors(event, List.of(request(null, "General", "5000.00", 100)));

        verify(cancelledOrder).setSector(null);
        verify(sectorRepository).deleteAll(List.of(removed));
    }

    @Test
    void replaceSectors_WithDuplicatedNames_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> sectorService.replaceSectors(event, List.of(
                request(null, "VIP", "100.00", 10),
                request(null, " vip ", "200.00", 10))));
    }

    @Test
    void replaceSectors_WithForeignOrRepeatedId_ThrowsBadRequest() {
        Sector kept = existing("VIP", "100.00", 10);
        when(sectorRepository.findByEventOrderByName(event)).thenReturn(List.of(kept));

        assertThrows(BadRequestException.class, () -> sectorService.replaceSectors(event,
                List.of(request(UUID.randomUUID(), "Otro", "100.00", 10))));
        assertThrows(BadRequestException.class, () -> sectorService.replaceSectors(event, List.of(
                request(kept.getId(), "VIP", "100.00", 10),
                request(kept.getId(), "VIP 2", "100.00", 10))));
    }

    @Test
    void replaceSectors_SectorWithTickets_KeepsPriceAndCannotShrinkBelowSoldOrBeDeleted() {
        Sector sold = existing("VIP", "15000.00", 50);
        when(sectorRepository.findByEventOrderByName(event)).thenReturn(List.of(sold));
        when(sectorRepository.countTicketsBySector(any(), any()))
                .thenReturn(List.of(new SectorTicketCount(sold.getId(), 20)));

        assertThrows(SectorHasTicketsException.class, () -> sectorService.replaceSectors(event,
                List.of(request(sold.getId(), "VIP", "16000.00", 50))));
        assertThrows(SectorHasTicketsException.class, () -> sectorService.replaceSectors(event,
                List.of(request(sold.getId(), "VIP", "15000.00", 19))));
        // Borrarlo: la lista nueva no lo incluye.
        assertThrows(SectorHasTicketsException.class, () -> sectorService.replaceSectors(event,
                List.of(request(null, "General", "1.00", 10))));
        verify(sectorRepository, never()).deleteAll(anyCollection());

        // Nombre, geometría y cupo (sin bajar de lo vendido) sí se pueden editar.
        sectorService.replaceSectors(event, List.of(request(sold.getId(), "Platea VIP", "15000.00", 20)));
        assertEquals("Platea VIP", sold.getName());
    }

    @Test
    void replaceSectors_CannotSwitchModeWhenTheEventHasTickets() {
        when(sectorRepository.findByEventOrderByName(event)).thenReturn(List.of());
        when(ticketRepository.countByEventAndStatusIn(any(), any())).thenReturn(3L);

        assertThrows(SectorHasTicketsException.class, () -> sectorService.replaceSectors(event,
                List.of(request(null, "General", "100.00", 10))));
    }

    @Test
    void replaceSectors_WithoutPoints_CreatesSectorWithoutGeometry() {
        when(sectorRepository.findByEventOrderByName(event)).thenReturn(List.of());
        org.mockito.ArgumentCaptor<List<Sector>> saved = org.mockito.ArgumentCaptor.forClass(List.class);

        sectorService.replaceSectors(event, List.of(
                new SectorRequest(null, "General", new BigDecimal("5000.00"), 200, null, null)));

        verify(sectorRepository).saveAll(saved.capture());
        assertEquals(null, saved.getValue().get(0).getPoints());
        assertEquals(200, event.getCapacity());
    }

    private Sector existing(String name, String price, int capacity) {
        Sector sector = new Sector(event);
        sector.setId(UUID.randomUUID());
        sector.setName(name);
        sector.setPrice(new BigDecimal(price));
        sector.setCapacity(capacity);
        sector.setPoints(List.of(new SectorPoint(0, 0), new SectorPoint(1, 0), new SectorPoint(1, 1)));
        return sector;
    }

    private SectorRequest request(UUID id, String name, String price, int capacity) {
        return new SectorRequest(id, name, new BigDecimal(price), capacity, "#e63946", TRIANGLE);
    }
}
