package com.example.expense_tracking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatch {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionSource source;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "imported_rows", nullable = false)
    private Integer importedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private Integer duplicateRows;

    @Column(name = "invalid_rows", nullable = false)
    private Integer invalidRows;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
