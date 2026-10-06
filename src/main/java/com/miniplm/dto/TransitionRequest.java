package com.miniplm.dto;

import com.miniplm.model.LifecycleState;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(
        @NotNull(message = "targetState is required")
        LifecycleState targetState
) {}
