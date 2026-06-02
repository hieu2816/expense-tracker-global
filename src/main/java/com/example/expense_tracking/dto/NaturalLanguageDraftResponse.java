package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class NaturalLanguageDraftResponse {
    private String originalText;
    private String language;
    private String description;
    private BigDecimal amount;
    private TransactionType type;
    private String category;
    private LocalDateTime transactionDate;
    private BigDecimal confidence;
    private List<String> warnings;
}
