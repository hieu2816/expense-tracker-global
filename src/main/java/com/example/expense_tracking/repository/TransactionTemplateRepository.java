package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.TransactionTemplate;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionTemplateRepository extends JpaRepository<TransactionTemplate, Long> {
    List<TransactionTemplate> findByUserOrderByCreatedAtDesc(User user);

    List<TransactionTemplate> findByUserAndActiveTrueOrderByCreatedAtDesc(User user);

    Optional<TransactionTemplate> findByIdAndUser(Long id, User user);
}
