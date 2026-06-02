package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TransactionTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal amount;
    private TransactionType type;
    private CategoryDTO category;
    private String currency;
    private Boolean active;
}
