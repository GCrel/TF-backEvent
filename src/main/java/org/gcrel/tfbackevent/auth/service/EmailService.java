package org.gcrel.tfbackevent.auth.service;

import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.gcrel.tfbackevent.auth.service.dto.TicketQr;
import org.gcrel.tfbackevent.model.Event;
import org.gcrel.tfbackevent.model.TicketOrder;
import org.gcrel.tfbackevent.model.VirtualEvent;
import org.gcrel.tfbackevent.payment.exception.TicketDeliveryException;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {
    private static final DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    @Value("${SMTP_FROM}")
    private String fromEmail;

    private final JavaMailSender mailSender;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(String to, String code) {
        sendCode(to, "Verifica tu cuenta", "Tu código de verificación es: ", code);
    }

    @Async
    public void sendEmailChangeCode(String to, String code) {
        sendCode(to, "Confirma tu nuevo email", "Tu código para confirmar el cambio de email es: ", code);
    }

    @Async
    public void sendPasswordChangeCode(String to, String code) {
        sendCode(to, "Confirma el cambio de contraseña",
                "Tu código para confirmar el cambio de contraseña es: ", code);
    }

    private void sendCode(String to, String subject, String intro, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(intro + code + "\nEste código expira en 15 minutos.");
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("No se pudo enviar el email de verificación. to={}", to, e);
        }
    }

    public void sendTicketEmail(String to, TicketOrder order, List<TicketQr> qrs) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            Event event = order.getEvent();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Tus entradas para " + event.getTitle());

            StringBuilder html = new StringBuilder()
                    .append("<h3>¡Gracias por tu compra!</h3>");
            if (order.getSector() != null) {
                html.append("<p>Sector: <strong>")
                        .append(HtmlUtils.htmlEscape(order.getSector().getName()))
                        .append("</strong></p>");
            }
            html.append("<p>Cada entrada tiene su propio código QR. Presentá uno por persona ")
                    .append("en la puerta del evento:</p>");
            for (int i = 0; i < qrs.size(); i++) {
                html.append("<p><strong>Entrada ").append(i + 1).append(" de ").append(qrs.size())
                        .append("</strong></p>")
                        .append("<img src='cid:qr").append(i)
                        .append("' style='width: 250px; border-radius: 8px;'><br>");
            }
            helper.setText(html.toString(), true);

            for (int i = 0; i < qrs.size(); i++) {
                byte[] qrBytes = Base64.getDecoder().decode(qrs.get(i).qrBase64());
                helper.addInline("qr" + i, new ByteArrayResource(qrBytes), "image/png");
            }

            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new TicketDeliveryException(
                    "No se pudo enviar el email de entradas para la orden " + order.getId(), e);
        }
    }

    public void sendVirtualTicketEmail(String to, TicketOrder order, int ticketCount) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            VirtualEvent event = (VirtualEvent) Hibernate.unproxy(order.getEvent());
            String platform = HtmlUtils.htmlEscape(event.getPlatform());
            String link = HtmlUtils.htmlEscape(event.getAccessLink());
            String instructions = event.getAccessInstructions() == null
                    ? null
                    : HtmlUtils.htmlEscape(event.getAccessInstructions()).replace("\n", "<br>");
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Tus datos de acceso para " + event.getTitle());
            helper.setText(
                    "<h3>¡Gracias por tu compra!</h3>"
                            + "<p>Tu compra corresponde a un evento virtual"
                            + (ticketCount > 1 ? " e incluye " + ticketCount + " entradas" : "")
                            + ".</p>"
                            + "<p><strong>Fecha y hora:</strong> "
                            + event.getDateTime().format(EVENT_DATE_FORMAT) + "</p>"
                            + "<p><strong>Plataforma:</strong> " + platform + "</p>"
                            + "<p><strong>Enlace de acceso:</strong> "
                            + "<a href=\"" + link + "\">" + link + "</a></p>"
                            + (instructions == null
                                    ? ""
                                    : "<p><strong>Instrucciones:</strong><br>"
                                            + instructions + "</p>")
                            + "<p>Si el enlace cambia, vas a encontrar el vigente en Mis entradas</p>",
                    true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new TicketDeliveryException(
                    "No se pudo enviar el email de acceso para la orden " + order.getId(), e);
        }
    }
}
