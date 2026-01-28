package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {
}
