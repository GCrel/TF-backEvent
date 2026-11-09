package org.gcrel.tfbackevent.admin.service;

import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.response.PaymentAdminResponse;
import org.gcrel.tfbackevent.admin.dto.response.TicketAdminResponse;
import org.gcrel.tfbackevent.admin.exception.AdminConflictException;
import org.gcrel.tfbackevent.admin.exception.PaymentNotFoundException;
import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.Ticket;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;
import org.gcrel.tfbackevent.model.enums.TicketStatus;
import org.gcrel.tfbackevent.payment.exception.TicketNotActiveException;
import org.gcrel.tfbackevent.payment.exception.TicketNotFoundException;
import org.gcrel.tfbackevent.payment.repository.PaymentRepository;
import org.gcrel.tfbackevent.payment.repository.TicketRepository;
import org.gcrel.tfbackevent.payment.service.DeliveryService;
import org.gcrel.tfbackevent.payment.service.PaymentRecoveryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSupportService {
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRecoveryService paymentRecoveryService;
    private final DeliveryService deliveryService;
    private final AuditService auditService;

    public AdminSupportService(
            PaymentRepository paymentRepository,
            TicketRepository ticketRepository,
            PaymentRecoveryService paymentRecoveryService,
            DeliveryService deliveryService,
            AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
        this.paymentRecoveryService = paymentRecoveryService;
        this.deliveryService = deliveryService;
        this.auditService = auditService;
    }

    // Transaccional para leer orden, evento y comprador (relaciones lazy) al armar la respuesta.
    @Transactional(readOnly = true)
    public Page<PaymentAdminResponse> searchPayments(
            UUID userId, UUID eventId, PaymentStatus status, Pageable pageable) {
        Specification<Payment> spec = Specification.unrestricted();
        if (userId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("order").get("buyer").get("id"), userId));
        }
        if (eventId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("order").get("event").get("id"), eventId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("paymentStatus"), status));
        }
        return paymentRepository.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "paymentDate", "id"))).map(PaymentAdminResponse::new);
    }

    @Transactional(readOnly = true)
    public Page<TicketAdminResponse> searchTickets(
            UUID userId, UUID eventId, TicketStatus status, Pageable pageable) {
        Specification<Ticket> spec = Specification.unrestricted();
        if (userId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("attendee").get("id"), userId));
        }
        if (eventId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("event").get("id"), eventId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        return ticketRepository.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "boughtAt", "id"))).map(TicketAdminResponse::new);
    }

    // Sin @Transactional: la consulta a MercadoPago es una llamada HTTP y no debe retener una
    // conexión de base. El pago lo actualiza su propia transacción (PaymentService).
    public PaymentAdminResponse reconcile(User actor, UUID id) {
        Payment payment = paymentRepository.findWithDetailsById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new AdminConflictException("Solo se pueden reconciliar pagos pendientes.");
        }

        paymentRecoveryService.reconcile(payment);

        Payment updated = paymentRepository.findWithDetailsById(id).orElseThrow();
        auditService.record(actor, AuditAction.PAYMENT_RECONCILED, AuditTargetType.PAYMENT, id,
                PaymentStatus.PENDING + " -> " + updated.getPaymentStatus());
        return new PaymentAdminResponse(updated);
    }

    // Respeta el cooldown y los envíos en curso de DeliveryService: no duplica emails.
    @Transactional
    public void resendDelivery(User actor, UUID ticketId) {
        Ticket ticket = ticketRepository.findWithEventAndAttendeeById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new TicketNotActiveException();
        }
        deliveryService.requestDelivery(ticket.getOrder().getId());
        auditService.record(actor, AuditAction.DELIVERY_REQUESTED, AuditTargetType.TICKET, ticketId,
                "Orden " + ticket.getOrder().getId());
    }
}
