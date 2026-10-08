package com.miniplm.dto;

import com.miniplm.model.Role;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String email,
        String name,
        Role role
) {}
