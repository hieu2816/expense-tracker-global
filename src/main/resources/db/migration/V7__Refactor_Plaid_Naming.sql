-- ===============================================================================
-- V7: Chuẩn hoá Naming Convention từ GoCardless sang Plaid (Clean Rebuild)
-- ===============================================================================

-- 1. Cập nhật bảng plaid_items
ALTER TABLE plaid_items ADD COLUMN institution_logo VARCHAR(500);

-- 2. Chuẩn hoá bảng bank_configs -> bank_accounts
ALTER TABLE bank_configs RENAME TO bank_accounts;

-- Đổi tên và thêm các cột hiển thị theo chuẩn Plaid
ALTER TABLE bank_accounts RENAME COLUMN account_name TO name;
ALTER TABLE bank_accounts ADD COLUMN official_name VARCHAR(255);

-- Xoá iban và tạo cột mask, subtype mới hoàn toàn
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS iban;
ALTER TABLE bank_accounts ADD COLUMN mask VARCHAR(10);
ALTER TABLE bank_accounts ADD COLUMN subtype VARCHAR(50);

-- Dọn dẹp tàn dư của GoCardless (Chấp nhận drop vì đang clean rebuild)
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS institution_id;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS institution_name;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS institution_logo;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS link_reference;

-- [CẢI TIẾN]: Xoá index cũ (nếu tồn tại) và tạo index mới để chống gãy migration
DROP INDEX IF EXISTS idx_bank_configs_status;
DROP INDEX IF EXISTS idx_bank_configs_plaid_item;
DROP INDEX IF EXISTS idx_bank_configs_plaid_account;

CREATE INDEX idx_bank_accounts_status ON bank_accounts(status);
CREATE INDEX idx_bank_accounts_plaid_item ON bank_accounts(plaid_item_id);
CREATE INDEX idx_bank_accounts_plaid_account ON bank_accounts(plaid_account_id);

-- 3. Chuẩn hoá bảng transactions
ALTER TABLE transactions RENAME COLUMN bank_config_id TO bank_account_id;
ALTER TABLE transactions RENAME COLUMN bank_transaction_id TO plaid_transaction_id;

-- Cập nhật lại Unique Constraint (Idempotency Guard)
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS uq_transactions_bank_tx_id;
ALTER TABLE transactions ADD CONSTRAINT uq_transactions_plaid_tx_id UNIQUE (plaid_transaction_id, bank_account_id);

-- 4. Chuẩn hoá bảng sync_logs
TRUNCATE TABLE sync_logs;
ALTER TABLE sync_logs DROP COLUMN IF EXISTS bank_config_id;
ALTER TABLE sync_logs ADD COLUMN plaid_item_id BIGINT NOT NULL REFERENCES plaid_items(id) ON DELETE CASCADE;

-- Tạo index mới cho sync_logs bằng logic Drop/Create
DROP INDEX IF EXISTS idx_sync_logs_bank_config;
CREATE INDEX idx_sync_logs_plaid_item ON sync_logs(plaid_item_id);
