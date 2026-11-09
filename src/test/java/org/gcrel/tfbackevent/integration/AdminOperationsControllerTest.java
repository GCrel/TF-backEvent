package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.controller.AdminAuditController;
import org.gcrel.tfbackevent.admin.controller.AdminEventController;
import org.gcrel.tfbackevent.admin.controller.AdminSupportController;
import org.gcrel.tfbackevent.admin.dto.response.AuditLogResponse;
import org.gcrel.tfbackevent.admin.dto.response.PaymentAdminResponse;
import org.gcrel.tfbackevent.admin.dto.response.TicketAdminResponse;
import org.gcrel.tfbackevent.admin.exception.AdminConflictException;
import org.gcrel.tfbackevent.admin.service.AdminEventService;
import org.gcrel.tfbackevent.admin.service.AdminSupportService;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.event.exception.EventHasTicketsException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.PaymentProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = { AdminEventController.class, AdminSupportController.class, AdminAuditController.class },
        properties = { "cookie.secure=true", "cookie.same-site=Strict",
                "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000" })
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
class AdminOperationsControllerTest {
    private static final String BASE = "/api/v1/admin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminEventService adminEventService;
    @MockitoBean
    private AdminSupportService adminSupportService;
    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private UserRepository userRepository;

    private Authentication adminAuth;
    private Authentication userAuth;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User admin = new User("Ana", "ana@example.com", "hash", LocalDate.of(1990, 1, 1));
        admin.setSystemRole(SystemRole.ADMIN);
        adminAuth = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        User regular = new User("Bruno", "bruno@example.com", "hash", LocalDate.of(1995, 1, 1));
        userAuth = new UsernamePasswordAuthenticationToken(regular, null, regular.getAuthorities());
    }

    @Test
    void everyEndpoint_WithoutSession_Returns401() throws Exception {
        mockMvc.perform(get(BASE + "/events")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/audit-log")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyEndpoint_WithRegularUser_Returns403AndNeverReachesTheService() throws Exception {
        mockMvc.perform(get(BASE + "/events").with(authentication(userAuth))).andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/events/{id}/cancel", id).with(authentication(userAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/payments").with(authentication(userAuth))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/payments/{id}/reconcile", id).with(authentication(userAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/tickets").with(authentication(userAuth))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/tickets/{id}/resend-delivery", id).with(authentication(userAuth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/audit-log").with(authentication(userAuth))).andExpect(status().isForbidden());
        verify(adminEventService, never()).cancel(any(), any());
        verify(adminSupportService, never()).reconcile(any(), any());
    }

    @Test
    void cancelEvent_Returns204() throws Exception {
        mockMvc.perform(patch(BASE + "/events/{id}/cancel", id).with(authentication(adminAuth)))
                .andExpect(status().isNoContent());
        verify(adminEventService).cancel(any(User.class), eq(id));
    }

    @Test
    void cancelEvent_WhenItHasTickets_Returns409() throws Exception {
        doThrow(new EventHasTicketsException()).when(adminEventService).cancel(any(User.class), eq(id));
        mockMvc.perform(patch(BASE + "/events/{id}/cancel", id).with(authentication(adminAuth)))
                .andExpect(status().isConflict());
    }

    @Test
    void searchPayments_PassesTheFilters() throws Exception {
        UUID userId = UUID.randomUUID();
        PaymentAdminResponse payment = new PaymentAdminResponse(id, "tx-1", PaymentStatus.PENDING, null, null,
                new BigDecimal("10.00"), LocalDateTime.now(), null, UUID.randomUUID(), UUID.randomUUID(),
                "Conf", userId, "Bruno");
        when(adminSupportService.searchPayments(eq(userId), any(), eq(PaymentStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE + "/payments").param("userId", userId.toString()).param("status", "PENDING")
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value("tx-1"))
                .andExpect(jsonPath("$.content[0].buyerName").value("Bruno"));
    }

    @Test
    void reconcile_Returns200WithThePayment() throws Exception {
        PaymentAdminResponse payment = new PaymentAdminResponse(id, "tx-1", PaymentStatus.APPROVED, "approved", null,
                new BigDecimal("10.00"), LocalDateTime.now(), null, UUID.randomUUID(), UUID.randomUUID(),
                "Conf", UUID.randomUUID(), "Bruno");
        when(adminSupportService.reconcile(any(User.class), eq(id))).thenReturn(payment);

        mockMvc.perform(post(BASE + "/payments/{id}/reconcile", id).with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void reconcile_WhenNotPending_Returns409() throws Exception {
        when(adminSupportService.reconcile(any(User.class), eq(id)))
                .thenThrow(new AdminConflictException("Solo se pueden reconciliar pagos pendientes."));
        mockMvc.perform(post(BASE + "/payments/{id}/reconcile", id).with(authentication(adminAuth)))
                .andExpect(status().isConflict());
    }

    @Test
    void reconcile_WhenMercadoPagoFails_Returns502() throws Exception {
        when(adminSupportService.reconcile(any(User.class), eq(id)))
                .thenThrow(new PaymentProcessingException("timeout", null));
        mockMvc.perform(post(BASE + "/payments/{id}/reconcile", id).with(authentication(adminAuth)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void searchTickets_Returns200() throws Exception {
        TicketAdminResponse ticket = new TicketAdminResponse(id, UUID.randomUUID(), "Conf", UUID.randomUUID(),
                "Bruno", null, TicketStatus.ACTIVE, LocalDateTime.now(), null, UUID.randomUUID(),
                DeliveryStatus.SENT);
        when(adminSupportService.searchTickets(any(), any(), eq(TicketStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(ticket), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE + "/tickets").param("status", "ACTIVE").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].deliveryStatus").value("SENT"));
    }

    @Test
    void resendDelivery_Returns204() throws Exception {
        mockMvc.perform(post(BASE + "/tickets/{id}/resend-delivery", id).with(authentication(adminAuth)))
                .andExpect(status().isNoContent());
        verify(adminSupportService).resendDelivery(any(User.class), eq(id));
    }

    @Test
    void searchAuditLog_PassesTheFiltersAndReturnsAPage() throws Exception {
        AuditLogResponse entry = new AuditLogResponse(id, UUID.randomUUID(), "Ana", AuditAction.USER_BLOCKED,
                AuditTargetType.USER, UUID.randomUUID(), "Spam", LocalDateTime.now());
        when(auditService.search(any(), eq(AuditAction.USER_BLOCKED), any(), eq(LocalDate.of(2026, 1, 1)), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(BASE + "/audit-log").param("action", "USER_BLOCKED").param("from", "2026-01-01")
                .with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorName").value("Ana"))
                .andExpect(jsonPath("$.content[0].details").value("Spam"));
    }

    @Test
    void searchAuditLog_WithAnInvalidAction_Returns400() throws Exception {
        mockMvc.perform(get(BASE + "/audit-log").param("action", "NOPE").with(authentication(adminAuth)))
                .andExpect(status().isBadRequest());
    }
}
