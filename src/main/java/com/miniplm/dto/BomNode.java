package com.miniplm.dto;

import java.util.List;

/** One row of the indented BOM. The root node has level 0 and no linkId. */
public record BomNode(
        Long linkId,
        Long partId,
        String partNumber,
        String name,
        String revision,
        String state,
        Integer quantity,
        String unit,
        int level,
        List<BomNode> children
) {}
