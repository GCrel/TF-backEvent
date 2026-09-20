package org.gcrel.tfbackevent.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import lombok.extern.slf4j.Slf4j;

import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.exception.TicketDeliveryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@Slf4j
public class EmailService {
    @Value("${SMTP_FROM}")
    private String fromEmail;

    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Verifica tu cuenta");
        message.setText(
                "Tu código de verificación es: " + code + "\nEste código expira en 15 minutos.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("No se pudo enviar el email de verificación. to={}", to, e);
        }
    }

    public void sendTicketEmail(String to, Ticket ticket, String qrBase64) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Tu entrada para " + ticket.getEvent().getTitle());
            helper.setText(
                    "<h3>¡Gracias por tu compra!</h3>"
                            + "<p>Presentá este código QR en la puerta del evento:</p><br>"
                            + "<img src='cid:qrImage' style='width: 250px; border-radius: 8px;'>",
                    true);
            byte[] qrBytes = Base64.getDecoder().decode(qrBase64);
            helper.addInline("qrImage", new ByteArrayResource(qrBytes), "image/png");

            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new TicketDeliveryException(
                    "No se pudo enviar el email de entrada para el ticket " + ticket.getId(), e);
        }
    }
}
