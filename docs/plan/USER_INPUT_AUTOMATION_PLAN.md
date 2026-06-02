# User Input Automation Plan

> **Project:** Expense Tracking Global  
> **Goal:** Reduce manual transaction input without depending on third-party bank APIs  
> **Date:** 2026-05-29  
> **Scope:** CSV/XLSX import, category rules, quick add templates, recurring transactions, English natural language input, receipt/bill upload  
> **Out of scope for first implementation:** Real bank API approval, full AI/OCR dependency, production S3 migration

---

## 1. Problem

The project currently supports manual transactions and Plaid-based bank sync. Plaid is useful, but relying on third-party bank API approval is a major product risk:

- The app can be blocked if Plaid does not approve development/production access.
- Users still need a usable product even without live bank sync.
- Manual input transaction-by-transaction is too slow for real daily use.

The new direction is:

```text
Bank API optional, user input assisted.
```

The app should keep Plaid as one possible input source, but it should not depend on Plaid to be valuable.

---

## 2. Target Product Experience

Users should be able to add transactions through several low-friction paths:

```text
Manual form
CSV/XLSX import
Quick add templates
Recurring transactions
Natural language input in English
Receipt/bill upload
Optional OCR extraction later
Optional bank provider later
```

Instead of asking the user to type every transaction manually, the app should help them import, generate, parse, reuse, and confirm transaction drafts.

---

## 3. Recommended Implementation Order

```text
1. Transaction source foundation
2. CSV import MVP
3. Category rules
4. Natural language input: English
5. Quick add templates
6. Receipt/bill upload MVP
7. Recurring transactions
8. Optional OCR / AI extraction
9. Optional provider abstraction for Plaid/demo/future bank APIs
```

Reasoning:

- CSV import solves the biggest no-bank-API problem first.
- Category rules make imports useful by reducing cleanup.
- Natural language input reduces daily friction.
- Quick add and recurring reduce repeated manual work.
- Receipt upload is useful even without OCR.
- OCR and third-party providers should remain optional.

---

## 4. Phase 1 - Transaction Source Foundation

### Goal

Make every transaction traceable to its input method.

Current issue:

```java
manual transactions use plaidTransactionId = "MANUAL_" + UUID
```

This works, but it mixes Plaid identity with non-Plaid transactions.

### Database Changes

Add columns to `transactions`:

```sql
ALTER TABLE transactions
ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'MANUAL_FORM',
ADD COLUMN source_reference VARCHAR(255),
ADD COLUMN import_batch_id UUID,
ADD COLUMN original_input TEXT,
ADD COLUMN parse_confidence NUMERIC(5, 2);
```

Recommended enum:

```text
MANUAL_FORM
CSV_IMPORT
XLSX_IMPORT
QUICK_TEMPLATE
RECURRING
NATURAL_LANGUAGE_EN
RECEIPT_UPLOAD
PLAID
DEMO_BANK
```

### Backend Changes

Create:

```text
TransactionSource enum
```

Update transaction creation so:

- Manual form transactions use `source = MANUAL_FORM`.
- Plaid transactions use `source = PLAID`.
- Future imports set their own source.
- `plaidTransactionId` is no longer used as the generic source id.

### Acceptance Criteria

- Existing manual transaction creation still works.
- Existing Plaid-imported transactions still work.
- New transactions have a meaningful `source`.
- `source_reference` can be used for duplicate detection.

---

## 5. Phase 2 - CSV Import MVP

### Goal

Allow users to import bank statements without any bank API.

### User Flow

```text
User uploads CSV
  -> system parses headers and sample rows
  -> user maps columns
  -> system previews valid/invalid/duplicate rows
  -> user confirms import
  -> transactions are created
  -> import summary is shown
```

### Supported Columns

Required mappings:

```text
date
description
amount
```

Optional mappings:

```text
type
category
currency
account
```

### Amount Handling

Support two modes:

```text
SIGNED_AMOUNT
SEPARATE_IN_OUT_COLUMNS
```

`SIGNED_AMOUNT`:

```text
amount > 0 => IN
amount < 0 => OUT
stored amount = absolute value
```

`SEPARATE_IN_OUT_COLUMNS`:

```text
money in column present => IN
money out column present => OUT
```

### Backend API

```text
POST /api/imports/csv/preview
POST /api/imports/csv/commit
GET  /api/imports
GET  /api/imports/{id}
```

### Suggested DTOs

```text
CsvImportPreviewRequest
CsvColumnMapping
CsvImportPreviewResponse
CsvImportRowPreview
CsvImportCommitRequest
CsvImportResultResponse
```

### Import Batch Entity

Create:

```text
import_batches
```

Fields:

```text
id
user_id
source
file_name
status
total_rows
imported_rows
duplicate_rows
invalid_rows
created_at
completed_at
```

### Duplicate Detection

Generate a stable hash:

```text
hash(userId + normalizedDate + normalizedAmount + normalizedDescription)
```

Store as:

