package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Check if email is existed
    boolean existsByEmail(String email);

    // Find user by email (for log in feature)
    Optional<User> findByEmail(String email);
}
