package com.example.expense_tracking.repository;

import com.example.expense_tracking.entity.BankConfig;
import com.example.expense_tracking.entity.Transaction;
import com.example.expense_tracking.entity.TransactionType;
import com.example.expense_tracking.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction,Long> {
    Page<Transaction> findAllByUser(User user, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.user = :user " +
    "AND (:category IS NULL OR t.category.name = :category)" +
    "AND t.transactionDate >= COALESCE(:startDate, t.transactionDate)" +
    "AND t.transactionDate <= COALESCE(:endDate, t.transactionDate)")
    Page<Transaction> findFilteredTransactions(
            @Param("user") User user,
            @Param("category") String category,
            @Param("startDate")LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t WHERE t.user = :user AND t.type = :type")
    BigDecimal calculateTotal(@Param("user") User user, @Param("type") TransactionType type);

    boolean existsByBankTransactionIdAndBankConfig(String bankTransactionId, BankConfig bankConfig);
}
