package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.RecurringTransaction;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByUserOrderByCreatedAtDesc(User user);

    Optional<RecurringTransaction> findByIdAndUser(Long id, User user);

    List<RecurringTransaction> findByActiveTrueAndNextRunDateLessThanEqual(LocalDate date);
}
