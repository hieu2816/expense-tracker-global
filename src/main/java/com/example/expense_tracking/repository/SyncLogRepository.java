package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
    Page<SyncLog> findByPlaidItemOrderBySyncedAtDesc(PlaidItem plaidItem, Pageable pageable);

    Optional<SyncLog> findTopByPlaidItemOrderBySyncedAtDesc(PlaidItem plaidItem);
}
