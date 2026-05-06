package com.example.expense_tracking.dto.bank;

import lombok.Data;

import java.time.LocalDate;

// Request body for POST /api/banks/{id}/sync (Use when user wants to sync transactions from linked bank accounts)
@Data
public class SyncRequest {
    // Start date for syncing transactions (inclusive)
    private LocalDate dateFrom;

    // End date for syncing transactions (inclusive)
    private LocalDate dateTo;
}
