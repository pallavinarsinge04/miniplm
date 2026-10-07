package com.miniplm.dto;

/** A parent assembly (and its revision) that uses the part. */
public record WhereUsedResponse(
        Long partId,
        String partNumber,
        String name,
        String revision,
        String state,
        Integer quantity,
        String unit
) {}
