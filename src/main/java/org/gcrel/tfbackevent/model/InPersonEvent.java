package org.gcrel.tfbackevent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.gcrel.tfbackevent.model.enums.EventType;

@Entity
@Table(name = "in_person_events")
public class InPersonEvent extends Event {
    @Column(nullable = false)
    private String address;

    // Sin scale, Hibernate crea numeric(10,0) y PostgreSQL redondea a grados enteros.
    // 7 decimales ≈ 1 cm; 10 dígitos alcanzan para ±180.0000000.
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    // Con sectores, es la suma de sus capacidades.
    @Column(nullable = false)
    private Integer capacity;

    // Foto del recinto sobre la que se dibujan los sectores.
    @Column(name = "layout_image_key")
    private String layoutImageKey;

    protected InPersonEvent() {
    }

    @Override
    public EventType getType() {
        return EventType.IN_PERSON;
    }

    public InPersonEvent(
            String title,
            String description,
            LocalDateTime dateTime,
            BigDecimal basePrice,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer capacity) {
        super(title, description, dateTime, basePrice);
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacity = capacity;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getLayoutImageKey() {
        return layoutImageKey;
    }

    public void setLayoutImageKey(String layoutImageKey) {
        this.layoutImageKey = layoutImageKey;
    }
}
