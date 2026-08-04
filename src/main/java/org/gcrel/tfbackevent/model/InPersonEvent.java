package org.gcrel.tfbackevent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "in_person_events")
public class InPersonEvent extends Event {
    @Column(nullable = false)
    private String address;

    @Column(precision = 10)
    private BigDecimal latitude;

    @Column(precision = 10)
    private BigDecimal longitude;

    @Column(nullable = false)
    private Integer capacity;

    protected InPersonEvent() {}

    public InPersonEvent(String title, String description, LocalDateTime dateTime, BigDecimal basePrice, String address, BigDecimal latitude, BigDecimal longitude, Integer capacity) {
        super(title, description, dateTime, basePrice);
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacity = capacity;
    }
}