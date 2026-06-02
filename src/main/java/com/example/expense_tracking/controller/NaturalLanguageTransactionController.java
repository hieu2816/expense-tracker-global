package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.NaturalLanguageConfirmRequest;
import com.example.expense_tracking.dto.NaturalLanguageDraftResponse;
import com.example.expense_tracking.dto.NaturalLanguageParseRequest;
import com.example.expense_tracking.dto.TransactionResponse;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.NaturalLanguageTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions/natural-language")
@RequiredArgsConstructor
public class NaturalLanguageTransactionController {
    private final NaturalLanguageTransactionService naturalLanguageTransactionService;

    @PostMapping("/parse")
    public ResponseEntity<NaturalLanguageDraftResponse> parse(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NaturalLanguageParseRequest request) {
        return ResponseEntity.ok(naturalLanguageTransactionService.parse(request, user));
    }

    @PostMapping("/confirm")
    public ResponseEntity<TransactionResponse> confirm(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NaturalLanguageConfirmRequest request) {
        return ResponseEntity.ok(naturalLanguageTransactionService.confirm(request, user));
    }
}
