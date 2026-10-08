package com.miniplm.service;

import com.miniplm.dto.AuditResponse;
import com.miniplm.model.AuditLog;
import com.miniplm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    /** Log an action performed by the logged-in user. */
    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        log(action, entityType, entityId, currentUserService.email(), details);
    }

    @Transactional
    public void log(String action, String entityType, Long entityId, String performedBy, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setPerformedBy(performedBy);
        entry.setDetails(details == null ? null : (details.length() > 500 ? details.substring(0, 500) : details));
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> find(String entityType, Long entityId) {
        List<AuditLog> rows = (entityType != null && entityId != null)
                ? auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId)
                : auditLogRepository.findTop100ByOrderByIdDesc();
        return rows.stream()
                .map(a -> new AuditResponse(a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getPerformedBy(), a.getPerformedAt(), a.getDetails()))
                .toList();
    }
}
