# Plaid Webhook Implementation Documentation

## Overview

This document details the complete implementation of the Plaid webhook feature for real-time transaction synchronization in the Expense Tracking Global application.

---

## 1. What We Did

### 1.1 Project Context

We previously migrated from GoCardless to Plaid for bank account linking. The initial Plaid integration only supported manual transaction sync - users had to click "Sync" to get new transactions.

### 1.2 Goal

Implement Plaid webhooks to enable automatic transaction sync when new transactions are available at the bank.

---

## 2. Implementation Plan

We created a detailed implementation plan with the following key requirements:

### 2.1 Critical Architectural Decisions

1. **Fix @Async Self-Invocation Issue**
   - Problem: Calling @Async method from within same class bypasses Spring's AOP proxy
   - Solution: Move @Async to TransactionSyncService.syncItem(), call it from WebhookService

2. **Enforce Idempotency Inside Sync Loop**
   - Problem: Global try-catch would rollback entire batch on duplicate
   - Solution: Try-catch inside the for loop, catch DataIntegrityViolationException

3. **Remove Dashboard Configuration for Webhooks**
   - Webhook URL must be set via /link/token/create, not Plaid Dashboard
   - Transaction webhooks are item-level, not app-level

4. **Simplify User Notifications**
   - Use existing PlaidItem status field instead of separate notification system
   - Frontend reads status and prompts re-authentication when needed

---

## 3. Implementation Steps

### Phase 1: Basic Setup

#### Step 1: Add Webhook URL to Link Token Request

**File: PlaidService.java**

```java
// Get webhook URL from config
String webhookUrl = plaidConfig.getWebhookUrl();

LinkTokenCreateRequest request = new LinkTokenCreateRequest()
        .user(user)
        .clientName("Expense Tracking Global")
        .products(List.of(Products.AUTH, Products.TRANSACTIONS))
        .countryCodes(List.of(CountryCode.GB))
        .language("en")
        .webhook(webhookUrl);  // <-- Add webhook URL
```

#### Step 2: Add Webhook Configuration

**File: PlaidConfig.java**
```java
@Value("${plaid.webhook-url:}")
private String webhookUrl;

// Getter added via Lombok @Data
```

**File: application.yaml**
```yaml
plaid:
  client-id: ${PLAID_CLIENT_ID}
  secret: ${PLAID_SECRET}
  environment: ${PLAID_ENV:sandbox}
  webhook-url: ${PLAID_WEBHOOK_URL:}  # Added
```

#### Step 3: Create Webhook DTO

**File: dto/webhook/PlaidWebhookRequest.java**
```java
@Data
public class PlaidWebhookRequest {
    @JsonProperty("webhook_type")
    private String webhookType;

    @JsonProperty("webhook_code")
    private String webhookCode;

    @JsonProperty("item_id")
    private String itemId;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;
    
    // ... other fields
}
```

#### Step 4: Create Webhook Endpoint

**File: BankController.java**
```java
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
```

#### Step 5: Create WebhookService

**File: WebhookService.java**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    private final PlaidItemRepository plaidItemRepository;
    private final TransactionSyncService transactionSyncService;

    public void handleWebhook(PlaidWebhookRequest webhook, Map<String, String> headers) {
        String itemId = webhook.getItemId();
        PlaidItem item = plaidItemRepository.findByItemId(itemId).orElse(null);
        
        switch (webhook.getWebhookType()) {
            case "TRANSACTIONS":
                handleTransactionsWebhook(webhook, item);
                break;
            case "ITEM":
                handleItemWebhook(webhook, item);
                break;
        }
    }

    private void handleTransactionsWebhook(PlaidWebhookRequest webhook, PlaidItem item) {
        switch (webhook.getWebhookCode()) {
            case "SYNC_UPDATES_AVAILABLE":
            case "DEFAULT_UPDATE":
                transactionSyncService.syncItem(item.getItemId());
                break;
            // ... other cases
        }
    }

    private void handleItemWebhook(PlaidWebhookRequest webhook, PlaidItem item) {
        switch (webhook.getWebhookCode()) {
            case "ERROR":
                item.setStatus("ERROR");
                plaidItemRepository.save(item);
                break;
            case "PENDING_EXPIRATION":
                item.setStatus("PENDING_EXPIRATION");
                plaidItemRepository.save(item);
                break;
            // ... other cases
        }
    }
}
```

#### Step 6: Add @Async to TransactionSyncService

**File: TransactionSyncService.java**
```java
@Async
@Transactional
public CompletableFuture<SyncResult> syncItem(String itemId) {
    // Query PlaidItem inside method to attach to current session
    PlaidItem item = plaidItemRepository.findByItemId(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Plaid item not found: " + itemId));
    
    // ... sync logic
    return CompletableFuture.completedFuture(new SyncResult(totalAdded, totalModified, totalRemoved));
}
```

#### Step 7: Enable @Async

**File: ExpenseTrackingApplication.java**
```java
@SpringBootApplication
@EnableScheduling
@EnableAsync  // <-- Added
public class ExpenseTrackingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackingApplication.class, args);
    }
}
```

---

### Phase 2: Bug Fixes

#### Issue 1: @Async Return Type

**Problem:** @Async only supports void or Future/CompletableFuture return types, not plain objects.

**Error:** `Invalid return type for async method (only Future and void supported): class SyncResult`

**Fix:**
```java
// Before
public SyncResult syncItem(PlaidItem item)

