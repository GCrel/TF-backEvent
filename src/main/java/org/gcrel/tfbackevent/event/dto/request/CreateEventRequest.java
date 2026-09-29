package org.gcrel.tfbackevent.event.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.gcrel.tfbackevent.model.enums.EventType;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotNull @Future LocalDateTime dateTime,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) 
        BigDecimal basePrice,
        @NotNull EventType eventType, // "IN_PERSON" | "VIRTUAL"

        // Physical Event Fields
        @Size(max = 255) String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Positive @Max(100_000) Integer capacity,

        // Virtual Event Fields
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be a valid https URL") 
        String accessLink,
        @Size(max = 50) String platform,
        @Size(max = 1000) String accessInstructions
    ) {

    @JsonIgnore
    @AssertTrue(message = "address, latitude, longitude and capacity are required for in-person events")
    public boolean isInPersonDataValid() {
        return eventType != EventType.IN_PERSON
                || (address != null && !address.isBlank()
                        && latitude != null && longitude != null && capacity != null);
    }

    @JsonIgnore
    @AssertTrue(message = "accessLink and platform are required for virtual events")
    public boolean isVirtualDataValid() {
        return eventType != EventType.VIRTUAL
                || (accessLink != null && !accessLink.isBlank()
                        && platform != null && !platform.isBlank());
    }
}
