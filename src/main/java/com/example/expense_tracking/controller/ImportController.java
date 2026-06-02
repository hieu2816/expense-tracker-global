package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.CsvImportPreviewResponse;
import com.example.expense_tracking.dto.CsvImportRequest;
import com.example.expense_tracking.dto.CsvImportResultResponse;
import com.example.expense_tracking.dto.ImportBatchResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.exception.ResourceNotFoundException;
import com.example.expense_tracking.service.CsvImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {
    private final CsvImportService csvImportService;

    @PostMapping("/csv/preview")
    public ResponseEntity<CsvImportPreviewResponse> previewCsv(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CsvImportRequest request) throws IOException {
        return ResponseEntity.ok(csvImportService.preview(request, user));
    }

    @PostMapping("/csv/commit")
    public ResponseEntity<CsvImportResultResponse> commitCsv(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CsvImportRequest request) throws IOException {
        return ResponseEntity.ok(csvImportService.commit(request, user));
    }

    @GetMapping
    public ResponseEntity<List<ImportBatchResponse>> getImports(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(csvImportService.getImportHistory(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportBatchResponse> getImport(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return ResponseEntity.ok(csvImportService.getImportBatch(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found")));
    }
}
