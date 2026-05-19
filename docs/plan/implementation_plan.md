# Refactoring Plan: GoCardless → Plaid (Clean Rebuild) — v3

## Context

Replacing GoCardless with **Plaid**. No existing users — **clean rebuild**. Balanced security (AES-256 JPA Converter).

### Conceptual Mapping

| GoCardless | Plaid | DB Entity |
|---|---|---|
| `Requisition` (1 bank auth) | `Item` (1 bank login) | **`plaid_items`** (NEW table) |
| `Account ID` | `Account ID` | `bank_accounts` (1 per account, FK → `plaid_items`) |
| JWT token (short-lived) | `access_token` (permanent) | Encrypted on `plaid_items` |
| Date-range fetch | Cursor-based `/transactions/sync` | `plaid_sync_cursor` on `plaid_items` |

---

## Bug Fixes Applied

### Bug #1 Fix: Item ≠ Account — New `plaid_items` Table

**Problem:** `access_token` and `cursor` belong to an **Item** (1 bank login), not an Account. One Item can have N accounts. Storing cursor per-account causes sync corruption.

**Solution:** New `plaid_items` table:

```
plaid_items (1 per bank login)
├── item_id (PK from Plaid)
├── access_token (encrypted)
├── sync_cursor
├── status
└── user_id (FK)

bank_accounts (1 per account, FK → plaid_items)
├── plaid_account_id
├── plaid_item_id (FK → plaid_items)
├── institution_name, mask, etc.
└── user_id (FK)
```

### Bug #2 Fix: Idempotent Sync on Crash & Concurrent Webhooks

**Problem:** If sync crashes mid-pagination, cursor isn't saved. Next sync replays all transactions → duplicates. Additionally, concurrent webhook triggers and scheduled syncs can race: a `SELECT` check (e.g., `existsByPlaidTransactionIdAndBankAccount`) passes on two threads simultaneously, then both attempt `INSERT` → duplicate rows.

**Solution — Database-Level Constraint as Single Source of Truth:**

> [!IMPORTANT]
> Application-level `SELECT`-before-`INSERT` checks (e.g., `repository.existsBy...`) are **strictly forbidden** for duplicate detection. They are inherently vulnerable to TOCTOU race conditions under concurrent webhook/scheduler execution.

- The `UNIQUE(plaid_transaction_id, bank_account_id)` constraint on the `transactions` table is the **sole deduplication mechanism**
- Each transaction insert uses `saveAndFlush()` wrapped in a `try-catch(DataIntegrityViolationException)` — constraint violation = silent skip
- Cursor saved **only after** the full `while(hasMore)` loop completes successfully
- If any non-duplicate exception occurs, `@Transactional` rolls back everything including cursor — safe retry on next sync

### Bug #3 Fix: Soft-Delete for Removed Transactions

**Problem:** Hard DELETE breaks foreign keys if user tagged/budgeted the transaction.

**Solution:** Add `is_deleted` + `deleted_at` columns. Removed transactions are **soft-deleted**, preserving relationships.

---

## Proposed Changes

### Phase 1: Database Migration

#### [NEW] `V7__Refactor_Plaid_Naming.sql`

