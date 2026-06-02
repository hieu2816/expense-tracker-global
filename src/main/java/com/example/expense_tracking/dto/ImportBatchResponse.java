package com.example.expense_tracking.dto;

import com.example.expense_tracking.entity.ImportStatus;
import com.example.expense_tracking.entity.TransactionSource;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ImportBatchResponse {
    private UUID id;
    private TransactionSource source;
    private String fileName;
    private ImportStatus status;
    private Integer totalRows;
    private Integer importedRows;
    private Integer duplicateRows;
    private Integer invalidRows;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
