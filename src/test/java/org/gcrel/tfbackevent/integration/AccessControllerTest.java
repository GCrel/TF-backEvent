package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.access.controller.AccessController;
import org.gcrel.tfbackevent.access.dto.request.ValidateQrRequest;
import org.gcrel.tfbackevent.access.dto.response.AccessValidationResponse;
import org.gcrel.tfbackevent.access.dto.response.VirtualAccessResponse;
import org.gcrel.tfbackevent.access.exception.AccessWindowClosedException;
import org.gcrel.tfbackevent.access.exception.TicketAlreadyUsedException;
import org.gcrel.tfbackevent.access.exception.TicketEventMismatchException;
import org.gcrel.tfbackevent.access.service.AccessService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.event.dto.response.EventResponse;
import org.gcrel.tfbackevent.event.exception.InvalidEventTypeException;
import org.gcrel.tfbackevent.event.exception.UnauthorizedEventAccessException;
import org.gcrel.tfbackevent.event.service.EventService;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@WebMvcTest(controllers = AccessController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
class AccessControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessService accessService;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private User user;
    private UsernamePasswordAuthenticationToken authentication;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        user = new User("Controller", "controller@example.com", "password", LocalDate.of(1990, 1, 1));
        authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void validateQr_Returns200AndValidationResponse() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(accessService.validateQr(
                any(ValidateQrRequest.class), any(User.class)))
                .thenReturn(new AccessValidationResponse(
                        ticketId, "Attendee", "Conference", "VIP", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/access/validate-qr")
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ValidateQrRequest(eventId, ticketId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.attendeeName").value("Attendee"))
                .andExpect(jsonPath("$.eventTitle").value("Conference"))
                .andExpect(jsonPath("$.sectorName").value("VIP"));
    }

    @Test
    void validateQr_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/access/validate-qr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ValidateQrRequest(UUID.randomUUID(), UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validateQr_WhenUnauthorized_Returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new UnauthorizedEventAccessException()).when(accessService)
                .validateQr(any(ValidateQrRequest.class), any(User.class));
        performValidQr(eventId).andExpect(status().isForbidden());
    }

    @Test
    void validateQr_WhenWindowClosed_Returns409() throws Exception {
        doThrow(new AccessWindowClosedException()).when(accessService)
                .validateQr(any(ValidateQrRequest.class), any(User.class));
        performValidQr(UUID.randomUUID()).andExpect(status().isConflict());
    }

    @Test
    void validateQr_WhenTicketAlreadyUsed_Returns409() throws Exception {
        doThrow(new TicketAlreadyUsedException(LocalDateTime.of(2026, 10, 10, 18, 0)))
                .when(accessService).validateQr(any(ValidateQrRequest.class), any(User.class));
        performValidQr(UUID.randomUUID()).andExpect(status().isConflict());
    }

    @Test
    void validateQr_WhenTicketBelongsToOtherEvent_Returns400() throws Exception {
        doThrow(new TicketEventMismatchException()).when(accessService)
                .validateQr(any(ValidateQrRequest.class), any(User.class));
        performValidQr(UUID.randomUUID()).andExpect(status().isBadRequest());
    }

    @Test
    void validateQr_WhenTicketMissing_Returns404() throws Exception {
        doThrow(new TicketNotFoundException(UUID.randomUUID())).when(accessService)
                .validateQr(any(ValidateQrRequest.class), any(User.class));
        performValidQr(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void validateQr_WhenQrIsBlank_Returns400WithFieldErrors() throws Exception {
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"qrCode\":\"\"}";
        mockMvc.perform(post("/api/v1/access/validate-qr")
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.qrCode").exists());
    }

    @Test
    void getVirtualAccess_ReturnsCredentials() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(accessService.getVirtualAccess(ticketId, user)).thenReturn(
                new VirtualAccessResponse(
                        "Online Conference",
                        LocalDateTime.of(2026, 10, 15, 18, 0),
                        "Meet",
                        "https://meet.example.com/room",
                        "Use the link"));

        mockMvc.perform(get("/api/v1/access/virtual/{ticketId}", ticketId)
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTitle").value("Online Conference"))
                .andExpect(jsonPath("$.platform").value("Meet"))
                .andExpect(jsonPath("$.accessLink").value("https://meet.example.com/room"));
    }

    @Test
    void getVirtualAccess_WhenEventIsNotVirtual_Returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doThrow(new InvalidEventTypeException()).when(accessService)
                .getVirtualAccess(ticketId, user);

        mockMvc.perform(get("/api/v1/access/virtual/{ticketId}", ticketId)
                .with(authentication(authentication)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyEvents_UsesEventServiceAndReturnsEvents() throws Exception {
        EventResponse response = new EventResponse(
                UUID.randomUUID(), "Conference", "Description",
                LocalDateTime.of(2026, 10, 15, 18, 0), "PUBLISHED",
                new java.math.BigDecimal("20.00"), "IN_PERSON", "Address",
                new java.math.BigDecimal("-34.60"), new java.math.BigDecimal("-58.38"),
                100, 100, null, null, null, null, "Ana García", null, null, null);
        when(eventService.getControlledEvents(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/access/my-events")
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Conference"))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions performValidQr(UUID eventId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/access/validate-qr")
                .with(authentication(authentication))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ValidateQrRequest(eventId, UUID.randomUUID().toString()))));
    }
}
