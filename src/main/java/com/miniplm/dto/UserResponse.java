package com.miniplm.dto;

import com.miniplm.model.Role;

public record UserResponse(Long id, String name, String email, Role role) {}
