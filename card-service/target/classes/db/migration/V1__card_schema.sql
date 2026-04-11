-- ============================================================
-- CBA Card Service — V1 Core Schema
-- ============================================================
-- All monetary amounts: NUMERIC(19,4)
-- All PKs: UUID (gen_random_uuid())
-- Optimistic locking: version BIGINT DEFAULT 0
-- Timestamps: TIMESTAMPTZ DEFAULT now()
-- ============================================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Card Products ─────────────────────────────────────────────────────────────
CREATE TABLE card_products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    card_type           VARCHAR(20)  NOT NULL CHECK (card_type IN ('DEBIT','PREPAID','CREDIT')),
    bin_range_start     VARCHAR(8)   NOT NULL,
    bin_range_end       VARCHAR(8)   NOT NULL,
    default_daily_limit NUMERIC(19,4) NOT NULL DEFAULT 500000, -- in minor units (cents)
    features            JSONB        NOT NULL DEFAULT '{}',
    active              BOOLEAN      NOT NULL DEFAULT true,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Cards ─────────────────────────────────────────────────────────────────────
CREATE TABLE cards (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pan_encrypted       TEXT         NOT NULL,           -- Jasypt AES-256 encrypted full PAN
    pan_hash            VARCHAR(64)  NOT NULL UNIQUE,    -- HMAC-SHA256(PAN, hmac_key) for O(1) lookup
    pan_prefix          VARCHAR(8)   NOT NULL,           -- First 8 digits (unencrypted — BIN routing)
    pan_suffix          VARCHAR(4)   NOT NULL,           -- Last 4 digits (unencrypted — display)
    expiry_date         VARCHAR(4)   NOT NULL,           -- YYMM
    cvv_encrypted       TEXT         NOT NULL,           -- Jasypt AES-256 encrypted CVV
    card_sequence_no    SMALLINT     NOT NULL DEFAULT 1,
    card_type           VARCHAR(20)  NOT NULL CHECK (card_type IN ('DEBIT','PREPAID','CREDIT')),
    status              VARCHAR(30)  NOT NULL DEFAULT 'ISSUED',
    virtual_flag        BOOLEAN      NOT NULL DEFAULT false,
    customer_id         UUID         NOT NULL,           -- FK to backend customers table
    linked_entity_id    UUID,                            -- account_id (debit/prepaid) or loan_id (credit)
    product_id          UUID         NOT NULL REFERENCES card_products(id),
    pin_retry_count     SMALLINT     NOT NULL DEFAULT 0,
    pin_set             BOOLEAN      NOT NULL DEFAULT false,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_cards_customer_id  ON cards(customer_id);
CREATE INDEX idx_cards_pan_prefix   ON cards(pan_prefix);
CREATE INDEX idx_cards_status       ON cards(status);
CREATE INDEX idx_cards_product_id   ON cards(product_id);

-- ── Physical Card Orders ──────────────────────────────────────────────────────
CREATE TABLE physical_card_orders (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id                 UUID         NOT NULL REFERENCES cards(id),
    status                  VARCHAR(30)  NOT NULL DEFAULT 'ORDERED',
    activation_code         VARCHAR(20),
    card_bureau_ref         VARCHAR(50),
    production_request_date DATE,
    dispatch_date           DATE,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pco_card_id ON physical_card_orders(card_id);

-- ── Card Limits ───────────────────────────────────────────────────────────────
CREATE TABLE card_limits (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id                 UUID          NOT NULL UNIQUE REFERENCES cards(id),
    daily_purchase_limit    NUMERIC(19,4) NOT NULL DEFAULT 500000,  -- minor units
    daily_withdrawal_limit  NUMERIC(19,4) NOT NULL DEFAULT 200000,
    per_txn_limit           NUMERIC(19,4) NOT NULL DEFAULT 100000,
    monthly_limit           NUMERIC(19,4) NOT NULL DEFAULT 2000000,
    currency_code           VARCHAR(3)    NOT NULL DEFAULT 'USD',
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ── Prepaid Wallets ───────────────────────────────────────────────────────────
CREATE TABLE prepaid_wallets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id       UUID          NOT NULL UNIQUE REFERENCES cards(id),
    customer_id   UUID          NOT NULL,
    balance       NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3)    NOT NULL DEFAULT 'USD',
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ── BIN Ranges ────────────────────────────────────────────────────────────────
-- Supports both 6-digit (legacy) and 8-digit BINs (EMV 2019 mandate)
CREATE TABLE bin_ranges (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bin_start     VARCHAR(8)   NOT NULL,
    bin_end       VARCHAR(8)   NOT NULL,
    scheme        VARCHAR(20)  NOT NULL CHECK (scheme IN ('VISA','MASTERCARD','VERVE','AFRIGO','UNION_PAY','UNKNOWN')),
    product_type  VARCHAR(50),
    card_type     VARCHAR(20)  CHECK (card_type IN ('DEBIT','PREPAID','CREDIT')),
    country_code  VARCHAR(3),  -- ISO 3166 alpha-3
    currency_code VARCHAR(3),  -- ISO 4217 numeric
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_bin_ranges_start ON bin_ranges(bin_start);
CREATE INDEX idx_bin_ranges_end   ON bin_ranges(bin_end);
CREATE INDEX idx_bin_ranges_scheme ON bin_ranges(scheme) WHERE active = true;

-- ── Authorization Log ─────────────────────────────────────────────────────────
CREATE TABLE authorization_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id         UUID         REFERENCES cards(id),
    stan            VARCHAR(6)   NOT NULL,
    rrn             VARCHAR(12),
    mti             VARCHAR(4)   NOT NULL,
    processing_code VARCHAR(6),
    amount          NUMERIC(19,4),
    currency_code   VARCHAR(3),
    response_code   VARCHAR(2)   NOT NULL,
    auth_code       VARCHAR(6),
    entry_mode      VARCHAR(20),   -- SWIPE, CHIP, CONTACTLESS, CNP
    terminal_id     VARCHAR(8),
    merchant_id     VARCHAR(15),
    merchant_name   VARCHAR(40),
    mcc             VARCHAR(4),
    fraud_score     INTEGER,
    decision        VARCHAR(20),   -- APPROVE, STEP_UP, DECLINE
    is_financial    BOOLEAN      NOT NULL DEFAULT false,
    scheme          VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_log_card_id    ON authorization_log(card_id);
CREATE INDEX idx_auth_log_stan       ON authorization_log(stan);
CREATE INDEX idx_auth_log_rrn        ON authorization_log(rrn);
CREATE INDEX idx_auth_log_created_at ON authorization_log(created_at);

-- ── Fraud Rules ───────────────────────────────────────────────────────────────
CREATE TABLE fraud_rules (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id   VARCHAR(30)   NOT NULL UNIQUE,
    weight    INTEGER       NOT NULL DEFAULT 50 CHECK (weight BETWEEN 0 AND 100),
    enabled   BOOLEAN       NOT NULL DEFAULT true,
    params    JSONB         NOT NULL DEFAULT '{}',  -- e.g. velocity_count, velocity_window_minutes
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Fraud Score Log ───────────────────────────────────────────────────────────
CREATE TABLE fraud_score_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authorization_log_id UUID         NOT NULL REFERENCES authorization_log(id),
    rule_id              VARCHAR(30)  NOT NULL,
    score_contribution   INTEGER      NOT NULL DEFAULT 0,
    triggered            BOOLEAN      NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_score_auth ON fraud_score_log(authorization_log_id);

-- ── Token Vault ───────────────────────────────────────────────────────────────
CREATE TABLE token_vault (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dpan_encrypted  TEXT        NOT NULL,           -- Jasypt encrypted DPAN
    dpan_hash       VARCHAR(64) NOT NULL UNIQUE,    -- HMAC-SHA256(DPAN) for lookup
    pan_hash        VARCHAR(64) NOT NULL,           -- links back to cards.pan_hash
    token_ref       VARCHAR(36) NOT NULL UNIQUE,    -- UUID used as external reference
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
    customer_id     UUID,
    card_id         UUID        REFERENCES cards(id),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_token_vault_card_id ON token_vault(card_id);
CREATE INDEX idx_token_vault_status  ON token_vault(status);

-- ── Settlement Batches ────────────────────────────────────────────────────────
CREATE TABLE settlement_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_ref       VARCHAR(36)   NOT NULL UNIQUE,
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','SETTLED','FAILED')),
    settlement_date DATE          NOT NULL,
    total_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    item_count      INTEGER       NOT NULL DEFAULT 0,
    opened_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_settlement_batches_date   ON settlement_batches(settlement_date);
CREATE INDEX idx_settlement_batches_status ON settlement_batches(status);

-- ── Settlement Items ──────────────────────────────────────────────────────────
CREATE TABLE settlement_items (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id             UUID          NOT NULL REFERENCES settlement_batches(id),
    authorization_log_id UUID          REFERENCES authorization_log(id),
    amount               NUMERIC(19,4) NOT NULL,
    currency_code        VARCHAR(3)    NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_settlement_items_batch ON settlement_items(batch_id);

-- ── Card Disputes ─────────────────────────────────────────────────────────────
CREATE TABLE card_disputes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id          UUID          NOT NULL REFERENCES cards(id),
    transaction_ref  VARCHAR(12)   NOT NULL,  -- original RRN (DE37)
    dispute_reason   VARCHAR(30)   NOT NULL,
    status           VARCHAR(30)   NOT NULL DEFAULT 'RAISED',
    raised_by        UUID          NOT NULL,  -- customer_id
    resolved_by      UUID,                    -- staff user_id
    original_amount  NUMERIC(19,4) NOT NULL,
    resolution_notes TEXT,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_disputes_card_id ON card_disputes(card_id);
CREATE INDEX idx_disputes_status  ON card_disputes(status);

-- ── API Keys (for Card API — BaaS M2M) ───────────────────────────────────────
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100)  NOT NULL,
    key_hash     VARCHAR(128)  NOT NULL UNIQUE,  -- PBKDF2WithHmacSHA256
    created_by   UUID          NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT true,
    scopes       JSONB         NOT NULL DEFAULT '[]',
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ── Webhooks ──────────────────────────────────────────────────────────────────
CREATE TABLE webhooks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    callback_url TEXT         NOT NULL,
    events       JSONB        NOT NULL DEFAULT '[]',
    secret_hash  VARCHAR(128) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Webhook Delivery Log ──────────────────────────────────────────────────────
CREATE TABLE webhook_delivery_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id      UUID         NOT NULL REFERENCES webhooks(id),
    event_type      VARCHAR(50)  NOT NULL,
    delivery_uuid   VARCHAR(36)  NOT NULL UNIQUE,
    payload         JSONB        NOT NULL,
    http_status     INTEGER,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DELIVERED','FAILED')),
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_delivery_webhook_id ON webhook_delivery_log(webhook_id);
CREATE INDEX idx_webhook_delivery_status     ON webhook_delivery_log(status) WHERE status IN ('PENDING','FAILED');
