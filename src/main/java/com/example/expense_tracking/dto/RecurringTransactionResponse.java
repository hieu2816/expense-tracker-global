package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.RecurringFrequency;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class RecurringTransactionResponse {
    private Long id;
    private TransactionTemplateResponse template;
    private RecurringFrequency frequency;
    private LocalDate nextRunDate;
    private LocalDate lastRunDate;
    private Boolean active;
}
