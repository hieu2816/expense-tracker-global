package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionAttachment;
import com.example.expense_tracking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionAttachmentRepository extends JpaRepository<TransactionAttachment, Long> {
    List<TransactionAttachment> findByTransactionAndUserOrderByCreatedAtDesc(Transaction transaction, User user);

    Optional<TransactionAttachment> findByIdAndUser(Long id, User user);
}
