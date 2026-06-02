package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.TransactionTemplateRequest;
import com.example.expense_tracking.dto.TransactionTemplateResponse;
import com.example.expense_tracking.dto.TransactionResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.TransactionTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transaction-templates")
@RequiredArgsConstructor
public class TransactionTemplateController {
    private final TransactionTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<TransactionTemplateResponse>> getTemplates(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(templateService.getTemplates(user));
    }

    @PostMapping
    public ResponseEntity<TransactionTemplateResponse> createTemplate(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransactionTemplateRequest request) {
        return ResponseEntity.ok(templateService.createTemplate(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionTemplateResponse> updateTemplate(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody TransactionTemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        templateService.deleteTemplate(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/create-transaction")
    public ResponseEntity<TransactionResponse> createTransactionFromTemplate(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(templateService.createTransactionFromTemplate(id, user));
    }
}
