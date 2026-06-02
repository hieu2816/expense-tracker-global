package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.RecurringTransactionRequest;
import com.example.expense_tracking.dto.RecurringTransactionResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.RecurringTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurring-transactions")
@RequiredArgsConstructor
public class RecurringTransactionController {
    private final RecurringTransactionService recurringService;

    @GetMapping
    public ResponseEntity<List<RecurringTransactionResponse>> getRecurring(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(recurringService.getRecurring(user));
    }

    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> createRecurring(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RecurringTransactionRequest request) {
        return ResponseEntity.ok(recurringService.createRecurring(request, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> updateRecurring(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody RecurringTransactionRequest request) {
        return ResponseEntity.ok(recurringService.updateRecurring(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecurring(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        recurringService.deleteRecurring(id, user);
        return ResponseEntity.noContent().build();
    }
}
