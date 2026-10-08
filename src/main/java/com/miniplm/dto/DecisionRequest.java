package com.miniplm.dto;

import com.miniplm.model.ApprovalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DecisionRequest(
        @NotBlank(message = "reviewerEmail is required")
        String reviewerEmail,

        @NotNull(message = "decision is required (APPROVED or REJECTED)")
        ApprovalStatus decision,

        String comment
) {}