```sql
-- =============================================
-- V7: Chuẩn hoá Naming Convention từ GoCardless sang Plaid (Clean Rebuild)
-- =============================================

-- 1. Create plaid_items table (1 per bank login/Item)
CREATE TABLE plaid_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id         VARCHAR(255) NOT NULL UNIQUE,   -- Plaid item_id
    access_token    TEXT NOT NULL,                   -- AES-256 encrypted
    sync_cursor     TEXT,                            -- /transactions/sync cursor
    institution_id  VARCHAR(100),
    institution_name VARCHAR(255),
    institution_logo VARCHAR(500),
    status           VARCHAR(20) DEFAULT 'ACTIVE',    -- ACTIVE | REQUIRES_UPDATE | REMOVED
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_plaid_items_user ON plaid_items(user_id);

-- 2. Normalize bank_configs -> bank_accounts
ALTER TABLE bank_configs RENAME TO bank_accounts;
ALTER TABLE bank_accounts RENAME COLUMN account_name TO name;
ALTER TABLE bank_accounts ADD COLUMN official_name VARCHAR(255);
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS iban;
ALTER TABLE bank_accounts ADD COLUMN mask VARCHAR(10);
ALTER TABLE bank_accounts ADD COLUMN subtype VARCHAR(50);
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS institution_id;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS institution_name;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS institution_logo;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS link_reference;

DROP INDEX IF EXISTS idx_bank_configs_status;
DROP INDEX IF EXISTS idx_bank_configs_plaid_item;
DROP INDEX IF EXISTS idx_bank_configs_plaid_account;

CREATE INDEX idx_bank_accounts_status ON bank_accounts(status);
CREATE INDEX idx_bank_accounts_plaid_item ON bank_accounts(plaid_item_id);
CREATE INDEX idx_bank_accounts_plaid_account ON bank_accounts(plaid_account_id);

-- 3. Normalize transactions
ALTER TABLE transactions RENAME COLUMN bank_config_id TO bank_account_id;
ALTER TABLE transactions RENAME COLUMN bank_transaction_id TO plaid_transaction_id;

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS uq_transactions_bank_tx_id;
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_plaid_tx_id UNIQUE (plaid_transaction_id, bank_account_id);

-- 4. Soft-delete support for transactions (Bug #3 fix)
ALTER TABLE transactions ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMP;

-- 5. Sync logs now reference PlaidItem directly
TRUNCATE TABLE sync_logs;
ALTER TABLE sync_logs DROP COLUMN IF EXISTS bank_config_id;
ALTER TABLE sync_logs ADD COLUMN plaid_item_id BIGINT NOT NULL REFERENCES plaid_items(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_sync_logs_bank_config;
CREATE INDEX idx_sync_logs_plaid_item ON sync_logs(plaid_item_id);
```

**Entity relationship:**
```
User (1) ──→ (N) PlaidItem (1) ──→ (N) BankAccount (1) ──→ (N) Transaction
                  │                      │
                  ├── access_token       ├── plaid_account_id
                  ├── sync_cursor        ├── institution details
                   └── item_id            └── mask, name
```

---

### Phase 2: Backend

#### 2A. Dependencies

##### [MODIFY] `pom.xml`
```xml
<dependency>
    <groupId>com.plaid</groupId>
    <artifactId>plaid-java</artifactId>
    <version>29.0.0</version>
</dependency>
```

#### 2B. Configuration

##### [DELETE] `config/GoCardlessConfig.java`

##### [NEW] `config/PlaidConfig.java`
```java
@Configuration @Data
public class PlaidConfig {
    @Value("${plaid.client-id}") private String clientId;
    @Value("${plaid.secret}") private String secret;
    @Value("${plaid.environment}") private String environment;

    @Bean
    public PlaidApi plaidClient() {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", clientId);
        apiKeys.put("secret", secret);
        ApiClient apiClient = new ApiClient(apiKeys);
        switch (environment) {
            case "development" -> apiClient.setPlaidAdapter(ApiClient.Development);
            case "production" -> apiClient.setPlaidAdapter(ApiClient.Production);
            default -> apiClient.setPlaidAdapter(ApiClient.Sandbox);
        }
        return apiClient.createService(PlaidApi.class);
    }
}
```

##### [MODIFY] `config/SyncConfig.java`
Remove `lookBackDays` and `initialSyncDays` (Plaid cursor replaces date-range):
```java
@Configuration @ConfigurationProperties(prefix = "sync") @Data
public class SyncConfig {
    private boolean enabled = true;
    private int intervalMinutes = 15;
    private int activeUserDays = 30;
}
```

##### [MODIFY] `application.yaml`
```yaml
plaid:
  client-id: ${PLAID_CLIENT_ID:your-client-id}
  secret: ${PLAID_SECRET:your-secret}
  environment: ${PLAID_ENV:sandbox}

encryption:
  secret-key: ${ENCRYPTION_KEY:base64-encoded-32-byte-key}

sync:
  enabled: true
  interval-minutes: 15
  active-user-days: 30
```

##### [NEW] `utils/AccessTokenEncryptor.java`
JPA `@Converter` — AES-256-GCM, transparent encrypt/decrypt:
```java
@Converter @Component
public class AccessTokenEncryptor implements AttributeConverter<String, String> {
    private final SecretKey key;
    public AccessTokenEncryptor(@Value("${encryption.secret-key}") String b64) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(b64), "AES");
    }
    @Override public String convertToDatabaseColumn(String plain) { /* AES encrypt → Base64 */ }
    @Override public String convertToEntityAttribute(String cipher) { /* Base64 → AES decrypt */ }
}
```

##### [MODIFY] `config/SecurityConfig.java`
```diff
- .requestMatchers("/api/banks/callback").permitAll()
+ .requestMatchers("/api/banks/webhook").permitAll()
```

---

#### 2C. Entity Layer

