-- =============================================
-- V6: Replace GoCardless with Plaid
-- =============================================

-- 1. Create plaid_items table (1 per bank login/Item)
CREATE TABLE plaid_items (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id          VARCHAR(255) NOT NULL UNIQUE,
    access_token     TEXT NOT NULL,
    sync_cursor      TEXT,
    institution_id   VARCHAR(100),
    institution_name VARCHAR(255),
    status           VARCHAR(20) DEFAULT 'ACTIVE',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_plaid_items_user ON plaid_items(user_id);

-- 2. Drop GoCardless columns from bank_configs
ALTER TABLE bank_configs DROP COLUMN IF EXISTS requisition_id;
ALTER TABLE bank_configs DROP COLUMN IF EXISTS gocardless_account_id;
ALTER TABLE bank_configs DROP COLUMN IF EXISTS access_expires_at;

-- 3. Add Plaid columns to bank_configs (1 per account)
ALTER TABLE bank_configs ADD COLUMN plaid_item_id BIGINT REFERENCES plaid_items(id);
ALTER TABLE bank_configs ADD COLUMN plaid_account_id VARCHAR(255);
CREATE INDEX idx_bank_configs_plaid_item ON bank_configs(plaid_item_id);
CREATE INDEX idx_bank_configs_plaid_account ON bank_configs(plaid_account_id);

-- 4. Soft-delete support for transactions (Bug #3 fix)
ALTER TABLE transactions ADD COLUMN is_deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN deleted_at TIMESTAMP;

-- 5. Enforce idempotency constraint (Bug #2 fix)
--    Do NOT assume this constraint exists from V1 — explicitly ensure it.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_bank_transaction_id_bank_config_id_key;
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_bank_tx_id
    UNIQUE (bank_transaction_id, bank_config_id);

-- 6. Mark all existing bank configs as expired
UPDATE bank_configs SET status = 'EXPIRED' WHERE status = 'LINKED';

-- 7. Clean up sync_logs (remove date-range columns)
ALTER TABLE sync_logs DROP COLUMN IF EXISTS date_from;
ALTER TABLE sync_logs DROP COLUMN IF EXISTS date_to;
