package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.controller.AdminStatsController;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.EventStatus;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse.PaymentCounts;
import org.gcrel.tfbackevent.report.dto.response.GlobalStatsResponse;
import org.gcrel.tfbackevent.report.dto.response.GlobalStatsResponse.UserCounts;
import org.gcrel.tfbackevent.report.dto.response.OrganizerSummaryResponse.TopEvent;
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

@WebMvcTest(controllers = AdminStatsController.class, properties = { "cookie.secure=true", "cookie.same-site=Strict",
        "jwt.access-token.expiration=1800000", "jwt.refresh-token.expiration=604800000" })
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
class AdminStatsControllerTest {
    private static final String URL = "/api/v1/admin/stats/overview";

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

    private Authentication adminAuth;
    private Authentication userAuth;

    @BeforeEach
    void setUp() {
        User admin = new User("Ana", "ana@example.com", "hash", LocalDate.of(1990, 1, 1));
        admin.setSystemRole(SystemRole.ADMIN);
        adminAuth = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
        User regular = new User("Bruno", "bruno@example.com", "hash", LocalDate.of(1995, 1, 1));
        userAuth = new UsernamePasswordAuthenticationToken(regular, null, regular.getAuthorities());
    }

    @Test
    void overview_WithoutSession_Returns401() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-03-11").param("to", "2026-03-12"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overview_WithARegularUser_Returns403AndNeverReachesTheService() throws Exception {
        mockMvc.perform(get(URL).param("from", "2026-03-11").param("to", "2026-03-12").with(authentication(userAuth)))
                .andExpect(status().isForbidden());

        verify(reportService, never()).globalOverview(any(), any());
    }

    @Test
    void overview_ReturnsThePeriodFigures() throws Exception {
        when(reportService.globalOverview(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 12))).thenReturn(
                new GlobalStatsResponse(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 12), 8, new BigDecimal("265.00"),
                        new PaymentCounts(4, 1, 0),
                        Map.of(EventStatus.PUBLISHED, 3L, EventStatus.DRAFT, 1L, EventStatus.CANCELLED, 0L, EventStatus.FINISHED, 0L),
                        List.of(new TopEvent(UUID.randomUUID(), "Conf", new BigDecimal("200.00"), 4)),
                        new UserCounts(8, 2)));

        mockMvc.perform(get(URL).param("from", "2026-03-11").param("to", "2026-03-12").with(authentication(adminAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-03-11"))
                .andExpect(jsonPath("$.to").value("2026-03-12"))
                .andExpect(jsonPath("$.ticketsSold").value(8))
                .andExpect(jsonPath("$.revenue").value(265.00))
                .andExpect(jsonPath("$.payments.approved").value(4))
                .andExpect(jsonPath("$.eventsByStatus.PUBLISHED").value(3))
                .andExpect(jsonPath("$.topEvents[0].title").value("Conf"))
                .andExpect(jsonPath("$.users.total").value(8))
                .andExpect(jsonPath("$.users.newInPeriod").value(2));
    }

    @Test
    void overview_WithoutTheRange_Returns400() throws Exception {
        mockMvc.perform(get(URL).with(authentication(adminAuth))).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("from", "2026-03-11").with(authentication(adminAuth)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("to", "2026-03-11").with(authentication(adminAuth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_WithAMalformedDate_Returns400() throws Exception {
        mockMvc.perform(get(URL).param("from", "11/03/2026").param("to", "2026-03-12").with(authentication(adminAuth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_WhenTheServiceRejectsTheRange_Returns400() throws Exception {
        when(reportService.globalOverview(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 11)))
                .thenThrow(new BadRequestException("La fecha 'from' no puede ser posterior a 'to'."));

        mockMvc.perform(get(URL).param("from", "2026-03-12").param("to", "2026-03-11").with(authentication(adminAuth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La fecha 'from' no puede ser posterior a 'to'."));
    }
}