##### [NEW] `entity/PlaidItem.java`
```java
@Entity @Table(name = "plaid_items")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlaidItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "item_id", nullable = false, unique = true)
    private String itemId;

    @Convert(converter = AccessTokenEncryptor.class)
    @Column(name = "access_token", nullable = false)
    private String accessToken;     // encrypted at rest

    @Column(name = "sync_cursor", columnDefinition = "TEXT")
    private String syncCursor;

    @Column(name = "institution_id")
    private String institutionId;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "status")
    private String status = "ACTIVE";   // ACTIVE | REQUIRES_UPDATE | REMOVED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

##### [MODIFY] `entity/BankAccount.java`
```diff
- private String requisitionId;
- private String gocardlessAccountId;
- private LocalDateTime accessExpiresAt;
+ @ManyToOne(fetch = FetchType.LAZY)
+ @JoinColumn(name = "plaid_item_id")
+ private PlaidItem plaidItem;
+
+ @Column(name = "plaid_account_id")
+ private String plaidAccountId;
```
Retained: `status`, `lastSyncedAt`.

##### [MODIFY] `entity/Transaction.java`
```diff
+ @Column(name = "is_deleted")
+ private Boolean isDeleted = false;
+
+ @Column(name = "deleted_at")
+ private LocalDateTime deletedAt;
```

**No changes to `User.java`.**

---

#### 2D. Repository Layer

##### [NEW] `repository/PlaidItemRepository.java`
```java
@Repository
public interface PlaidItemRepository extends JpaRepository<PlaidItem, Long> {
    Optional<PlaidItem> findByItemId(String itemId);
    List<PlaidItem> findByUserAndStatus(User user, String status);
    List<PlaidItem> findByStatus(String status);
}
```

##### [MODIFY] `repository/BankAccountRepository.java`
```diff
- Optional<BankAccount> findByGocardlessAccountId(String id);
- Optional<BankAccount> findByRequisitionId(String id);
+ Optional<BankAccount> findByPlaidAccountId(String accountId);
+ List<BankAccount> findByPlaidItem(PlaidItem item);
+ List<BankAccount> findByPlaidItemAndStatus(PlaidItem item, String status);
```

##### [MODIFY] `repository/TransactionRepository.java`
```diff
+ Optional<Transaction> findByPlaidTransactionIdAndBankAccount(
+     String plaidTransactionId, BankAccount bankAccount);
+
+ // For filtered queries: exclude soft-deleted
+ @Query("... AND (t.isDeleted = false OR t.isDeleted IS NULL)")
```

---

#### 2E. Service Layer

##### [DELETE] `service/GoCardlessService.java`

##### [NEW] `service/PlaidService.java`
```java
@Service @RequiredArgsConstructor @Slf4j
public class PlaidService {
    private final PlaidApi plaidClient;

    public String createLinkToken(String clientUserId);
    public ItemPublicTokenExchangeResponse exchangePublicToken(String publicToken);
    public List<AccountBase> getAccounts(String accessToken);
    public TransactionsSyncResponse syncTransactions(String accessToken, String cursor);
    public String createUpdateLinkToken(String clientUserId, String accessToken);
    public void removeItem(String accessToken);
}
```

##### [MODIFY] `service/BankLinkingService.java` — Major rewrite

```
getLinkToken(user):
  → PlaidService.createLinkToken(user.getId().toString())
  → return { linkToken }

exchangeToken(user, publicToken, metadata):
  → PlaidService.exchangePublicToken(publicToken)
   → Save PlaidItem { itemId, accessToken (encrypted), institutionName, institutionLogo }
   → PlaidService.getAccounts(accessToken)
   → For each account: Save BankAccount { plaidItem, plaidAccountId, mask, name }
   → Trigger initial sync on the PlaidItem
   → return success

unlinkBank(user, bankAccountId):
  → Find BankAccount, verify ownership
  → Unlink transactions (set bankAccount = null)
  → Delete BankAccount
  → If no more BankAccounts for this PlaidItem → PlaidService.removeItem() + delete PlaidItem
