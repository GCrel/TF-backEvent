package org.gcrel.tfbackevent.model;

import jakarta.persistence.*;
import org.gcrel.tfbackevent.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    protected Payment() {
    }

    public Payment(String transactionId, BigDecimal totalAmount, LocalDateTime paymentDate, Ticket ticket) {
        this.transactionId = transactionId;
        this.totalAmount = totalAmount;
        this.paymentDate = paymentDate;
        this.paymentStatus = PaymentStatus.PENDING;
        this.ticket = ticket;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
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

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}