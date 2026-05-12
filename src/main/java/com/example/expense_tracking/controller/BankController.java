package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.SyncLogResponse;
import com.example.expense_tracking.dto.bank.*;
import com.example.expense_tracking.dto.webhook.PlaidWebhookRequest;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.PlaidItemRepository;
import com.example.expense_tracking.service.BankLinkingService;
import com.example.expense_tracking.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Expose bank-linking and sync endpoints.

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
@Slf4j
@Validated
public class BankController {
    private final BankLinkingService bankLinkingService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final PlaidItemRepository PlaidItemRepository;

    // Start the Plaid Link flow.
    @PostMapping("/link")
    public ResponseEntity<LinkBankResponse> linkBank(
            @AuthenticationPrincipal User user) {
        log.info("User {} starting bank linking", user.getUsername());

        LinkBankResponse response = bankLinkingService.startLinking(user, null, null);
        return ResponseEntity.ok(response);
    }

    // Finish the Plaid Link flow after the frontend returns a public token.
    @PostMapping("/link/complete")
    public ResponseEntity<Map<String, String>> completeLinking(
            @AuthenticationPrincipal User user,
            @RequestBody PlaidExchangeRequest request) {
        bankLinkingService.completeLinking(user, request);
        return ResponseEntity.ok(Map.of("message", "Bank linked successfully"));
    }

    // List all bank accounts for the current user.
    @GetMapping
    public ResponseEntity<List<BankAccountResponse>> getUserBanks(
            @AuthenticationPrincipal User user) {
        log.debug("Fetching bank accounts for user {}", user.getEmail());

        List<BankAccountResponse> accounts = bankLinkingService.getUserBankAccounts(user);
        return ResponseEntity.ok(accounts);
    }

    // Get one bank account by id.
    @GetMapping("/{id}")
    public ResponseEntity<BankAccountResponse> getBankAccount(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        log.debug("Fetching bank account {} for user {}", id, user.getEmail());
        BankAccountResponse account = bankLinkingService.getUserBankAccount(user, id);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(account);
    }

    // Manually trigger transaction sync for one bank account.
    @PostMapping("/{id}/sync")
    public ResponseEntity<SyncResponse> syncBankAccount(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) SyncRequest request) {
        log.info("Manual sync requested for bank {} by user {}", id, user.getEmail());
        SyncResponse response = bankLinkingService.manualSync(user, id, request);
        return ResponseEntity.ok(response);
    }

    // Unlink a bank account while keeping transactions.
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> unlinkBank(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        log.info("Unlinking bank {} by user {}", id, user.getEmail());
        boolean success = bankLinkingService.unlinkBank(user, id);

        if (!success) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("message", "Bank unlinked successfully"));
    }

// Return sync history for one bank account.
    @GetMapping("/{id}/sync-history")
    public ResponseEntity<Page<SyncLogResponse>> getSyncHistory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") @Max(100) int size) {
        log.debug("Fetching sync history for bank account {} by user {}", id, user.getEmail());
        Page<SyncLogResponse> history = bankLinkingService.getSyncHistory(user, id, page, size);
        return ResponseEntity.ok(history);
    }

    // Plaid webhook endpoint - called when events occur (new transactions, errors, etc.)
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers) {
        
        log.info("Received Plaid webhook");
        
        try {
            PlaidWebhookRequest webhook = objectMapper.readValue(rawBody, PlaidWebhookRequest.class);
            webhookService.handleWebhook(webhook, headers);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("status", "error"));
        }
    }
}
