package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionResponse {
    // Transaction id.
    private Long id;

    // Category info.
    private CategoryDTO category;

    // Money amount.
    private BigDecimal amount;

    // IN or OUT.
    private TransactionType type;

    // Optional description.
    private String description;

    // Transaction timestamp.
    private LocalDateTime transactionDate;
}
