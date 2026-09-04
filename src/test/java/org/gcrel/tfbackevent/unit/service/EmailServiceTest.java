package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.gcrel.tfbackevent.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {
    @Mock private JavaMailSender mailSender;

    @InjectMocks private EmailService emailService;

    @Test
    void sendVerificationEmail_Success() {
        String to = "usuario@ejemplo.com";
        String code = "123456";

        emailService.sendVerificationEmail(to, code);

        ArgumentCaptor<SimpleMailMessage> messageCaptor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage capturedMessage = messageCaptor.getValue();

        assertEquals(to, Objects.requireNonNull(capturedMessage.getTo())[0]);
        assertEquals("Verifica tu cuenta", capturedMessage.getSubject());

        assertTrue(Objects.requireNonNull(capturedMessage.getText()).contains(code));
        assertTrue(capturedMessage.getText().contains("expira en 15 minutos"));
    }
}
