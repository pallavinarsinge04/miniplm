package com.miniplm.dto;

import java.time.LocalDateTime;

public record TaskResponse(
        Long taskId,
        Long partId,
        String partNumber,
        String partName,
        Long versionId,
        String revision,
        String approverEmail,
        String status,
        String comment,
        LocalDateTime decidedAt
) {}
