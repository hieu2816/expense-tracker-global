package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SyncLogResponse {
    // Sync log id.
    private Long id;

    // Time when sync finished.
    private LocalDateTime syncedAt;

    // Legacy start date.
    private LocalDate dateFrom;

    // Legacy end date.
    private LocalDate dateTo;

    // Number of transactions fetched.
    private Integer transactionsFetched;

    // Number of new transactions stored.
    private Integer transactionsNew;

    // Sync status.
    private String status;

    // Error text when sync fails.
    private String errorMessage;

    // Row creation time.
    private LocalDateTime createdAt;
}
