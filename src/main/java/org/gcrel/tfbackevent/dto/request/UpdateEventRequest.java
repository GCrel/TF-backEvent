package org.gcrel.tfbackevent.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@Data
public class UpdateEventRequest {
    private String title;
    private String description;
    private LocalDateTime dateTime;
    private BigDecimal basePrice;

    // Physical Event
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer capacity;

    // Virtual Event
    private String accessLink;
    private String platform;
    private String accessInstructions;
}
