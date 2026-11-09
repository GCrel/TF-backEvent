package org.gcrel.tfbackevent.report.repository.dto;

import java.util.UUID;

public record ControllerValidations(UUID controllerId, String name, long count) {
}
