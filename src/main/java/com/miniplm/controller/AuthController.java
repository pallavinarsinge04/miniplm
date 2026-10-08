package com.miniplm.controller;

import com.miniplm.dto.LoginRequest;
import com.miniplm.dto.LoginResponse;
import com.miniplm.dto.UserResponse;
import com.miniplm.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Who am I? Useful to check that your token works. */
    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }
}
