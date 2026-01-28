package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    private String name;
    private Long id;
    private TransactionType type;
}
