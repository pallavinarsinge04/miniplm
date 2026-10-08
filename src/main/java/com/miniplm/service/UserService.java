package com.miniplm.service;

import com.miniplm.dto.UserRequest;
import com.miniplm.dto.UserResponse;
import com.miniplm.exception.DuplicateResourceException;
import com.miniplm.model.User;
import com.miniplm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public UserResponse create(UserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with this email already exists: " + email);
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));   // BCrypt hash only
        user.setRole(req.role());
        User saved = userRepository.save(user);

        auditService.log("USER_CREATED", "User", saved.getId(), "Role " + saved.getRole() + ": " + email);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
