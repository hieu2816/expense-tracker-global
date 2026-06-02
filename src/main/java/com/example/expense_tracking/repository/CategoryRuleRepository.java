package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.CategoryRule;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {
    List<CategoryRule> findByUserOrderByPriorityAscCreatedAtAsc(User user);

    List<CategoryRule> findByUserAndActiveTrueOrderByPriorityAscCreatedAtAsc(User user);

    Optional<CategoryRule> findByIdAndUser(Long id, User user);
}
