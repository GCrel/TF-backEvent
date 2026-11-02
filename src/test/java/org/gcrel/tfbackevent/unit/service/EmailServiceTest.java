package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;

import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.auth.service.dto.TicketQr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "organizador@ejemplo.com");
    }

    @Test
    void sendVerificationEmail_Success() {
        String to = "usuario@ejemplo.com";
        String code = "123456";

        emailService.sendVerificationEmail(to, code);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();

        assertEquals(to, Objects.requireNonNull(capturedMessage.getTo())[0]);
        assertEquals("Verifica tu cuenta", capturedMessage.getSubject());

        assertTrue(Objects.requireNonNull(capturedMessage.getText()).contains(code));
        assertTrue(capturedMessage.getText().contains("expira en 15 minutos"));
    }

    @Test
    void sendEmailChangeCode_SendsCodeToTheNewEmail() {
        emailService.sendEmailChangeCode("nuevo@ejemplo.com", "654321");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();

        assertEquals("nuevo@ejemplo.com", Objects.requireNonNull(message.getTo())[0]);
        assertEquals("Confirma tu nuevo email", message.getSubject());
        assertTrue(Objects.requireNonNull(message.getText()).contains("654321"));
    }

    @Test
    void sendPasswordChangeCode_SendsCode() {
        emailService.sendPasswordChangeCode("usuario@ejemplo.com", "654321");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();

        assertEquals("Confirma el cambio de contraseña", message.getSubject());
        assertTrue(Objects.requireNonNull(message.getText()).contains("654321"));
        assertTrue(message.getText().contains("expira en 15 minutos"));
    }

    @Test
    void sendTicketEmail_Success() throws Exception {
        String to = "comprador@ejemplo.com";
        jakarta.mail.internet.MimeMessage mimeMessage = mock(jakarta.mail.internet.MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // build minimal event and ticket objects
        org.gcrel.tfbackevent.model.InPersonEvent event = new org.gcrel.tfbackevent.model.InPersonEvent(
                "Concierto",
                "Descripción",
                java.time.LocalDateTime.now().plusDays(10),
                new java.math.BigDecimal("120.00"),
                "Lugar 1",
                new java.math.BigDecimal("-34.60"),
                new java.math.BigDecimal("-58.38"),
                500);

        org.gcrel.tfbackevent.model.User attendee = new org.gcrel.tfbackevent.model.User(
                "Asistente",
                "asistente@ejemplo.com",
                "pwd",
                java.time.LocalDate.of(1995, 6, 15));

        org.gcrel.tfbackevent.model.TicketOrder order = new org.gcrel.tfbackevent.model.TicketOrder(
                attendee, event, 2, event.getBasePrice(), java.time.LocalDateTime.now());

        // a simple base64 string representing an image (may be empty content for the
        // test)
        String qrBase64 = java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 });

        emailService.sendTicketEmail(to, order, java.util.List.of(
                new TicketQr(java.util.UUID.randomUUID(), qrBase64),
                new TicketQr(java.util.UUID.randomUUID(), qrBase64)));

        // verify that the JavaMailSender created and sent the MimeMessage
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }
}
