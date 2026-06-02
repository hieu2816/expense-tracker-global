package com.example.expense_tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recurring_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private TransactionTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
