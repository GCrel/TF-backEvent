package org.gcrel.tfbackevent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.gcrel.tfbackevent.model.enums.DeliveryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// Orden de compra: agrupa las entradas adquiridas juntas por un comprador y
// concentra el estado de entrega por email.
@Entity
@Table(name = "ticket_orders")
public class TicketOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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

    protected TicketOrder() {
    }

    public TicketOrder(User buyer, Event event, int quantity, BigDecimal unitPrice, LocalDateTime createdAt) {
        this.buyer = buyer;
        this.event = event;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.createdAt = createdAt;
        this.deliveryStatus = DeliveryStatus.NOT_REQUIRED;
        this.deliveryAttempts = 0;
    }

    // Programa el envío de las entradas lo antes posible.
    public void scheduleDelivery(LocalDateTime now) {
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.nextDeliveryAttemptAt = now;
        this.lastDeliveryError = null;
    }

    public void cancelDelivery() {
        this.deliveryStatus = DeliveryStatus.NOT_REQUIRED;
        this.nextDeliveryAttemptAt = null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        TicketOrder that = (TicketOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getBuyer() {
        return buyer;
    }

    public Event getEvent() {
        return event;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(int deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    public LocalDateTime getNextDeliveryAttemptAt() {
        return nextDeliveryAttemptAt;
    }

    public void setNextDeliveryAttemptAt(LocalDateTime nextDeliveryAttemptAt) {
        this.nextDeliveryAttemptAt = nextDeliveryAttemptAt;
    }

    public String getLastDeliveryError() {
        return lastDeliveryError;
    }

    public void setLastDeliveryError(String lastDeliveryError) {
        this.lastDeliveryError = lastDeliveryError;
    }
}
