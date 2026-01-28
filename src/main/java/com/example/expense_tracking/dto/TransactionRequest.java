package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionRequest {
    @NotNull(message = "Category name cannot be empty")
    private String category;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Type is required (IN/OUT)")
    private TransactionType type;
    private String description;
    private LocalDateTime transactionDate;

    private String accountNumber;
}
