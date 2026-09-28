package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.payment.controller.MpController;
import org.gcrel.tfbackevent.payment.dto.response.MpAccountStatusResponse;
import org.gcrel.tfbackevent.payment.service.MpOAuthService;
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

@WebMvcTest(controllers = MpController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
public class MpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpOAuthService mpOAuthService;

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
        testUser = new User("Jane Doe", "jane@example.com", "password", LocalDate.of(1992, 2, 2));
        testUser.setId(UUID.randomUUID());
        authentication = new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    @Test
    void connect_Success_Returns302AndLocationHeader() throws Exception {
        String expectedAuthUrl = "https://auth.mercadopago.com/authorization?client_id=test&state=" + testUser.getId();

        when(mpOAuthService.buildAuthorizationUrl(testUser.getId())).thenReturn(expectedAuthUrl);

        mockMvc.perform(get("/api/v1/mp/connect").with(authentication(authentication)))
                .andExpect(status().isFound()) // Código 302
                .andExpect(header().string("Location", expectedAuthUrl));
    }

    @Test
    void callback_Success_Returns200AndBody() throws Exception {
        UUID stateUserId = UUID.randomUUID();
        String authCode = "AUTH_CODE_123";

        MpAccountStatusResponse expectedResponse = new MpAccountStatusResponse(
                true,
                "organizer@mp.com",
                LocalDateTime.now());

        when(mpOAuthService.linkAccount(eq(authCode), eq(stateUserId))).thenReturn(expectedResponse);

        mockMvc.perform(get("/api/v1/mp/callback")
                .param("code", authCode)
                .param("state", stateUserId.toString())
                .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.mpEmail").value("organizer@mp.com"));
    }

    @Test
    void status_Success_Returns200AndBody() throws Exception {
        MpAccountStatusResponse expectedResponse = new MpAccountStatusResponse(
                true,
                "organizer@mp.com",
                LocalDateTime.now());

        when(mpOAuthService.getAccountStatus(any(User.class))).thenReturn(expectedResponse);

        mockMvc.perform(get("/api/v1/mp/status").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.mpEmail").value("organizer@mp.com"));
    }

    @Test
    void disconnect_Success_Returns204() throws Exception {
        doNothing().when(mpOAuthService).unlinkAccount(any(User.class));

        mockMvc.perform(delete("/api/v1/mp/disconnect").with(authentication(authentication)))
                .andExpect(status().isNoContent());
    }
}
