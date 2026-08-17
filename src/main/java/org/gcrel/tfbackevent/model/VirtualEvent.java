package org.gcrel.tfbackevent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "virtual_events")
public class VirtualEvent extends Event {
    @Column(name = "access_link")
    private String accessLink;

    @Column(nullable = false)
    private String platform;

    @Column(name = "access_instructions", columnDefinition = "TEXT")
    private String accessInstructions;

    protected VirtualEvent() {
    }

    public VirtualEvent(String title, String description, LocalDateTime dateTime, BigDecimal basePrice, String accessLink, String platform, String accessInstructions) {
        super(title, description, dateTime, basePrice);
        this.accessLink = accessLink;
        this.platform = platform;
        this.accessInstructions = accessInstructions;
    }

    public String getAccessLink() {
        return accessLink;
    }

    public String getPlatform() {
        return platform;
    }

    public String getAccessInstructions() {
        return accessInstructions;
    }
}