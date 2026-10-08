package org.gcrel.tfbackevent.access.dto.response;

import java.time.LocalDateTime;

public record VirtualAccessResponse(
        String eventTitle,
        LocalDateTime dateTime,
        String platform,
        String accessLink,
        String accessInstructions) {
}