// After
public CompletableFuture<SyncResult> syncItem(String itemId)
```

#### Issue 2: Detached Entity Issue

**Problem:** Passing PlaidItem entity to @Async method caused LazyInitializationException or detached entity state.

**Fix:**
- Changed method signature from `syncItem(PlaidItem item)` to `syncItem(String itemId)`
- Query PlaidItem inside the method to attach to current session

#### Issue 3: Performance Bottleneck

**Problem:** Using `transactionRepository.findAll().stream().filter()` loaded entire transactions table into RAM - O(N) memory load.

**Fix:** Use direct database queries:
```java
// Before (O(N) memory load)
transactionRepository.findAll().stream()
    .filter(tx -> removed.getTransactionId().equals(tx.getPlaidTransactionId()) ...)

// After (O(1) index lookup)
// Direct DB query replaces O(N) memory load with O(1) index lookup for performance.
Transaction tx = transactionRepository.findByPlaidTransactionIdAndBankAccount(
    removed.getTransactionId(), account);
```

---

## 4. Files Created/Modified

| File | Action |
|------|--------|
| `PlaidService.java` | Modified - add webhook URL to Link token |
| `PlaidConfig.java` | Modified - add webhookUrl field |
| `application.yaml` | Modified - add PLAID_WEBHOOK_URL config |
| `dto/webhook/PlaidWebhookRequest.java` | Created - webhook payload DTO |
| `BankController.java` | Modified - add /webhook endpoint |
| `WebhookService.java` | Created - event routing |
| `TransactionSyncService.java` | Modified - refactor for async + performance |
| `TransactionRepository.java` | Modified - add findByPlaidTransactionId methods |
| `ExpenseTrackingApplication.java` | Modified - add @EnableAsync |
| `SecurityConfig.java` | Already permits /webhook endpoint |

---

## 5. Testing

### 5.1 Local Testing with ngrok

1. Start ngrok: `ngrok http 8080`
2. Update .env with webhook URL:
   ```
   PLAID_WEBHOOK_URL=https://your-ngrok-url.io/api/banks/webhook
   ```
3. Restart backend
4. Link a bank account (webhook URL is set automatically via Link token)

### 5.2 Test via Postman

**Step 1: Get access token**
```bash
# Create public token
POST https://sandbox.plaid.com/sandbox/public_token/create
{
  "client_id": "YOUR_CLIENT_ID",
  "secret": "YOUR_SECRET",
  "institution_id": "ins_1",
  "products": ["auth", "transactions"]
}

# Exchange for access token
POST https://sandbox.plaid.com/item/public_token/exchange
{
  "client_id": "YOUR_CLIENT_ID",
  "secret": "YOUR_SECRET",
  "public_token": "public-sandbox-xxx"
}
```

**Step 2: Fire test webhook**
```bash
POST https://sandbox.plaid.com/sandbox/item/fire_webhook
{
  "client_id": "YOUR_CLIENT_ID",
  "secret": "YOUR_SECRET",
  "access_token": "access-sandbox-xxx",
  "webhook_type": "TRANSACTIONS",
  "webhook_code": "SYNC_UPDATES_AVAILABLE"
}
```

**Expected backend logs:**
```
INFO - Received Plaid webhook
INFO - New transactions available for item xxx, triggering sync
INFO - Triggered async sync for item xxx
INFO - Transaction sync completed for item xxx
```

---

## 6. Webhook Events Handled

### TRANSACTIONS Webhooks
| Code | Action |
|------|--------|
| SYNC_UPDATES_AVAILABLE | Trigger async sync |
| DEFAULT_UPDATE | Trigger async sync |
| TRANSACTIONS_REMOVED | Remove transactions + sync |
| INITIAL_UPDATE | First sync complete |
| HISTORICAL_UPDATE | Full history loaded |

### ITEM Webhooks
| Code | Action |
|------|--------|
| ERROR | Set status to ERROR |
| PENDING_EXPIRATION | Set status to PENDING_EXPIRATION |
| PENDING_DISCONNECT | Set status to PENDING_DISCONNECT |
| USER_PERMISSION_REVOKED | Set status to REMOVED |
| LOGIN_REPAIRED | Set status to ACTIVE |

---

## 7. Production Deployment

### Webhook URL Configuration

| Environment | Webhook URL |
|-------------|-------------|
| Development | ngrok URL (temporary) |
| Staging | https://staging.yourdomain.com/api/banks/webhook |
| Production | https://yourdomain.com/api/banks/webhook |

**Important:** Webhook URL is automatically set via /link/token/create when user links a bank account. No manual configuration needed.

---

## 8. Known Limitations

- Webhook signature verification not implemented (optional for production)
- ngrok free tier URL changes on restart (use paid plan for production)

---

## 9. Future Enhancements

- Add webhook signature verification for security
- Add email notifications for connection issues
- Support more webhook events (Transfer, Income, etc.)