package org.gcrel.tfbackevent.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class UpdateEventRequest {
    private String title;
    private String description;
    private LocalDateTime dateTime;
    private Double basePrice;

    // Physical Event
    private String address;
    private Double latitude;
    private Double longitude;
    private Integer capacity;

    // Virtual Event
    private String accessLink;
    private String platform;
    private String accessInstructions;
}
