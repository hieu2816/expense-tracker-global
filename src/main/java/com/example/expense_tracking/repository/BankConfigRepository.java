package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankConfigRepository extends JpaRepository<BankConfig, Long> {
    Optional<BankConfig> findByAccountNumber(String accountNumber);
}
