package com.miniplm.dto;

import java.time.LocalDateTime;

public record VersionResponse(
        Long id,
        String revision,
        String state,
        LocalDateTime createdAt
) {}
