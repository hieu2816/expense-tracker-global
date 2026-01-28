package com.example.expense_tracking.controller;

import com.example.expense_tracking.dto.CassoWebhookDTO;
import com.example.expense_tracking.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {
    private final TransactionService transactionService;


    @PostMapping("/handler")
    public ResponseEntity<String> handleCassoWebhook(
            @RequestHeader(value = "secure-token", required = false) String token,
            @RequestBody CassoWebhookDTO payload
    ) {
        try {
            // Pass the token and data to Service logic
            transactionService.processCassoWebhooks(token, payload);

            // Always return 200 to Casso so it know this have received
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
