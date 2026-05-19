# Plaid Integration Documentation

## Overview

This document details the complete migration from GoCardless to Plaid for bank account linking and transaction synchronization in the Expense Tracking Global application.

---

## 1. What We Did

### 1.1 Migration from GoCardless to Plaid

We migrated from GoCardless (European Open Banking) to Plaid (primarily US-focused, but supports UK/Europe) to enable more comprehensive bank account connectivity with better transaction sync capabilities.

**Key Changes:**
- Removed GoCardless SDK and configuration
- Added Plaid SDK dependency
- Created new entities and services for Plaid integration
- Implemented Plaid Link widget for bank linking

### 1.2 Database Migrations

| Migration | Description |
|-----------|-------------|
| V1__Init_database.sql | Initial schema |
| V2__GoCardless_Migration.sql | GoCardless support (deprecated) |
| V3__Adjust_bank_configs_And_users.sql | Schema adjustments |
| V4__Add_link_reference_to_bank_configs.sql | Link reference UUID |
| V5__Add_category_unique_constraint.sql | Category uniqueness |
| V6__Plaid_Migration.sql | Plaid entities and tables |
| V7__Refactor_Plaid_Naming.sql | Naming consistency |

---

## 2. How Plaid Works in This Application (Current Implementation)

### 2.1 Architecture Overview

```
┌──────────────┐     ┌──────────────────┐     ┌────────────────────┐
│    Plaid     │     │   Your Server   │     │     Database       │
│   (Bank)     │◄───►│                  │◄───►│                    │
│              │     │                  │     │                    │
└──────────────┘     └──────────────────┘     └────────────────────┘
```

**Current Flow (Manual Sync):**
1. User clicks "Link Bank" → Plaid Link widget opens
2. User authenticates with bank → Plaid returns public token
3. Backend exchanges public token for access token
4. Backend fetches account details from Plaid
5. User clicks "Sync" → Backend pulls transactions from Plaid

**Note:** Automatic webhook-based sync is NOT implemented yet. Currently, users must manually trigger sync to get new transactions.

### 2.2 Plaid Products Used

Currently enabled products:
- **AUTH** - Account verification (ownership validation)
- **TRANSACTIONS** - Transaction history and sync

### 2.3 Flow Details with Code Snippets

#### Step 1: User Initiates Bank Linking

**Frontend (BankAccounts.jsx):**
```javascript
const openPlaidLink = async () => {
    // Call backend to get Link token
    const response = await api.post('/banks/link');
    const linkToken = response.data.linkToken;

    // Open Plaid Link widget
    const handler = window.Plaid.create({
        token: linkToken,
        onSuccess: async (publicToken) => {
            // Send public token to backend
            await api.post('/banks/link/complete', { publicToken });
            loadAccounts();
        },
    });
    handler.open();
};
```

#### Step 2: Backend Creates Link Token

**PlaidService.java:**
```java
public String createLinkToken(String clientUserId) {
    LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
            .clientUserId(clientUserId);

    LinkTokenCreateRequest request = new LinkTokenCreateRequest()
            .user(user)
            .clientName("Expense Tracking Global")
            .products(List.of(Products.AUTH, Products.TRANSACTIONS))
            .countryCodes(List.of(CountryCode.GB))
            .language("en");

    Response<LinkTokenCreateResponse> response = plaidClient.linkTokenCreate(request).execute();
    return response.body().getLinkToken();
}
```

#### Step 3: Backend Exchanges Public Token

**PlaidService.java:**
```java
public ItemPublicTokenExchangeResponse exchangePublicToken(String publicToken) {
    ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
            .publicToken(publicToken);

    ItemPublicTokenExchangeResponse response = plaidClient.itemPublicTokenExchange(request).execute().body();
    return response;  // Contains access_token and item_id
}
```

#### Step 4: Backend Fetches Account Details

**PlaidService.java:**
```java
public List<AccountBase> getAccounts(String accessToken) {
    AccountsGetRequest request = new AccountsGetRequest()
            .accessToken(accessToken);

    AccountsGetResponse response = plaidClient.accountsGet(request).execute().body();
    return response.getAccounts();
}
```

#### Step 5: Backend Stores Bank Account

