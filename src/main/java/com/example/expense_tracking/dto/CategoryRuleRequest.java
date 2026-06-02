package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.CategoryRuleMatchType;
import com.example.expense_tracking.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryRuleRequest {
    @NotBlank(message = "Keyword is required")
    private String keyword;

    @NotNull(message = "Match type is required")
    private CategoryRuleMatchType matchType;

    @NotBlank(message = "Category is required")
    private String category;

    private TransactionType transactionType;

    private Integer priority = 100;

    private Boolean active = true;
}
