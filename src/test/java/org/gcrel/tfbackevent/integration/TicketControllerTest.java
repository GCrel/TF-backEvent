package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.controller.TicketController;
import org.gcrel.tfbackevent.payment.dto.request.PurchaseTicketRequest;
import org.gcrel.tfbackevent.payment.dto.response.PurchaseResponse;
import org.gcrel.tfbackevent.payment.dto.response.TicketResponse;
import org.gcrel.tfbackevent.payment.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@WebMvcTest(controllers = TicketController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
public class TicketControllerTest {
    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User testUser;
    private Authentication authentication;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        testUser = new User("Jane Doe", "jane@example.com", "password", LocalDate.of(1992, 2, 2));
        authentication = new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    @Test
    void purchase_Success_Returns201AndBody() throws Exception {
        UUID eventId = UUID.randomUUID();
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                eventId, 2, "card_tok", 1, "payer@example.com", "visa");

        UUID orderId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        PurchaseResponse response = new PurchaseResponse(orderId, "approved", "accredited", "Compra aceptada",
                List.of(
                        new PurchaseResponse.PurchasedTicket(ticketId, "dGVzdA=="),
                        new PurchaseResponse.PurchasedTicket(UUID.randomUUID(), "dGVzdA==")));

        when(ticketService.purchase(any(PurchaseTicketRequest.class), any(User.class)))
                .thenReturn(response);

        mockMvc.perform(
                post("/api/v1/tickets/purchase")
                        .with(authentication(authentication))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andExpect(jsonPath("$.tickets[0].ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.status").value("approved"))
                // 2. AÑADIDO: Verificamos que el nuevo campo llegue al cliente
                .andExpect(jsonPath("$.statusDetail").value("accredited"));
    }

    @Test
    void getMyTickets_Success_Returns200AndList() throws Exception {
        TicketResponse first = new TicketResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Evento 1",
                "ACTIVE",
                LocalDateTime.now(),
                "qr1");
        TicketResponse second = new TicketResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Evento 2",
                "ACTIVE",
                LocalDateTime.now(),
                "qr2");

        when(ticketService.getMyTickets(any(User.class))).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/tickets/my-tickets").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventTitle").value("Evento 1"));
    }

    @Test
    void resendEmail_Success_Returns204() throws Exception {
        UUID ticketId = UUID.randomUUID();
        doNothing().when(ticketService).resendTicketEmail(eq(ticketId), any(User.class));

        mockMvc.perform(
                post("/api/v1/tickets/{id}/resend-email", ticketId)
                        .with(authentication(authentication)))
                .andExpect(status().isNoContent());
    }

    @Test
    void getTicket_Success_Returns200AndBody() throws Exception {
        UUID ticketId = UUID.randomUUID();
        TicketResponse response = new TicketResponse(
                ticketId,
                UUID.randomUUID(),
                "Evento X",
                "ACTIVE",
                LocalDateTime.now(),
                "qrcode");

        when(ticketService.getTicket(eq(ticketId), any(User.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId).with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.eventTitle").value("Evento X"));
    }
}
