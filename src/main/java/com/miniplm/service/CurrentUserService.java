package com.miniplm.service;

import com.miniplm.model.User;
import com.miniplm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Who is making the current request (taken from the verified JWT). */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public String email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        return auth.getName();   // the token's subject = the user's email
    }

    public Optional<User> user() {
        String email = email();
        return "anonymous".equals(email) ? Optional.empty() : userRepository.findByEmail(email);
    }
}
