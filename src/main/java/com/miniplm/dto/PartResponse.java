package com.miniplm.dto;

public record PartResponse(
        Long id,
        String partNumber,
        String name,
        String description,
        String type,
        String latestRevision,
        String latestState
) {}
