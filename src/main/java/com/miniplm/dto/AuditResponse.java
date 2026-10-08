package com.miniplm.dto;

import java.time.LocalDateTime;

public record AuditResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        String performedBy,
        LocalDateTime performedAt,
        String details
) {}
