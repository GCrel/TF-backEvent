package org.gcrel.tfbackevent.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.JwtService;
import org.gcrel.tfbackevent.common.config.GlobalExceptionHandler;
import org.gcrel.tfbackevent.common.config.SecurityConfig;
import org.gcrel.tfbackevent.payment.controller.WebhookController;
import org.gcrel.tfbackevent.payment.dto.request.WebhookPaymentRequest;
import org.gcrel.tfbackevent.payment.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = WebhookController.class)
@Import({ GlobalExceptionHandler.class, SecurityConfig.class })
@AutoConfigureMockMvc
public class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @MockitoBean
    private WebhookService webhookService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void receivePaymentNotification_Success_Returns200AndCallsService() throws Exception {
        WebhookPaymentRequest.Data data = new WebhookPaymentRequest.Data("12345");
        WebhookPaymentRequest request = new WebhookPaymentRequest("payment", "payment", data);

        doNothing()
                .when(webhookService)
                .processPaymentNotification(
                        any(WebhookPaymentRequest.class), eq("sig"), eq("req-1"), eq("12345"));

        mockMvc.perform(
                post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "sig")
                        .header("x-request-id", "req-1")
                        .queryParam("data.id", "12345")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(webhookService)
                .processPaymentNotification(
                        any(WebhookPaymentRequest.class), eq("sig"), eq("req-1"), eq("12345"));
    }
}
