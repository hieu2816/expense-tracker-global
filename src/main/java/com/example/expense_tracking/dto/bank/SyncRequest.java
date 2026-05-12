package com.example.expense_tracking.dto.bank;

import lombok.Data;

import java.time.LocalDate;

// Request body for manual bank sync.
@Data
public class SyncRequest {
    // Start date for the old date-based sync.
    private LocalDate dateFrom;

    // End date for the old date-based sync.
    private LocalDate dateTo;
}
