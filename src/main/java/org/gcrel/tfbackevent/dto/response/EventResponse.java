package org.gcrel.tfbackevent.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import org.gcrel.tfbackevent.model.InPersonEvent;
import org.gcrel.tfbackevent.model.VirtualEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@Data
public class EventResponse {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime dateTime;
    private String status;
    private BigDecimal basePrice;
    private String eventType;

    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer capacity;

    private String accessLink;
    private String platform;
    private String accessInstructions;

    // Physical Event Constructor
    public EventResponse(InPersonEvent event) {
        this.id = event.getId();
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.dateTime = event.getDateTime();
        this.status = event.getStatus().name();
        this.basePrice = event.getBasePrice();
        this.eventType = "IN_PERSON";

        this.address = event.getAddress();
        this.latitude = event.getLatitude();
        this.longitude = event.getLongitude();
        this.capacity = event.getCapacity();
    }

    // Virtual Event Constructor
    public EventResponse(VirtualEvent event) {
        this.id = event.getId();
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.dateTime = event.getDateTime();
        this.status = event.getStatus().name();
        this.basePrice = event.getBasePrice();
        this.eventType = "VIRTUAL";

        this.accessLink = event.getAccessLink();
        this.platform = event.getPlatform();
        this.accessInstructions = event.getAccessInstructions();
    }
}
