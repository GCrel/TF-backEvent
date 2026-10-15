package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.event.controller.EventController;
import org.gcrel.tfbackevent.event.dto.request.CreateEventRequest;
import org.gcrel.tfbackevent.event.dto.request.UpdateEventRequest;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@WebMvcTest(controllers = EventController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
public class EventControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository userRepository;

    private User testUser;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testUser = new User("John Doe", "test@example.com", "password", LocalDate.of(1990, 1, 1));
        authentication = new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    @Test
    void createEvent_Success_Returns201AndBody() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                "Tech Meetup",
                "Spring Boot meetup",
                LocalDateTime.of(2027, 9, 10, 19, 0),
                new BigDecimal("25.00"),
                EventType.IN_PERSON,
                "Main St 123",
                new BigDecimal("-34.6037"),
                new BigDecimal("-58.3816"),
                100,
                null,
                null,
                null);

        UUID eventId = UUID.randomUUID();
        EventResponse response = new EventResponse(
                eventId,
                request.title(),
                request.description(),
                request.dateTime(),
                "ACTIVE",
                request.basePrice(),
                "IN_PERSON",
                request.address(),
                request.latitude(),
                request.longitude(),
                request.capacity(),
                request.capacity(),
                null,
                null,
                null,
                null);

        when(eventService.createEvent(any(CreateEventRequest.class), any(User.class)))
                .thenReturn(response);

        mockMvc.perform(
                post("/api/v1/events")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.title").value("Tech Meetup"))
                .andExpect(jsonPath("$.eventType").value("IN_PERSON"));
    }

    @Test
    void createEvent_WithoutAuthentication_Returns401() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                "Tech Meetup",
                "Spring Boot meetup",
                LocalDateTime.of(2026, 9, 10, 19, 0),
                new BigDecimal("25.00"),
                EventType.IN_PERSON,
                "Main St 123",
                new BigDecimal("-34.6037"),
                new BigDecimal("-58.3816"),
                100,
                null,
                null,
                null);

        mockMvc.perform(
                post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateEvent_Success_Returns200AndBody() throws Exception {
        UUID eventId = UUID.randomUUID();
        UpdateEventRequest request = new UpdateEventRequest(
                "Updated Meetup",
                "Updated description",
                LocalDateTime.now().plusDays(5),
                new BigDecimal("30.00"),
                "Updated Address",
                new BigDecimal("-34.6000"),
                new BigDecimal("-58.3800"),
                120,
                null,
                null,
                null);

        EventResponse response = new EventResponse(
                eventId,
                request.title(),
                request.description(),
                request.dateTime(),
                "ACTIVE",
                request.basePrice(),
                "IN_PERSON",
                request.address(),
                request.latitude(),
                request.longitude(),
                request.capacity(),
                request.capacity(),
                null,
                null,
                null,
                null);

        when(eventService.updateEvent(eq(eventId), any(UpdateEventRequest.class), any(User.class)))
                .thenReturn(response);

        mockMvc.perform(
                put("/api/v1/events/{id}", eventId)
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.title").value("Updated Meetup"));
    }

    @Test
    void updateEvent_UnauthorizedOrganizer_Returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        UpdateEventRequest request = new UpdateEventRequest(
                "Updated Meetup",
                "Updated description",
                LocalDateTime.now().plusDays(5),
                new BigDecimal("30.00"),
                "Updated Address",
                new BigDecimal("-34.6000"),
                new BigDecimal("-58.3800"),
                120,
                null,
                null,
                null);

        doThrow(new UnauthorizedEventAccessException())
                .when(eventService)
                .updateEvent(eq(eventId), any(UpdateEventRequest.class), any(User.class));

        mockMvc.perform(
                put("/api/v1/events/{id}", eventId)
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.error")
                                .value("No tenés permisos para modificar este evento."))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void publishEvent_Success_Returns200AndBody() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventResponse response = new EventResponse(
                eventId,
                "Publishable Event",
                "To be published",
                LocalDateTime.of(2026, 12, 1, 20, 0),
                "PUBLISHED",
                new BigDecimal("40.00"),
                "IN_PERSON",
                "Lugar",
                new BigDecimal("-34.6"),
                new BigDecimal("-58.38"),
                100,
                60,
                null,
                null,
                null,
                null);

        when(eventService.publishEvent(eq(eventId), any(User.class))).thenReturn(response);

        mockMvc.perform(
                put("/api/v1/events/{id}/publish", eventId)
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.availableTickets").value(60));
    }

    @Test
    void publishEvent_Unauthorized_Returns403() throws Exception {
        UUID eventId = UUID.randomUUID();

        doThrow(new UnauthorizedEventAccessException())
                .when(eventService)
                .publishEvent(eq(eventId), any(User.class));

        mockMvc.perform(
                put("/api/v1/events/{id}/publish", eventId)
                        .with(authentication(authentication)))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.error")
                                .value("No tenés permisos para modificar este evento."))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getEvent_Success_Returns200AndBody() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventResponse response = new EventResponse(
                eventId,
                "Public Event",
                "Public description",
                LocalDateTime.of(2026, 11, 1, 18, 30),
                "ACTIVE",
                new BigDecimal("15.00"),
                "VIRTUAL",
                null,
                null,
                null,
                null,
                null,
                "Meet",
                null,
                null,
                null);

        when(eventService.getEvent(eq(eventId), any(User.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/events/{id}", eventId).with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.eventType").value("VIRTUAL"))
                .andExpect(jsonPath("$.accessLink").doesNotExist())
                .andExpect(jsonPath("$.accessInstructions").doesNotExist());
    }

    @Test
    void getEvent_WhenOrganizer_IncludesAccessData() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventResponse response = new EventResponse(
                eventId, "Webinar", "Description", LocalDateTime.of(2026, 11, 1, 18, 30),
                "DRAFT", new BigDecimal("15.00"), "VIRTUAL",
                null, null, null, null, null,
                "Meet", "https://meet.example.com/room", "Entrar 5 minutos antes", true);

        when(eventService.getEvent(eq(eventId), any(User.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/events/{id}", eventId).with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessLink").value("https://meet.example.com/room"))
                .andExpect(jsonPath("$.accessInstructions").value("Entrar 5 minutos antes"))
                .andExpect(jsonPath("$.isOrganizer").value(true));
    }

    @Test
    void getEvent_NotFound_Returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEvent(eq(eventId), any(User.class))).thenThrow(new EventNotFoundException(eventId));

        mockMvc.perform(get("/api/v1/events/{id}", eventId).with(authentication(authentication)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllEvents_Success_Returns200AndList() throws Exception {
        EventResponse first = new EventResponse(
                UUID.randomUUID(),
                "Event 1",
                "Desc 1",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "ACTIVE",
                new BigDecimal("10.00"),
                "IN_PERSON",
                "Address 1",
                new BigDecimal("-34.60"),
                new BigDecimal("-58.38"),
                50,
                50,
                null,
                null,
                null,
                null);
        EventResponse second = new EventResponse(
                UUID.randomUUID(),
                "Event 2",
                "Desc 2",
                LocalDateTime.of(2026, 9, 2, 11, 0),
                "ACTIVE",
                new BigDecimal("20.00"),
                "VIRTUAL",
                null,
                null,
                null,
                null,
                null,
                "Zoom",
                null,
                null,
                null);

        when(eventService.getPublishedEvents(
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(first, second), PageRequest.of(0, 2), 5));

        mockMvc.perform(get("/api/v1/events").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Event 1"))
                .andExpect(jsonPath("$.content[1].title").value("Event 2"))
                .andExpect(jsonPath("$.content[0].accessLink").doesNotExist())
                .andExpect(jsonPath("$.content[0].accessInstructions").doesNotExist())
                .andExpect(jsonPath("$.content[1].accessLink").doesNotExist())
                .andExpect(jsonPath("$.content[1].accessInstructions").doesNotExist())
                .andExpect(jsonPath("$.content[0].isOrganizer").doesNotExist())
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void getAllEvents_WithFilters_PassesThemToService() throws Exception {
        when(eventService.getPublishedEvents(eq(EventType.VIRTUAL), eq("java"),
                eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/events")
                .param("eventType", "VIRTUAL")
                .param("q", "java")
                .param("from", "2026-11-01")
                .param("to", "2026-11-30")
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void getAllEvents_WithUnknownEventType_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("eventType", "HYBRID")
                .with(authentication(authentication)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllEvents_WithInvalidDate_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("from", "01/11/2026")
                .with(authentication(authentication)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelEvent_Success_Returns204() throws Exception {
        UUID eventId = UUID.randomUUID();
        doNothing().when(eventService).cancelEvent(eq(eventId), any(User.class));

        mockMvc.perform(
                patch("/api/v1/events/{id}/cancel", eventId)
                        .with(authentication(authentication)))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancelEvent_WithoutAuthentication_Returns401() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/events/{id}/cancel", eventId))
                .andExpect(status().isUnauthorized());
    }
}
