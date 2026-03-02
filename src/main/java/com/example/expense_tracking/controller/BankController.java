package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.bank.*;
import com.example.expense_tracking.dto.gocardless.GCInstitution;
import com.example.expense_tracking.entity.SyncLog;
import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.service.BankLinkingService;
import org.springframework.data.domain.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// REST controller for bank account operations

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
@Slf4j
public class BankController {
    private final BankLinkingService bankLinkingService;

    // Lust available banks/institutions for linking
    // Get /api/banks/institutions?country=GB
    @GetMapping("/institutions")
    public ResponseEntity<List<GCInstitution>> getInstitutions(
            @RequestParam(defaultValue = "GB") String country) {
        log.info("Fetching institutions for country: {}", country);
        List<GCInstitution> institutions = bankLinkingService.getInstitutions(country);
        return ResponseEntity.ok(institutions);
    }

    // Start the bank linking process
    // POST /api/banks/link
    @PostMapping("/link")
    public ResponseEntity<LinkBankResponse> linkBank(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody LinkBankRequest linkBankRequest) {
        log.info("User {} starting bank linking for institution: {}",
                user.getUsername(), linkBankRequest.getInstitutionId());

        LinkBankResponse response = bankLinkingService.startLinking(user, linkBankRequest.getInstitutionId(), linkBankRequest.getCountryCode());
        return ResponseEntity.ok(response);
    }

    // Handle callback from GoCardless after user authorizes
    // GET /api/banks/callback?ref=user_123_1234567890
    // This endpoint is called bu GoCardless redirect after the user completes (or cancels) the bank linking process
    @GetMapping("/callback")
    public ResponseEntity<CallbackResponse> handleCallback(@RequestParam String ref) {
        log.info("Received callback with ref {}", ref);

        CallbackResponse response = bankLinkingService.processCallback(ref);
        if ("FAILED".equals(response.getStatus())) {
            // Return 200 even on failure so frontend can show error message
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(response);
    }

    // List all bank account for the current user
    // GET /api/banks
    @GetMapping
    public ResponseEntity<List<BankAccountResponse>> getUserBanks(
            @AuthenticationPrincipal User user) {
        log.debug("Fetching bank accounts for user {}", user.getEmail());

        List<BankAccountResponse> accounts = bankLinkingService.getUserBankAccounts(user);
        return ResponseEntity.ok(accounts);
    }

    // Get details of a specific bank account
    // GET /api/banks/{id}
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

    // Manually trigger transactions sync for a bank account
    // POST /api/banks/{id}/sync
    @PostMapping("/{id}/sync")
    public ResponseEntity<SyncResponse> syncBankAccount(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) SyncRequest request) {
        log.info("Manual sync requested for bank {} by user {}", id, user.getEmail());
        SyncResponse response = bankLinkingService.manualSync(user, id, request);
        return ResponseEntity.ok(response);
    }

    // Unlink a bank account (remove the bank connection but keeps all transactions)
    // DELETE /api/banks/{id}
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

    // Get sync history for a specific bank account
    // GET /api/banks/{id}/sync-history?page=0&size=10
    @GetMapping("/{id}/sync-history")
    public ResponseEntity<Page<SyncLog>> getSyncHistory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("Fetching sync history for bank account {} by user {}", id, user.getEmail());
        Page<SyncLog> history = bankLinkingService.getSyncHistory(user, id, page, size);
        return ResponseEntity.ok(history);
    }
}
