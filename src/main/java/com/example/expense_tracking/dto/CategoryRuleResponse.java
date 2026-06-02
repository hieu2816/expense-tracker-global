package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.CategoryRuleMatchType;
import com.example.expense_tracking.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryRuleResponse {
    private Long id;
    private String keyword;
    private CategoryRuleMatchType matchType;
    private CategoryDTO category;
    private TransactionType transactionType;
    private Integer priority;
    private Boolean active;
}
