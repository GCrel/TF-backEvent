package org.gcrel.tfbackevent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "in_person_events")
public class InPersonEvent extends Event {
    @Column(nullable = false)
    private String address;

    @Column(precision = 10, scale = 7)
    private Double latitude;

    @Column(precision = 10, scale = 7)
    private Double longitude;

    @Column(nullable = false)
    private Integer capacity;

    protected InPersonEvent() {}

    public InPersonEvent(String title, String description, LocalDateTime dateTime, Double basePrice, String address, Double latitude, Double longitude, Integer capacity) {
        super(title, description, dateTime, basePrice);
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacity = capacity;
    }
}