```

##### [MODIFY] `service/TransactionSyncService.java` — Major rewrite

```java
@Transactional
public SyncResult syncItem(PlaidItem item) {
    String cursor = item.getSyncCursor();  // null on first sync
    List<BankAccount> accounts = bankAccountRepo.findByPlaidItem(item);
    // Build account lookup map
    Map<String, BankAccount> accountMap = accounts.stream()
        .collect(Collectors.toMap(BankAccount::getPlaidAccountId, ba -> ba));

    int totalAdded = 0, totalModified = 0, totalRemoved = 0;
    boolean hasMore = true;

    while (hasMore) {
        var response = plaidService.syncTransactions(item.getAccessToken(), cursor);

        // Process REMOVED → soft-delete (Bug #3)
        for (var removed : response.getRemoved()) {
            transactionRepo.findByPlaidTransactionIdAndBankAccount(
                removed.getTransactionId(), accountMap.get(removed.getAccountId()))
              .ifPresent(tx -> {
                  tx.setIsDeleted(true);
                  tx.setDeletedAt(LocalDateTime.now());
                  transactionRepo.save(tx);
              });
            totalRemoved++;
        }

        // Process MODIFIED → update existing
        for (var mod : response.getModified()) {
            BankAccount ba = accountMap.get(mod.getAccountId());
            transactionRepo.findByPlaidTransactionIdAndBankAccount(mod.getTransactionId(), ba)
              .ifPresent(tx -> {
                  tx.setAmount(BigDecimal.valueOf(mod.getAmount()));
                  tx.setDescription(mod.getName());
                  tx.setIsDeleted(false);  // un-delete if was soft-deleted
                  transactionRepo.save(tx);
              });
            totalModified++;
        }

        // Process ADDED → insert via DB constraint guard (Bug #2)
        // NEVER use existsBy() here — it fails under concurrent webhook/scheduler execution.
        // Instead, attempt insert and let the UNIQUE constraint reject duplicates.
        for (var added : response.getAdded()) {
            BankAccount ba = accountMap.get(added.getAccountId());
            if (ba == null) continue;  // account not tracked

            Transaction tx = Transaction.builder()
                .user(item.getUser())
                .bankAccount(ba)
                .plaidTransactionId(added.getTransactionId())
                .amount(BigDecimal.valueOf(Math.abs(added.getAmount())))
                .type(added.getAmount() < 0 ? TransactionType.OUT : TransactionType.IN)
                .currency(added.getIsoCurrencyCode())
                .description(added.getName())
                .transactionDate(LocalDate.parse(added.getDate()).atStartOfDay())
                .isDeleted(false)
                .build();
            try {
                transactionRepo.saveAndFlush(tx);
                totalAdded++;
            } catch (DataIntegrityViolationException e) {
                // UNIQUE(plaid_transaction_id, bank_account_id) violation — duplicate, skip silently
                log.trace("Duplicate transaction skipped: {}", added.getTransactionId());
            }
        }

        hasMore = response.getHasMore();
        cursor = response.getNextCursor();
    }

    // Cursor saved ONLY after all pages processed successfully
    item.setSyncCursor(cursor);
    item.setUpdatedAt(LocalDateTime.now());
    plaidItemRepo.save(item);

    // Update lastSyncedAt on all accounts
    accounts.forEach(ba -> { ba.setLastSyncedAt(LocalDateTime.now()); });
    bankAccountRepo.saveAll(accounts);

    return new SyncResult(totalAdded, totalModified, totalRemoved);
}
```

**Key:** Sync operates on **PlaidItem** level (not BankAccount), distributing transactions to correct accounts via `accountMap`.

---

#### 2F. Controller Layer

##### [MODIFY] `controller/BankController.java`

```diff
- GET  /api/banks/institutions     → REMOVE (Plaid Link handles UI)
- POST /api/banks/link             → REPLACE with:
+ GET  /api/banks/link-token       → Create Plaid Link token
+ POST /api/banks/exchange-token   → Exchange public_token
- GET  /api/banks/callback         → REPLACE with:
+ POST /api/banks/webhook          → Plaid webhook receiver
```

Retained unchanged: `GET /banks`, `GET /banks/{id}`, `POST /banks/{id}/sync`, `DELETE /banks/{id}`, `GET /banks/{id}/sync-history`

Webhook handler:
```java
@PostMapping("/webhook")
public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> body) {
    String type = (String) body.get("webhook_type");
    String code = (String) body.get("webhook_code");
    String itemId = (String) body.get("item_id");

    if ("TRANSACTIONS".equals(type) && "SYNC_UPDATES_AVAILABLE".equals(code)) {
        plaidItemRepo.findByItemId(itemId)
            .ifPresent(item -> transactionSyncService.syncItem(item));
    } else if ("ITEM".equals(type) && "ERROR".equals(code)) {
        plaidItemRepo.findByItemId(itemId)
            .ifPresent(item -> { item.setStatus("REQUIRES_UPDATE"); plaidItemRepo.save(item); });
    }
    return ResponseEntity.ok().build();
}
```

---

#### 2G. DTO Changes

##### [DELETE] Entire `dto/gocardless/` package (10 files)
##### [DELETE] `dto/bank/LinkBankRequest.java`, `LinkBankResponse.java`, `CallbackResponse.java`
##### [MODIFY] `dto/bank/SyncResponse.java` — add `modified`, `removed` counts
##### [NEW] `dto/bank/ExchangeTokenRequest.java`
```java
@Data
public class ExchangeTokenRequest {
    @NotBlank private String publicToken;
    private String institutionId;
    private String institutionName;
    private List<String> accountIds;
}
```
##### [NEW] `dto/bank/ExchangeTokenResponse.java`
```java
@Data @Builder
public class ExchangeTokenResponse {
    private boolean success;
    private String message;
    private int accountsLinked;
}
```

---

### Phase 3: Frontend

##### [MODIFY] `frontend/package.json` — add `"react-plaid-link": "^3.6.0"`

##### [MODIFY] `frontend/src/pages/BankAccounts.jsx`
Replace institution modal + redirect with Plaid Link overlay:
```jsx
import { usePlaidLink } from 'react-plaid-link';

