package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CsvImportResultResponse {
    private UUID importBatchId;
    private Integer totalRows;
    private Integer importedRows;
    private Integer duplicateRows;
    private Integer invalidRows;
}
