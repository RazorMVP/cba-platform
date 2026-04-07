-- ═══════════════════════════════════════════════════════════════════
-- V14__shares.sql — Share Products & Share Accounts (GSIM)
-- Mirrors Mifos shareproducts / shareaccounts / gsim pattern
-- ═══════════════════════════════════════════════════════════════════

-- ── share_products ───────────────────────────────────────────────────
CREATE TABLE share_products (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    name                        VARCHAR(100)    NOT NULL,
    short_name                  VARCHAR(10)     NOT NULL UNIQUE,
    description                 TEXT,
    external_id                 VARCHAR(100),
    currency_code               CHAR(3)         NOT NULL DEFAULT 'USD',
    total_shares                BIGINT          NOT NULL,
    shares_issued               BIGINT          NOT NULL DEFAULT 0,
    unit_price                  NUMERIC(19,4)   NOT NULL,
    nominal_shares              BIGINT          NOT NULL DEFAULT 1,
    min_shares                  BIGINT          NOT NULL DEFAULT 1,
    max_shares                  BIGINT,
    capital_amount              NUMERIC(19,4),
    allow_dividends_for_inactive_clients BOOLEAN NOT NULL DEFAULT FALSE,
    dividend_active             BOOLEAN         NOT NULL DEFAULT FALSE,
    active                      BOOLEAN         NOT NULL DEFAULT TRUE,
    start_date                  DATE,
    close_date                  DATE,
    minimum_active_period_for_dividends INT,
    minimum_active_period_type  VARCHAR(20),
    lock_in_period              INT,
    lock_in_period_type         VARCHAR(20),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_share_products_tenant ON share_products(tenant_id);

-- ── share_accounts ───────────────────────────────────────────────────
CREATE TABLE share_accounts (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    account_number              VARCHAR(50)     UNIQUE NOT NULL,
    external_id                 VARCHAR(100),
    customer_id                 UUID            NOT NULL REFERENCES customers(id),
    product_id                  UUID            NOT NULL REFERENCES share_products(id),
    status                      VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    -- SUBMITTED | APPROVED | ACTIVE | CLOSED | REJECTED | WITHDRAWN
    currency_code               CHAR(3)         NOT NULL DEFAULT 'USD',
    requested_shares            BIGINT          NOT NULL,
    approved_shares             BIGINT          NOT NULL DEFAULT 0,
    pending_for_approval_shares BIGINT          NOT NULL DEFAULT 0,
    redeemed_shares             BIGINT          NOT NULL DEFAULT 0,
    unit_price                  NUMERIC(19,4)   NOT NULL,
    purchase_date               DATE,
    total_approved_shares_amount NUMERIC(19,4)  NOT NULL DEFAULT 0,
    total_redeemed_shares_amount NUMERIC(19,4)  NOT NULL DEFAULT 0,
    lock_in_period              INT,
    lock_in_period_type         VARCHAR(20),
    minimum_active_period_for_dividends INT,
    minimum_active_period_type  VARCHAR(20),
    allow_dividends_for_inactive_clients BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_on_date           DATE            NOT NULL DEFAULT CURRENT_DATE,
    approved_on_date            DATE,
    activated_on_date           DATE,
    rejected_on_date            DATE,
    closed_on_date              DATE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_share_accounts_customer ON share_accounts(customer_id);
CREATE INDEX idx_share_accounts_product  ON share_accounts(product_id);
CREATE INDEX idx_share_accounts_status   ON share_accounts(status);
CREATE INDEX idx_share_accounts_tenant   ON share_accounts(tenant_id);

-- ── share_account_transactions ───────────────────────────────────────
CREATE TABLE share_account_transactions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    share_account_id    UUID            NOT NULL REFERENCES share_accounts(id),
    transaction_type    VARCHAR(30)     NOT NULL,
    -- PURCHASE | REDEEM | DIVIDEND_PAYMENT | CHARGE_PAYMENT
    number_of_shares    BIGINT          NOT NULL,
    unit_price          NUMERIC(19,4)   NOT NULL,
    amount              NUMERIC(19,4)   NOT NULL,
    transaction_date    DATE            NOT NULL DEFAULT CURRENT_DATE,
    reversed            BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_share_txn_account ON share_account_transactions(share_account_id);

-- ── gsim_accounts ────────────────────────────────────────────────────
-- Group Savings Individual Monitoring — links a group to shared savings accounts
CREATE TABLE gsim_accounts (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    group_id            UUID            NOT NULL REFERENCES groups(id),
    account_number      VARCHAR(50)     UNIQUE NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    child_deposit_amount NUMERIC(19,4)  NOT NULL,
    applications        INT             NOT NULL DEFAULT 0,
    balance             NUMERIC(19,4)   NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_gsim_group  ON gsim_accounts(group_id);
CREATE INDEX idx_gsim_tenant ON gsim_accounts(tenant_id);

-- ── gsim_members ─────────────────────────────────────────────────────
CREATE TABLE gsim_members (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    gsim_account_id     UUID            NOT NULL REFERENCES gsim_accounts(id),
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    savings_account_id  UUID            REFERENCES accounts(id),
    deposit_amount      NUMERIC(19,4)   NOT NULL,
    UNIQUE (gsim_account_id, customer_id)
);