**BankLinkingService.java:**
```java
public void completeLinking(User user, PlaidExchangeRequest request) {
    // Exchange public token for access token
    var exchange = plaidService.exchangePublicToken(request.getPublicToken());
    var accessToken = exchange.getAccessToken();
    var itemId = exchange.getItemId();

    // Create or update PlaidItem
    PlaidItem item = plaidItemRepository.findByItemId(itemId)
            .orElseGet(() -> PlaidItem.builder()
                    .user(user)
                    .itemId(itemId)
                    .build());
    item.setAccessToken(accessToken);
    item.setStatus("ACTIVE");
    plaidItemRepository.save(item);

    // Fetch and store accounts
    var accounts = plaidService.getAccounts(accessToken);
    for (var account : accounts) {
        BankAccount bankAccount = bankConfigRepository.findByPlaidAccountId(account.getAccountId())
                .orElseGet(BankAccount::new);
        bankAccount.setUser(user);
        bankAccount.setPlaidItem(item);
        bankAccount.setPlaidAccountId(account.getAccountId());
        bankAccount.setName(account.getName());
        bankAccount.setMask(account.getMask());
        bankAccount.setStatus("ACTIVE");
        bankConfigRepository.save(bankAccount);
    }

    // Run initial sync
    transactionSyncService.initialSync(bankAccount);
}
```

#### Step 6: Transaction Sync (Manual)

**TransactionSyncService.java:**
```java
@Async
@Transactional
public SyncResult syncItem(PlaidItem item) {
    String cursor = item.getSyncCursor();
    List<BankAccount> accounts = bankAccountRepository.findByPlaidItem_Id(item.getId());
    Map<String, BankAccount> accountMap = accounts.stream()
            .collect(Collectors.toMap(BankAccount::getPlaidAccountId, a -> a));

    int totalAdded = 0, totalModified = 0, totalRemoved = 0;

    while (true) {
        var response = plaidService.syncTransactions(item.getAccessToken(), cursor);

        // Handle removed transactions
        if (response.getRemoved() != null) {
            for (var removed : response.getRemoved()) {
                BankAccount account = accountMap.get(removed.getAccountId());
                if (account == null) continue;
                
                // Soft-delete transactions
                transactionRepository.findAll().stream()
                    .filter(tx -> removed.getTransactionId().equals(tx.getPlaidTransactionId()))
                    .forEach(tx -> {
                        tx.setIsDeleted(true);
                        tx.setDeletedAt(LocalDateTime.now());
                        transactionRepository.save(tx);
                    });
                totalRemoved++;
            }
        }

        // Handle modified transactions
        if (response.getModified() != null) {
            for (var modified : response.getModified()) {
                BankAccount account = accountMap.get(modified.getAccountId());
                if (account == null) continue;
                
                transactionRepository.findAll().stream()
                    .filter(tx -> modified.getTransactionId().equals(tx.getPlaidTransactionId()))
                    .findFirst()
                    .ifPresent(tx -> {
                        tx.setAmount(BigDecimal.valueOf(Math.abs(modified.getAmount())));
                        tx.setDescription(modified.getName());
                        transactionRepository.save(tx);
                    });
                totalModified++;
            }
        }

        // Handle new transactions (with idempotency)
        if (response.getAdded() != null) {
            for (var added : response.getAdded()) {
                BankAccount account = accountMap.get(added.getAccountId());
                if (account == null) continue;

                Transaction tx = Transaction.builder()
                        .user(item.getUser())
                        .bankAccount(account)
                        .plaidTransactionId(added.getTransactionId())
                        .amount(BigDecimal.valueOf(Math.abs(added.getAmount())))
                        .type(added.getAmount() < 0 ? TransactionType.OUT : TransactionType.IN)
                        .description(added.getName())
                        .transactionDate(added.getDate().atStartOfDay())
                        .build();

                try {
                    transactionRepository.saveAndFlush(tx);
                    totalAdded++;
                } catch (DataIntegrityViolationException e) {
                    // Duplicate - skip silently (idempotency)
                    log.trace("Duplicate transaction skipped: {}", added.getTransactionId());
                }
            }
        }

        // Check if more pages
        if (!Boolean.TRUE.equals(response.getHasMore())) break;
        cursor = response.getNextCursor();
    }

    // Save cursor for next sync
    item.setSyncCursor(cursor);
    plaidItemRepository.save(item);

    return new SyncResult(totalAdded, totalModified, totalRemoved);
}
```

---

## 3. Technical Implementation Details

### 3.1 Plaid Configuration

**File:** `src/main/resources/application.yaml`

```yaml
plaid:
  client-id: ${PLAID_CLIENT_ID}
  secret: ${PLAID_SECRET}
  environment: ${PLAID_ENV:sandbox}
```

**PlaidConfig.java:**
```java
@Configuration
@Data
public class PlaidConfig {
    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.environment}")
    private String environment;

    @Bean
    public PlaidApi plaidClient() {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", clientId);
        apiKeys.put("secret", secret);
        apiKeys.put("plaidVersion", "2020-09-14");

        ApiClient apiClient = new ApiClient(apiKeys);
        if ("production".equalsIgnoreCase(environment)) {
            apiClient.setPlaidAdapter(ApiClient.Production);
        } else {
            apiClient.setPlaidAdapter(ApiClient.Sandbox);
        }
        return apiClient.createService(PlaidApi.class);
    }
}
```

