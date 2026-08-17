package org.gcrel.tfbackevent.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class CreateEventRequest {
    private String title;
    private String description;
    private LocalDateTime dateTime;
    private Double basePrice;
    private String eventType; // "IN_PERSON" | "VIRTUAL"

    // Physical Event Fields
    private String address;
    private Double latitude;
    private Double longitude;
    private Integer capacity;

    // Virtual Event Fields
    private String accessLink;
    private String platform;
    private String accessInstructions;
}