```text
source_reference = CSV_<hash>
```

If a transaction with the same `user_id`, `source`, and `source_reference` exists, mark as duplicate.

### Frontend

Add:

```text
Transactions -> Import
```

UI steps:

1. Upload CSV.
2. Show detected columns.
3. Map columns.
4. Preview rows.
5. Show validation errors and duplicates.
6. Confirm import.
7. Show import result.

### Acceptance Criteria

- User can import a valid CSV.
- User can preview rows before saving.
- Duplicate rows are not imported twice.
- Invalid rows are shown clearly.
- Imported transactions appear in existing transaction list/dashboard.

---

## 6. Phase 3 - Category Rules

### Goal

Automatically categorize imported or parsed transactions using user-defined rules.

### Rule Examples

```text
description contains "grab" -> Transport
description contains "starbucks" -> Coffee
description contains "netflix" -> Subscription
description contains "salary" -> Income
```

### Database

Create:

```text
category_rules
```

Fields:

```text
id
user_id
keyword
match_type
category_id
transaction_type
priority
active
created_at
updated_at
```

`match_type`:

```text
CONTAINS
STARTS_WITH
EXACT
REGEX
```

For MVP, implement only:

```text
CONTAINS
```

### Backend API

```text
GET    /api/category-rules
POST   /api/category-rules
PUT    /api/category-rules/{id}
DELETE /api/category-rules/{id}
```

### Usage Points

Apply rules during:

```text
CSV import
Natural language parsing
Future Plaid/demo-bank imports
```

### Acceptance Criteria

- User can create category rules.
- Rules are scoped per user.
- Import and natural language drafts can auto-fill category.
- User can still override category before confirm.

---

## 7. Phase 4 - Natural Language Input

### Goal

Let users create transaction drafts by typing everyday language.

The first implementation supports English only because the app currently displays transaction amounts in the default app currency, so large local-currency style examples would be interpreted as unrealistically large app-currency amounts.

### General Flow

```text
User enters text
  -> backend parses text
  -> backend returns transaction draft
  -> frontend shows editable preview
  -> user confirms
  -> transaction is created
```

### Backend API

```text
POST /api/transactions/natural-language/parse
POST /api/transactions/natural-language/confirm
```

Alternative shorter endpoint:

```text
POST /api/transactions/quick-parse
```

Recommended request:

```json
{
  "text": "lunch 12 today",
  "language": "en"
}
```

Recommended response:

```json
{
  "originalText": "lunch 12 today",
  "language": "en",
  "description": "lunch",
  "amount": 12,
  "type": "OUT",
  "category": "Food",
  "transactionDate": "2026-06-01T00:00:00",
  "confidence": 0.82,
  "warnings": []
}
```

### English Parser

Supported examples:

```text
lunch 12 today
grab 120 yesterday
salary 2500 on the 25th
coffee 4.5 this morning
netflix 15 monthly
gas 70
book 20 last friday
```

Amount rules:

```text
12 -> 12
12.50 -> 12.50
$12 -> 12
1k -> 1000
2.5k -> 2500
```

Date rules:

```text
today -> today
yesterday -> today - 1 day
this morning -> today
last night -> today - 1 day
on the 25th -> day 25 of current month
05/25 -> date in current year, based on configured locale
```

Type rules:

```text
salary, paycheck, paid, refund -> IN
most other merchant/spending words -> OUT
```

Category keyword examples:

```text
lunch, dinner, coffee, restaurant -> Food
grab, uber, taxi, bus, gas -> Transport
salary, paycheck -> Income
netflix, spotify -> Subscription
hospital, pharmacy -> Health
```

### Parser Design

Use rule-based parsing first.

Create:

```text
NaturalLanguageParser
EnglishTransactionParser
ParsedTransactionDraft
```

Do not depend on AI for MVP.

Confidence scoring:

```text
amount found: +0.35
date found: +0.20
category matched: +0.20
type inferred: +0.15
description extracted: +0.10
```

If confidence is low, frontend should show warning:

```text
Please review this transaction before saving.
```

When confirmed:

```text
source = NATURAL_LANGUAGE_EN
original_input = user text
parse_confidence = confidence
source_reference = hash(userId + originalText + amount + date)
```

### Acceptance Criteria

- English input can parse amount, date, type, category, and description.
- Parser returns editable draft.
- User must confirm before transaction is saved.
- Low-confidence parse shows a warning.
- Confirmed transactions are saved with correct `source`.

---

## 8. Phase 5 - Quick Add Templates

### Goal

Allow users to create reusable transaction templates for common spending.

### Examples

```text
Coffee - 45,000 - Food - OUT
Lunch - 70,000 - Food - OUT
Grab - 120,000 - Transport - OUT
Salary - 25,000,000 - Income - IN
```

### Database

Create:

```text
transaction_templates
```

Fields:

```text
id
user_id
name
description
amount
type
category_id
currency
active
created_at
updated_at
```

### API

