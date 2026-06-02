package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.ImportBatch;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {
    List<ImportBatch> findTop20ByUserOrderByCreatedAtDesc(User user);

    Optional<ImportBatch> findByIdAndUser(UUID id, User user);
}
