package com.miniplm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitRequest(
        @NotEmpty(message = "At least one reviewer is required")
        List<@NotBlank(message = "reviewer email cannot be blank") String> reviewerEmails
) {}
