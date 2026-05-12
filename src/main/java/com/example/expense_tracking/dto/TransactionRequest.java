package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionRequest {
    // Category name chosen by the user.
    @NotNull(message = "Category name cannot be empty")
    private String category;

    // Transaction amount.
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    // Direction of the money flow.
    @NotNull(message = "Type is required (IN/OUT)")
    private TransactionType type;

    // Optional description.
    private String description;

    // Optional transaction timestamp.
    private LocalDateTime transactionDate;

    // Bank account id for manual transactions.
    private Long bankAccountId;
}
