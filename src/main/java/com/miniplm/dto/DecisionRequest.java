package com.miniplm.dto;

import com.miniplm.model.ApprovalStatus;
import jakarta.validation.constraints.NotNull;

/** The reviewer is the logged-in user, so no email is sent any more. */
public record DecisionRequest(
        @NotNull(message = "decision is required (APPROVED or REJECTED)")
        ApprovalStatus decision,

        String comment
) {}
