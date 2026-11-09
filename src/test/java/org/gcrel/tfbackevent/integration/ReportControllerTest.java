package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.event.exception.EventNotFoundException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.EventType;
import org.gcrel.tfbackevent.report.controller.ReportController;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse.ControllerValidations;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse.HourlyAccess;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse.DailySales;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse.PaymentCounts;
import org.gcrel.tfbackevent.report.exception.ReportAccessDeniedException;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReportController.class, properties = { "cookie.secure=true", "cookie.same-site=Strict",
        "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000" })
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
class ReportControllerTest {
    private static final String BASE = "/api/v1/reports";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private UserRepository userRepository;

    private Authentication auth;
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = new User("Org", "org@example.com", "hash", LocalDate.of(1990, 1, 1));
        auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void summary_WithoutSession_Returns401() throws Exception {
        mockMvc.perform(get(BASE + "/events/{id}/summary", eventId)).andExpect(status().isUnauthorized());
    }

    @Test
    void summary_Returns200WithEveryFigureAndNullRates() throws Exception {
        when(reportService.eventSummary(eq(eventId), any(User.class))).thenReturn(new EventSummaryResponse(
                eventId, "Conf", EventType.VIRTUAL, EventStatus.PUBLISHED, 8, 2, new BigDecimal("300.00"),
                new BigDecimal("37.50"), new PaymentCounts(3, 1, 1), new BigDecimal("0.7500"), null, null, 4,
                new BigDecimal("0.5000")));

        mockMvc.perform(get(BASE + "/events/{id}/summary", eventId).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("VIRTUAL"))
                .andExpect(jsonPath("$.ticketsSold").value(8))
                .andExpect(jsonPath("$.ticketsPending").value(2))
                .andExpect(jsonPath("$.revenue").value(300.00))
                .andExpect(jsonPath("$.averageTicketPrice").value(37.50))
                .andExpect(jsonPath("$.payments.approved").value(3))
                .andExpect(jsonPath("$.payments.rejected").value(1))
                .andExpect(jsonPath("$.payments.pending").value(1))
                .andExpect(jsonPath("$.approvalRate").value(0.75))
                .andExpect(jsonPath("$.capacity").value((Object) null))
                .andExpect(jsonPath("$.occupancyRate").value((Object) null))
                .andExpect(jsonPath("$.attendance").value(4))
                .andExpect(jsonPath("$.attendanceRate").value(0.5));
    }

    @Test
    void summary_WhenNotTheOrganizer_Returns403() throws Exception {
        when(reportService.eventSummary(eq(eventId), any(User.class))).thenThrow(new ReportAccessDeniedException());

        mockMvc.perform(get(BASE + "/events/{id}/summary", eventId).with(authentication(auth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No tenés permisos para ver los reportes de este evento."));
    }

    @Test
    void summary_WhenTheEventDoesNotExist_Returns404() throws Exception {
        when(reportService.eventSummary(eq(eventId), any(User.class))).thenThrow(new EventNotFoundException(eventId));

        mockMvc.perform(get(BASE + "/events/{id}/summary", eventId).with(authentication(auth)))
                .andExpect(status().isNotFound());
    }

    @Test
    void summary_WithAMalformedId_Returns400() throws Exception {
        mockMvc.perform(get(BASE + "/events/{id}/summary", "no-es-un-uuid").with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sales_Returns200WithOneRowPerDay() throws Exception {
        when(reportService.eventSales(eq(eventId), any(User.class))).thenReturn(new EventSalesResponse(eventId, List.of(
                new DailySales(LocalDate.of(2026, 3, 10), 2, new BigDecimal("100.00")),
                new DailySales(LocalDate.of(2026, 3, 11), 0, new BigDecimal("0.00")))));

        mockMvc.perform(get(BASE + "/events/{id}/sales", eventId).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.days[0].tickets").value(2))
                .andExpect(jsonPath("$.days[0].revenue").value(100.00))
                .andExpect(jsonPath("$.days[1].tickets").value(0));
    }

    @Test
    void attendance_ForAnInPersonEvent_ReturnsHoursAndControllers() throws Exception {
        List<HourlyAccess> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hours.add(new HourlyAccess(hour, hour == 9 ? 3 : 0));
        }
        UUID controllerId = UUID.randomUUID();
        when(reportService.eventAttendance(eq(eventId), any(User.class))).thenReturn(new EventAttendanceResponse(
                eventId, hours, List.of(new ControllerValidations(controllerId, "Ana", 2))));

        mockMvc.perform(get(BASE + "/events/{id}/attendance", eventId).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byHour.length()").value(24))
                .andExpect(jsonPath("$.byHour[9].accesses").value(3))
                .andExpect(jsonPath("$.byController[0].name").value("Ana"))
                .andExpect(jsonPath("$.byController[0].validations").value(2));
    }

    @Test
    void attendance_ForAVirtualEvent_OmitsTheControllersField() throws Exception {
        when(reportService.eventAttendance(eq(eventId), any(User.class))).thenReturn(
                new EventAttendanceResponse(eventId, List.of(new HourlyAccess(0, 0)), null));

        mockMvc.perform(get(BASE + "/events/{id}/attendance", eventId).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byHour").exists())
                .andExpect(jsonPath("$.byController").doesNotExist());
    }

    @Test
    void series_WithoutSession_Return401() throws Exception {
        mockMvc.perform(get(BASE + "/events/{id}/sales", eventId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/events/{id}/attendance", eventId)).andExpect(status().isUnauthorized());
    }

    @Test
    void series_WhenNotTheOrganizerOrTheEventIsMissing_Return403And404() throws Exception {
        when(reportService.eventSales(eq(eventId), any(User.class))).thenThrow(new ReportAccessDeniedException());
        when(reportService.eventAttendance(eq(eventId), any(User.class)))
                .thenThrow(new EventNotFoundException(eventId));

        mockMvc.perform(get(BASE + "/events/{id}/sales", eventId).with(authentication(auth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/events/{id}/attendance", eventId).with(authentication(auth)))
                .andExpect(status().isNotFound());
    }
}
