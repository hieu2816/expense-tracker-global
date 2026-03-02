package com.example.expense_tracking.dto.bank;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Request body for POST /api/banks/link (Use when user wants to link a new bank account)
@Data
public class LinkBankRequest {
    @NotBlank(message = "Institution ID is required")
    private String institutionId;

    // Country code for institution lookup (default: GB)
    private String countryCode;
}