```text
GET    /api/transaction-templates
POST   /api/transaction-templates
PUT    /api/transaction-templates/{id}
DELETE /api/transaction-templates/{id}
POST   /api/transaction-templates/{id}/create-transaction
```

### Frontend

Add a quick-add strip on the Transactions page:

```text
[Coffee] [Lunch] [Grab] [Salary]
```

Clicking a template opens a pre-filled transaction modal or creates a transaction directly with confirmation.

### Acceptance Criteria

- User can create/edit/delete templates.
- User can create a transaction from a template.
- Template-created transactions use `source = QUICK_TEMPLATE`.

---

## 9. Phase 6 - Receipt / Bill Upload

### Goal

Allow users to attach receipts or bills to transactions and optionally create transaction drafts from uploaded files.

### MVP Level 1 - Attachment Only

Flow:

```text
User creates transaction
  -> uploads receipt image/pdf
  -> file is linked to transaction
```

Database:

```text
transaction_attachments
```

Fields:

```text
id
transaction_id
user_id
file_name
content_type
file_size
storage_path
created_at
```

API:

```text
POST   /api/transactions/{id}/attachments
GET    /api/transactions/{id}/attachments
GET    /api/attachments/{id}
DELETE /api/attachments/{id}
```

Storage:

```text
uploads/receipts/
```

Use a Docker volume for local/EC2 persistence.

### MVP Level 2 - Receipt Draft

Flow:

```text
User uploads receipt first
  -> app stores file
  -> user fills amount/date/category
  -> app creates transaction linked to receipt
```

API:

```text
POST /api/receipts/upload
POST /api/receipts/{id}/create-transaction
```

### Future Level 3 - OCR / AI Extraction

Design optional interface:

```text
ReceiptExtractor
  |-- ManualReceiptExtractor
  |-- OcrReceiptExtractor
  |-- AiReceiptExtractor
```

Do not make OCR required for the MVP.

### Acceptance Criteria

- User can upload receipt image/PDF.
- Attachment is scoped to transaction owner.
- User cannot access another user's receipt.
- Files persist across container restart.
- Receipt-created transactions use `source = RECEIPT_UPLOAD`.

---

## 10. Phase 7 - Recurring Transactions

### Goal

Automatically create repeated transactions such as rent, salary, subscriptions, and insurance.

### Database

Create:

```text
recurring_transactions
```

Fields:

```text
id
user_id
template_id
frequency
next_run_date
last_run_date
active
created_at
updated_at
```

Frequency:

```text
DAILY
WEEKLY
MONTHLY
YEARLY
```

### Backend Job

Use scheduled job:

```java
@Scheduled(cron = "0 5 0 * * *")
```

Each run:

```text
find active recurring rules where next_run_date <= today
create transaction from template
set source = RECURRING
set source_reference = RECURRING_<recurringId>_<date>
advance next_run_date
```

### Duplicate Protection

Before creating:

```text
check source_reference exists for same user
```

This prevents duplicate transactions if the scheduler runs twice.

### Acceptance Criteria

- User can create recurring rules from templates.
- Scheduler creates due transactions.
- Duplicate scheduler runs do not duplicate transactions.
- User can pause/resume recurring rules.

---

## 11. Optional Future - Provider Abstraction

After the assisted-input features are stable, introduce a provider abstraction.

```text
TransactionInputProvider
  |-- PlaidProvider
  |-- CsvImportProvider
  |-- DemoBankProvider
  |-- ReceiptProvider
  |-- NaturalLanguageProvider
```

This makes Plaid one provider among many, not the center of the app.

Recommended config:

```text
PLAID_ENABLED=false
DEMO_BANK_ENABLED=true
CSV_IMPORT_ENABLED=true
NATURAL_LANGUAGE_ENABLED=true
RECEIPT_UPLOAD_ENABLED=true
```

---

## 12. Testing Plan

### Backend Unit Tests

- CSV parser parses valid rows.
- CSV parser reports invalid rows.
- Duplicate hash generation is stable.
- Category rules match descriptions correctly.
- English parser handles amount/date/category examples.
- Quick template creates correct transaction.
- Recurring scheduler creates due transaction once.
- Attachment security blocks cross-user access.

### Integration Tests

- CSV preview then commit imports transactions.
- Natural language parse then confirm creates transaction.
- Receipt upload links file to transaction.
- Recurring job creates transactions from templates.

### Frontend Tests / Manual QA

- Upload CSV and map columns.
- Confirm import result.
- Create category rule and verify it applies.
- Parse English input.
- Create transaction from quick template.
- Upload receipt.
- Create recurring transaction.

---

## 13. Recommended MVP Cut

If time is limited, implement this first:

```text
1. Transaction source fields
2. CSV import preview + commit
3. Category rules
4. Natural language input, English, rule-based
```

Then add:

```text
5. Quick add templates
6. Receipt upload
7. Recurring transactions
```

This order gives the biggest user value while avoiding dependency on third-party bank APIs.
