package org.gcrel.tfbackevent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_status_date", columnList = "payment_status, payment_date"))
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

    @Column(name = "next_reconcile_at")
    private LocalDateTime nextReconcileAt;

    @Column(nullable = false)
    @Version
    private long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private TicketOrder order;

    protected Payment() {
    }

    public Payment(
            String transactionId,
            BigDecimal totalAmount,
            LocalDateTime paymentDate,
            TicketOrder order) {
        this.transactionId = transactionId;
        this.totalAmount = totalAmount;
        this.paymentDate = paymentDate;
        this.paymentStatus = PaymentStatus.PENDING;
        this.order = order;
        this.nextReconcileAt = paymentDate;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public TicketOrder getOrder() {
        return order;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setOrder(TicketOrder order) {
        this.order = order;
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

    public LocalDateTime getNextReconcileAt() {
        return nextReconcileAt;
    }

    public void setMercadoPagoStatus(String mercadoPagoStatus) {
        this.mercadoPagoStatus = mercadoPagoStatus;
    }

    public void setStatusDetail(String statusDetail) {
        this.statusDetail = statusDetail;
    }

    public void setNextReconcileAt(LocalDateTime nextReconcileAt) {
        this.nextReconcileAt = nextReconcileAt;
    }
}
