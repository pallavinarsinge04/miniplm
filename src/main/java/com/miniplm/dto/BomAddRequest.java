package com.miniplm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BomAddRequest(
        @NotBlank(message = "childPartNumber is required")
        String childPartNumber,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        String unit
) {}
