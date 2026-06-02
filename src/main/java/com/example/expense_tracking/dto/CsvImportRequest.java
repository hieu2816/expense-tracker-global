package com.example.expense_tracking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CsvImportRequest {
    private String fileName;

    @NotBlank(message = "CSV content is required")
    private String content;

    @Valid
    @NotNull(message = "Column mapping is required")
    private CsvColumnMapping mapping;
}
