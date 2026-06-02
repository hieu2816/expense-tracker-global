package com.example.expense_tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CsvImportPreviewResponse {
    private List<String> headers;
    private List<CsvImportRowPreview> rows;
    private Integer totalRows;
    private Integer validRows;
    private Integer duplicateRows;
    private Integer invalidRows;
}
