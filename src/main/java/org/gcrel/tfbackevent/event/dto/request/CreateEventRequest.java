package org.gcrel.tfbackevent.event.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CreateEventRequest {
    private String title;
    private String description;
    private LocalDateTime dateTime;
    private BigDecimal basePrice;
    private String eventType; // "IN_PERSON" | "VIRTUAL"

    // Physical Event Fields
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer capacity;

    // Virtual Event Fields
    private String accessLink;
    private String platform;
    private String accessInstructions;
}
