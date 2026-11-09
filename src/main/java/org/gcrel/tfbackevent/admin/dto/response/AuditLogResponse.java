package org.gcrel.tfbackevent.admin.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.model.AuditLog;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String actorName,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        String details,
        LocalDateTime createdAt) {
    public AuditLogResponse(AuditLog log) {
        this(log.getId(), log.getActor().getId(), log.getActor().getName(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getDetails(), log.getCreatedAt());
    }
}
