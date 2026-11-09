package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.repository.AuditLogRepository;
import org.gcrel.tfbackevent.admin.service.AuditService;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.model.AuditLog;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void record_SavesActorActionTargetAndTimestamp() {
        User actor = new User("Ana", "ana@example.com", "x", LocalDate.of(1990, 1, 1));
        UUID targetId = UUID.randomUUID();

        auditService.record(actor, AuditAction.USER_BLOCKED, AuditTargetType.USER, targetId, "Spam");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals(actor, log.getActor());
        assertEquals(AuditAction.USER_BLOCKED, log.getAction());
        assertEquals(AuditTargetType.USER, log.getTargetType());
        assertEquals(targetId, log.getTargetId());
        assertEquals("Spam", log.getDetails());
        assertEquals(false, log.getCreatedAt() == null);
    }

    @Test
    void record_TruncatesLongDetails() {
        User actor = new User("Ana", "ana@example.com", "x", LocalDate.of(1990, 1, 1));

        auditService.record(actor, AuditAction.USER_BLOCKED, AuditTargetType.USER, UUID.randomUUID(), "a".repeat(900));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(500, captor.getValue().getDetails().length());
    }

    @Test
    void search_WhenFromIsAfterTo_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> auditService.search(
                null, null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), PageRequest.of(0, 20)));
        verify(auditLogRepository, never()).findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    }
}
