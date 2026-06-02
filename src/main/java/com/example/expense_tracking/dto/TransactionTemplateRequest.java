package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionTemplateRequest {
    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Type is required")
    private TransactionType type;

    @NotBlank(message = "Category is required")
    private String category;

    private String currency = "GBP";

    private Boolean active = true;
}
