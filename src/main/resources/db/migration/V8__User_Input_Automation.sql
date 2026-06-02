ALTER TABLE transactions
    ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'MANUAL_FORM',
    ADD COLUMN source_reference VARCHAR(255),
    ADD COLUMN import_batch_id UUID,
    ADD COLUMN original_input TEXT,
    ADD COLUMN parse_confidence NUMERIC(5, 2);

CREATE INDEX idx_transactions_user_source_reference
    ON transactions (user_id, source, source_reference);

CREATE TABLE import_batches (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    duplicate_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE category_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keyword VARCHAR(255) NOT NULL,
    match_type VARCHAR(50) NOT NULL,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    transaction_type VARCHAR(10),
    priority INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_category_rules_user_active_priority
    ON category_rules (user_id, active, priority);
