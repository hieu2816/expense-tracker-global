package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.CategorySummaryResponse;
import com.example.expense_tracking.dto.DashBoardResponse;
import com.example.expense_tracking.dto.TransactionRequest;
import com.example.expense_tracking.dto.TransactionResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {
    private final TransactionService transactionService;

    // Create a manual transaction.
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.createTransaction(request, user));
    }

    // List transactions with filters and pagination.
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) {
        return ResponseEntity.ok(transactionService.getAllTransactions(user, page, size, category, startDate, endDate));
    }

    // Update one transaction.
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, request, user));
    }

    // Delete one transaction.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        transactionService.deleteTransaction(id, user);
        return ResponseEntity.noContent().build();
    }

    // Return dashboard summary stats.
    @GetMapping("/dashboard")
    public ResponseEntity<DashBoardResponse> getDashBoardStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(transactionService.getDashBoardStats(user));
    }

    // Return category totals for the current user.
    @GetMapping("/category-summary")
    public ResponseEntity<List<CategorySummaryResponse>> getCategorySummary(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(transactionService.getCategorySummary(user));
    }

    // Export filtered transactions as CSV.
    @GetMapping("/export")
    public void exportToCsv(
            @AuthenticationPrincipal User user,
            HttpServletResponse response,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate) throws IOException {
        response.setContentType("text/csv");
        response.addHeader("Content-Disposition", "attachment; filename=transactions.csv");
        transactionService.exportToCsv(user, category, startDate, endDate, response.getWriter());
    }
}
