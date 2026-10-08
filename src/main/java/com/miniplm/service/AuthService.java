package com.miniplm.service;

import com.miniplm.dto.LoginRequest;
import com.miniplm.dto.LoginResponse;
import com.miniplm.dto.UserResponse;
import com.miniplm.exception.InvalidCredentialsException;
import com.miniplm.exception.ResourceNotFoundException;
import com.miniplm.model.User;
import com.miniplm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    @Transactional
    public LoginResponse login(LoginRequest req) {
        // Same message for "no such user" and "wrong password", so nobody can probe which emails exist
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);

        String token = jwtService.generate(user);
        auditService.log("LOGIN", "User", user.getId(), user.getEmail(), "Signed in");
        return new LoginResponse(token, "Bearer", jwtService.expirySeconds(),
                user.getEmail(), user.getName(), user.getRole());
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        User user = currentUserService.user()
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
