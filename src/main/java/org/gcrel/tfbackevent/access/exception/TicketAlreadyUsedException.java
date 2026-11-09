package org.gcrel.tfbackevent.access.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gcrel.tfbackevent.common.exception.ConflictException;

public class TicketAlreadyUsedException extends ConflictException {
    private static final DateTimeFormatter ACCESS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LocalDateTime validatedAt;
    private final String validatedBy;

    public TicketAlreadyUsedException(LocalDateTime validatedAt, String validatedBy) {
        super("Ticket fue usado: " + validatedAt.format(ACCESS_DATE_FORMAT)
                + (validatedBy == null ? "" : " por " + validatedBy));
        this.validatedAt = validatedAt;
        this.validatedBy = validatedBy;
    }

    // Para resolver disputas en la puerta: cuándo y quién validó la entrada.
    @Override
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("validatedAt", validatedAt);
        details.put("validatedBy", validatedBy);
        return details;
    }
}
