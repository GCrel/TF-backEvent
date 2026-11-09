package org.gcrel.tfbackevent.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.response.AuditLogResponse;
import org.gcrel.tfbackevent.admin.repository.AuditLogRepository;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.model.AuditLog;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final int MAX_DETAILS = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // Se une a la transacción de quien llama: la acción y su registro se confirman o se
    // revierten juntos.
    public void record(User actor, AuditAction action, AuditTargetType targetType, UUID targetId,
            String details) {
        String trimmed = details != null && details.length() > MAX_DETAILS
                ? details.substring(0, MAX_DETAILS)
                : details;
        auditLogRepository.save(new AuditLog(actor, action, targetType, targetId, trimmed, LocalDateTime.now()));
    }

    // Transaccional para leer el actor (relación lazy) al armar la respuesta.
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(
            UUID actorId, AuditAction action, UUID targetId, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("La fecha 'from' no puede ser posterior a 'to'.");
        }
        Specification<AuditLog> spec = Specification.unrestricted();
        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actor").get("id"), actorId));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
        }
        if (to != null) {
            // `to` es inclusivo: abarca todo ese día.
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findAll(spec, page).map(AuditLogResponse::new);
    }
}
