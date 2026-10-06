package com.miniplm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartRequest(
        @NotBlank(message = "Part number is required")
        @Size(max = 50, message = "Part number must be at most 50 characters")
        String partNumber,

        @NotBlank(message = "Name is required")
        String name,

        String description,

        String type
) {}