// Fetch link_token → open Plaid overlay → onSuccess → POST exchange-token
const { open, ready } = usePlaidLink({
    token: linkToken,
    onSuccess: async (publicToken, metadata) => {
        await api.post('/banks/exchange-token', {
            publicToken,
            institutionId: metadata.institution.institution_id,
            institutionName: metadata.institution.name,
            accountIds: metadata.accounts.map(a => a.id),
        });
        loadAccounts();
    },
});
// Single button: <Button onClick={open} disabled={!ready}>Connect Bank</Button>
```
**Removed:** Country selector, institution selector, loadInstitutions API, link modal.

---

## File Change Summary

### DELETE (15 files)
| File | Reason |
|---|---|
| `config/GoCardlessConfig.java` | GoCardless |
| `service/GoCardlessService.java` | GoCardless |
| `dto/gocardless/*` (10 files) | GoCardless DTOs |
| `dto/bank/LinkBankRequest.java` | Replaced by Plaid Link |
| `dto/bank/LinkBankResponse.java` | Replaced by linkToken |
| `dto/bank/CallbackResponse.java` | No callback in Plaid |

### CREATE (8 files)
| File | Purpose |
|---|---|
| `config/PlaidConfig.java` | SDK setup + PlaidApi bean |
| `service/PlaidService.java` | Plaid API wrapper |
| `entity/PlaidItem.java` | Item entity (Bug #1 fix) |
| `repository/PlaidItemRepository.java` | Item queries |
| `utils/AccessTokenEncryptor.java` | AES-256 JPA converter |
| `dto/bank/ExchangeTokenRequest.java` | Token exchange request |
| `dto/bank/ExchangeTokenResponse.java` | Token exchange response |
| `db/migration/V7__Refactor_Plaid_Naming.sql` | Schema migration |

### MODIFY (12 files)
| File | Change |
|---|---|
| `pom.xml` | Add `plaid-java` |
| `application.yaml` | Plaid + encryption config |
| `entity/BankAccount.java` | FK to PlaidItem, remove GC fields |
| `entity/Transaction.java` | Add `isDeleted`, `deletedAt` |
| `config/SyncConfig.java` | Remove date-range params |
| `config/SecurityConfig.java` | callback → webhook |
| `service/BankLinkingService.java` | Link token + exchange flow |
| `service/TransactionSyncService.java` | Cursor sync on Item level |
| `controller/BankController.java` | New endpoints |
| `repository/BankAccountRepository.java` | Plaid queries |
| `repository/TransactionRepository.java` | Soft-delete support |
| `frontend/src/pages/BankAccounts.jsx` | Plaid Link SDK |
| `docker-compose.yaml` | Plaid env vars |

---

## Verification Plan

```bash
./mvnw clean test                  # JPA validate catches schema mismatches
./mvnw spring-boot:run             # Flyway V6 runs, app starts
```

### Sandbox Testing
1. Env: `PLAID_CLIENT_ID`, `PLAID_SECRET`, `PLAID_ENV=sandbox`
2. Connect → Plaid Link → sandbox bank → `user_good`/`pass_good`
3. Verify: PlaidItem + BankAccount(s) created, transactions synced
4. Re-sync → no duplicates (cursor + idempotency guard)
5. Webhook test via Plaid Sandbox tools
