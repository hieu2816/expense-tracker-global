-- User table
CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255)        NOT NULL,
    full_name     VARCHAR(100),
    is_active     BOOLEAN   DEFAULT TRUE,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bank_configs
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    bank_name      VARCHAR(50)  NOT NULL,
    account_number VARCHAR(50)  NOT NULL,
    casso_api_key  VARCHAR(255) NOT NULL, -- API key
    secure_token   VARCHAR(255),          -- token for webhook
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- if user is deleted, delete config too
    CONSTRAINT fk_bankconfigs_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    -- a user can not add 1 bank account twice
    UNIQUE (user_id, account_number)
);

CREATE TABLE categories
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT, -- null = default category of system
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('IN', 'OUT')),
    icon       VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE transactions
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT         NOT NULL,
    bank_config_id      BIGINT,                  -- null for cash
    category_id         BIGINT,

    amount              DECIMAL(19, 2) NOT NULL,
    description         TEXT,
    transaction_date    TIMESTAMP      NOT NULL,
    type                VARCHAR(20)    NOT NULL CHECK (type IN ('IN', 'OUT')),

    bank_transaction_id VARCHAR(255),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transactions_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_bankconfigs FOREIGN KEY (bank_config_id) REFERENCES bank_configs (id),
    CONSTRAINT fk_transactions_categories FOREIGN KEY (category_id) REFERENCES categories (id),

    UNIQUE (bank_transaction_id, bank_config_id) -- avoid 2 banks that have the same transaction id
);

CREATE TABLE webhook_logs
(
    id            BIGSERIAL PRIMARY KEY,
    payload       JSONB NOT NULL,
    status        VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    received_at   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trans_user_date ON transactions (user_id, transaction_date);
CREATE INDEX idx_bank_acc ON bank_configs (account_number);