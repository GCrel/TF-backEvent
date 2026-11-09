package org.gcrel.tfbackevent.report.dto.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

public record EventAttendanceResponse(
        UUID eventId,
        List<HourlyAccess> byHour,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ControllerValidations> byController) {

    public record HourlyAccess(int hour, long accesses) {
    }

    public record ControllerValidations(UUID controllerId, String name, long validations) {
    }
}
