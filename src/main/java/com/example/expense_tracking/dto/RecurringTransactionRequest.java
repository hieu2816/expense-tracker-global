package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.RecurringFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RecurringTransactionRequest {
    @NotNull(message = "Template id is required")
    private Long templateId;

    @NotNull(message = "Frequency is required")
    private RecurringFrequency frequency;

    @NotNull(message = "Next run date is required")
    private LocalDate nextRunDate;

    private Boolean active = true;
}
