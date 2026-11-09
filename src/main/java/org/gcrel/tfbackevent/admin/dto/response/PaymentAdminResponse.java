package org.gcrel.tfbackevent.admin.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.Payment;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;

public record PaymentAdminResponse(
        UUID id,
        String transactionId,
        PaymentStatus status,
        String mercadoPagoStatus,
        String statusDetail,
        BigDecimal totalAmount,
        LocalDateTime paymentDate,
        LocalDateTime nextReconcileAt,
        UUID orderId,
        UUID eventId,
        String eventTitle,
        UUID buyerId,
        String buyerName) {
    public PaymentAdminResponse(Payment payment) {
        this(payment.getId(), payment.getTransactionId(), payment.getPaymentStatus(),
                payment.getMercadoPagoStatus(), payment.getStatusDetail(), payment.getTotalAmount(),
                payment.getPaymentDate(), payment.getNextReconcileAt(), payment.getOrder().getId(),
                payment.getOrder().getEvent().getId(), payment.getOrder().getEvent().getTitle(),
                payment.getOrder().getBuyer().getId(), payment.getOrder().getBuyer().getName());
    }
}
