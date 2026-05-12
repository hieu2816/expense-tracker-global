package com.example.expense_tracking.dto.bank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Response for reading linked bank accounts.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {
    // Internal account id.
    private Long id;

    // Plaid institution id.
    private String institutionId;

    // Bank display name.
    private String institutionName;

    // Bank logo URL.
    private String institutionLogo;

    // Masked account number.
    private String maskedIban;

    // Account name shown to the user.
    private String accountName;

    // Current account status.
    private String status;

    // Last time this account synced.
    private LocalDateTime lastSyncedAt;

    // Authorization expiration time.
    private LocalDateTime accessExpiresAt;

    // When the account was created.
    private LocalDateTime createdAt;
}
