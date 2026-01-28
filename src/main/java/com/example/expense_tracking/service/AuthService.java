package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.LoginRequest;
import com.example.expense_tracking.dto.LoginResponse;
import com.example.expense_tracking.dto.RegisterRequest;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.UserRepository;
import com.example.expense_tracking.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public User register(RegisterRequest request) {
        // 1. Check if email already exist
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // 2. Create new user
        User newUser = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                // Encrypt password before saving
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        // 3. Save to database
        return userRepository.save(newUser);
    }

    public LoginResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email not found"));

        // 2. Check if the password is correct
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Passwords do not match");
        }

        // 3. Generate token
        String token = jwtUtils.generateToken(user);

        return new LoginResponse(token, user.getFullName());
    }
}
