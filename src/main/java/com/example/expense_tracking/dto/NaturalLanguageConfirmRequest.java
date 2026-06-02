package com.example.expense_tracking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NaturalLanguageConfirmRequest {
    @NotBlank(message = "Original text is required")
    private String originalText;

    private String language;

    @NotNull
    @Valid
    private TransactionRequest transaction;

    private java.math.BigDecimal confidence;
}
