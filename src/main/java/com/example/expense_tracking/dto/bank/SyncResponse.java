package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// Response for manual bank sync.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {
    // Sync status.
    private String status;

    // Start date used for the legacy sync path.
    private LocalDate dateFrom;

    // End date used for the legacy sync path.
    private LocalDate dateTo;

    // Total transactions fetched.
    private int transactionsFetched;

    // New transactions saved.
    private int transactionsNew;

    // Error text when sync fails.
    private String errorMessage;
}
