package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.ChangePasswordRequest;
import com.example.expense_tracking.dto.UpdateProfileRequest;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Update Basic Info (name)
    public User updateProfile(User user, UpdateProfileRequest request) {
        user.setFullName(request.getFullName());
        return userRepository.save(user);
    }

    public void changePassword(User user, ChangePasswordRequest request) {
        // Verify if the OLD password is correct
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }

        // Hash the new password
        String newHash = passwordEncoder.encode(request.getNewPassword());
        user.setPasswordHash(newHash);

        userRepository.save(user);
    }
}
