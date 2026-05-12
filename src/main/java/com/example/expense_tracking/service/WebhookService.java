package com.example.expense_tracking.service;

import com.example.expense_tracking.dto.webhook.PlaidWebhookRequest;
import com.example.expense_tracking.entity.PlaidItem;
import com.example.expense_tracking.repository.PlaidItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    private final PlaidItemRepository plaidItemRepository;
    private final TransactionSyncService transactionSyncService;

    public void handleWebhook(PlaidWebhookRequest webhook, Map<String, String> headers) {
        log.info("Received webhook: type={}, code={}, itemId={}", 
                webhook.getWebhookType(), webhook.getWebhookCode(), webhook.getItemId());

        String itemId = webhook.getItemId();
        
        PlaidItem item = plaidItemRepository.findByItemId(itemId).orElse(null);
        if (item == null) {
            log.error("Received webhook for unknown item_id: {}", itemId);
            return;
        }

        String webhookType = webhook.getWebhookType();
        String webhookCode = webhook.getWebhookCode();

        switch (webhookType) {
            case "TRANSACTIONS":
                handleTransactionsWebhook(webhook, item);
                break;
            case "ITEM":
                handleItemWebhook(webhook, item);
                break;
            default:
                log.warn("Unknown webhook type: {}", webhookType);
        }
    }

    private void handleTransactionsWebhook(PlaidWebhookRequest webhook, PlaidItem item) {
        String code = webhook.getWebhookCode();
        
        switch (code) {
            case "SYNC_UPDATES_AVAILABLE":
            case "DEFAULT_UPDATE":
                log.info("New transactions available for item {}, triggering sync", item.getItemId());
                triggerSync(item);
                break;
                
            case "TRANSACTIONS_REMOVED":
                log.info("Transactions removed for item {}", item.getItemId());
                triggerSync(item);
                break;
                
            case "INITIAL_UPDATE":
                log.info("Initial update complete for item {}", item.getItemId());
                triggerSync(item);
                break;
                
            case "HISTORICAL_UPDATE":
                log.info("Historical update complete for item {}", item.getItemId());
                triggerSync(item);
                break;
                
            default:
                log.warn("Unknown TRANSACTIONS webhook code: {}", code);
        }
    }

    private void handleItemWebhook(PlaidWebhookRequest webhook, PlaidItem item) {
        String code = webhook.getWebhookCode();
        
        switch (code) {
            case "ERROR":
                log.error("Item {} entered error state: {}", 
                        item.getItemId(), webhook.getErrorMessage());
                item.setStatus("ERROR");
                plaidItemRepository.save(item);
                break;
                
            case "PENDING_EXPIRATION":
                log.warn("Item {} access pending expiration", item.getItemId());
                item.setStatus("PENDING_EXPIRATION");
                plaidItemRepository.save(item);
                break;
                
            case "PENDING_DISCONNECT":
                log.warn("Item {} pending disconnect", item.getItemId());
                item.setStatus("PENDING_DISCONNECT");
                plaidItemRepository.save(item);
                break;
                
            case "USER_PERMISSION_REVOKED":
                log.warn("User revoked permission for item {}", item.getItemId());
                item.setStatus("REMOVED");
                plaidItemRepository.save(item);
                break;
                
            case "LOGIN_REPAIRED":
                log.info("Item {} login repaired", item.getItemId());
                item.setStatus("ACTIVE");
                plaidItemRepository.save(item);
                break;
                
            case "NEW_ACCOUNTS_AVAILABLE":
                log.info("New accounts available for item {}", item.getItemId());
                break;
                
            case "WEBHOOK_UPDATE_ACKNOWLEDGED":
                log.info("Webhook URL updated successfully for item {}", item.getItemId());
                break;
                
            default:
                log.warn("Unknown ITEM webhook code: {}", code);
        }
    }

    private void triggerSync(PlaidItem item) {
        try {
            transactionSyncService.syncItem(item.getItemId());
            log.info("Triggered async sync for item {}", item.getItemId());
        } catch (Exception e) {
            log.error("Failed to trigger sync for item {}: {}", 
                    item.getItemId(), e.getMessage());
        }
    }
}