### 3.2 Entity Relationships

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│      User       │       │    PlaidItem    │       │   BankAccount  │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id              │◄──┐   │ id              │◄──┐   │ id              │
│ email           │   │   │ user_id         │   │   │ plaid_item_id  │
│ password_hash   │   └──►│ item_id (UUID)  │   └──►│ plaid_account_id│
└─────────────────┘       │ access_token    │       │ name            │
                         │ sync_cursor     │       │ mask            │
                         │ status          │       │ status          │
                         └─────────────────┘       └─────────────────┘
                                    │
                                    ▼
                         ┌─────────────────┐
                         │   Transaction   │
                         ├─────────────────┤
                         │ id              │
                         │ bank_account_id │
                         │ plaid_transaction_id (unique)
                         │ amount          │
                         │ description     │
                         │ transaction_date│
                         └─────────────────┘
```

### 3.3 Database Schema

**V6__Plaid_Migration.sql:**
```sql
-- Create plaid_items table
CREATE TABLE plaid_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    item_id VARCHAR(255) NOT NULL UNIQUE,
    access_token TEXT NOT NULL,
    sync_cursor TEXT,
    institution_id VARCHAR(255),
    institution_name VARCHAR(255),
    institution_logo VARCHAR(500),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create bank_accounts table (renamed from bank_configs)
CREATE TABLE bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    plaid_item_id BIGINT REFERENCES plaid_items(id),
    plaid_account_id VARCHAR(255),
    name VARCHAR(255),
    official_name TEXT,
    mask VARCHAR(10),
    subtype VARCHAR(50),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add unique constraint on plaid_transaction_id
ALTER TABLE transactions 
ADD CONSTRAINT uk_plaid_transaction_id UNIQUE (plaid_transaction_id, bank_account_id);
```

### 3.4 Key Files and Their Roles

| File | Purpose |
|------|---------|
| `PlaidConfig.java` | Spring configuration for Plaid API client |
| `PlaidService.java` | Low-level Plaid API calls (link token, exchange, sync, accounts) |
| `BankLinkingService.java` | Business logic for linking/unlinking banks |
| `TransactionSyncService.java` | Transaction synchronization with Plaid (cursor-based) |
| `PlaidItem.java` | Entity representing a linked Plaid item |
| `BankAccount.java` | Entity representing a linked bank account |
| `BankController.java` | REST API endpoints |
| `AccessTokenEncryptor.java` | Encrypts access tokens before storing in DB |

### 3.5 Access Token Security

Access tokens are encrypted before storing in the database using a custom JPA converter:

**AccessTokenEncryptor.java:**
```java
@Converter
public class AccessTokenEncryptor implements AttributeConverter<String, String> {
    @Value("${encryption.secret-key}")
    private String secretKey;

    @Override
    public String convertToDatabaseColumn(String accessToken) {
        // Encrypt using AES
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        // Decrypt
    }
}
```

---

## 4. Security Considerations

### 4.1 Access Token Storage

Access tokens are encrypted before storing in the database using the `AccessTokenEncryptor` converter. This prevents plain-text tokens from being stored in case of database compromise.

### 4.2 HTTPS Requirement

Plaid requires all API calls to use HTTPS with TLS v1.2+.

### 4.3 API Version

The implementation uses Plaid API version `2020-09-14` as specified in PlaidConfig.

---

## 5. Testing

### 5.1 Testing Bank Linking

1. Start the backend application
2. Start the frontend
3. Login to the application
4. Navigate to Bank Accounts page
5. Click "Link Bank"
6. Complete the Plaid Link flow
7. Verify bank account appears in the list

### 5.2 Testing Transaction Sync

1. After linking a bank, click "Sync"
2. Check that transactions appear in the Transactions page
3. Click "Sync" again to verify idempotency (no duplicates)

---

## 6. Known Limitations

### 6.1 No Automatic Sync

Currently, users must manually trigger sync to get new transactions. There is no automatic webhook-based sync implemented.

### 6.2 No Webhook Verification

Webhook signature verification is not implemented (webhooks not implemented yet).

---

## 7. Future Enhancements (Planned)

- [ ] Implement webhook endpoint for automatic transaction sync
- [ ] Add webhook signature verification for security
- [ ] Income verification product (for salary/payroll data)
- [ ] Transfer/payment functionality

---

## 8. Resources

- [Plaid Documentation](https://plaid.com/docs/)
- [Plaid API Reference](https://plaid.com/docs/api/)
- [Link Token Create](https://plaid.com/docs/api/link/index.html.md)
- [Transactions Sync](https://plaid.com/docs/api/products/transactions/index.html.md)

---

This documentation covers the current Plaid integration implementation (v1.0). The implementation handles bank linking, account fetching, and manual transaction sync with proper idempotency handling.