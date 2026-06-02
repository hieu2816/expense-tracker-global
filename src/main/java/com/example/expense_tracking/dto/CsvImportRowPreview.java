package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CsvImportRowPreview {
    private Integer rowNumber;
    private LocalDateTime transactionDate;
    private String description;
    private BigDecimal amount;
    private TransactionType type;
    private String category;
    private String currency;
    private Boolean valid;
    private Boolean duplicate;
    private List<String> errors;
}
