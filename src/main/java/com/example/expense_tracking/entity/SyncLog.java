package com.example.expense_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plaid_item_id", nullable = false)
    private PlaidItem plaidItem;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "transactions_fetched")
    private Integer transactionsFetched = 0;

    @Column(name = "transactions_new")
    private Integer transactionsNew = 0;

    @Column(name = "status", nullable = false)
    private String status;  // SUCCESS, FAILED, PARTIAL

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) {
            syncedAt = LocalDateTime.now();
        }
    }
}
