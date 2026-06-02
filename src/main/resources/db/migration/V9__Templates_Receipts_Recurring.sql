CREATE TABLE transaction_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    amount NUMERIC(19, 2) NOT NULL,
    type VARCHAR(10) NOT NULL,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    currency VARCHAR(10) NOT NULL DEFAULT 'GBP',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_templates_user_active
    ON transaction_templates (user_id, active);

CREATE TABLE transaction_attachments (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_attachments_transaction
    ON transaction_attachments (transaction_id);

CREATE TABLE recurring_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id BIGINT NOT NULL REFERENCES transaction_templates(id) ON DELETE CASCADE,
    frequency VARCHAR(20) NOT NULL,
    next_run_date DATE NOT NULL,
    last_run_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recurring_transactions_due
    ON recurring_transactions (active, next_run_date);
