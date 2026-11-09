package org.gcrel.tfbackevent.report.repository.dto;

import org.gcrel.tfbackevent.model.enums.EventStatus;

public record EventStatusCount(EventStatus status, long count) {
}
