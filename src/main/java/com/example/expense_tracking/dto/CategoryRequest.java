package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "Category name is required")
    private String name;

    @NotNull(message = "Type is required (IN/OUT)")
    private TransactionType type;

    private String icon;
}
