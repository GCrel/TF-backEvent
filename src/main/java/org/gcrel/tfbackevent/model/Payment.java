package org.gcrel.tfbackevent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.DeliveryStatus;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "mp_status")
    private String mercadoPagoStatus;

    @Column(name = "status_detail")
    private String statusDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "next_delivery_attempt_at")
    private LocalDateTime nextDeliveryAttemptAt;

    @Column(name = "last_delivery_error")
    private String lastDeliveryError;

    @Column(nullable = false)
    @Version
    private long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    protected Payment() {
    }

    public Payment(
            String transactionId,
            BigDecimal totalAmount,
            LocalDateTime paymentDate,
            Ticket ticket) {
        this.transactionId = transactionId;
        this.totalAmount = totalAmount;
        this.paymentDate = paymentDate;
        this.paymentStatus = PaymentStatus.PENDING;
        this.deliveryStatus = DeliveryStatus.NOT_REQUIRED;
        this.deliveryAttempts = 0;
        this.ticket = ticket;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        Payment payment = (Payment) o;
        return Objects.equals(transactionId, payment.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(transactionId);
    }

    public UUID getId() {
        return id;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void setPaymentDate(LocalDateTime date) {
        this.paymentDate = date;
    }

    public String getMercadoPagoStatus() {
        return mercadoPagoStatus;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public LocalDateTime getNextDeliveryAttemptAt() {
        return nextDeliveryAttemptAt;
    }

    public String getLastDeliveryError() {
        return lastDeliveryError;
    }

    public void setMercadoPagoStatus(String mercadoPagoStatus) {
        this.mercadoPagoStatus = mercadoPagoStatus;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public void setDeliveryAttempts(int deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    public void setNextDeliveryAttemptAt(LocalDateTime nextDeliveryAttemptAt) {
        this.nextDeliveryAttemptAt = nextDeliveryAttemptAt;
    }

    public void setLastDeliveryError(String lastDeliveryError) {
        this.lastDeliveryError = lastDeliveryError;
    }
}
