-- ═══════════════════════════════════════════════════════════════════
-- V13__deposits_extended.sql — Fixed Deposit & Recurring Deposit Accounts
-- Mirrors Mifos fixeddepositaccounts / recurringdepositaccounts pattern
-- ═══════════════════════════════════════════════════════════════════

-- ── fixed_deposit_products ───────────────────────────────────────────
CREATE TABLE fixed_deposit_products (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    name                        VARCHAR(100)    NOT NULL,
    short_name                  VARCHAR(20)     NOT NULL,
    description                 TEXT,
    currency_code               CHAR(3)         NOT NULL DEFAULT 'USD',
    min_deposit_amount          NUMERIC(19,4)   NOT NULL,
    max_deposit_amount          NUMERIC(19,4),
    nominal_annual_interest_rate NUMERIC(8,4)   NOT NULL,
    interest_compounding_period VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    interest_posting_period     VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',
    interest_calculation_type   VARCHAR(30)     NOT NULL DEFAULT 'DAILY_BALANCE',
    interest_calculation_days_in_year INT       NOT NULL DEFAULT 365,
    min_deposit_term            INT             NOT NULL,
    max_deposit_term            INT,
    min_deposit_term_type       VARCHAR(20)     NOT NULL DEFAULT 'MONTHS',
    max_deposit_term_type       VARCHAR(20)     NOT NULL DEFAULT 'MONTHS',
    in_multiples_of_deposit_term     INT,
    in_multiples_of_deposit_term_type VARCHAR(20),
    pre_closure_penal_applicable BOOLEAN        NOT NULL DEFAULT FALSE,
    pre_closure_penal_interest  NUMERIC(8,4)    NOT NULL DEFAULT 0,
    pre_closure_penal_interest_on VARCHAR(20)   NOT NULL DEFAULT 'WHOLE_TERM',
    withhold_tax                BOOLEAN         NOT NULL DEFAULT FALSE,
    active                      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_fd_products_tenant ON fixed_deposit_products(tenant_id);

-- ── fixed_deposit_accounts ───────────────────────────────────────────
CREATE TABLE fixed_deposit_accounts (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    account_number              VARCHAR(50)     UNIQUE NOT NULL,
    external_id                 VARCHAR(100),
    customer_id                 UUID            NOT NULL REFERENCES customers(id),
    product_id                  UUID            NOT NULL REFERENCES fixed_deposit_products(id),
    field_officer_id            UUID            REFERENCES staff(id),
    status                      VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    -- SUBMITTED | APPROVED | ACTIVE | MATURED | PREMATURE_CLOSURE | CLOSED | REJECTED | WITHDRAWN
    currency_code               CHAR(3)         NOT NULL DEFAULT 'USD',
    deposit_amount              NUMERIC(19,4)   NOT NULL,
    deposit_period              INT             NOT NULL,
    deposit_period_type         VARCHAR(20)     NOT NULL DEFAULT 'MONTHS',
    expected_first_deposit_on_date DATE,
    maturity_date               DATE,
    maturity_amount             NUMERIC(19,4),
    maturity_instruction        VARCHAR(30)     NOT NULL DEFAULT 'HOLD_AMOUNT_IN_SAVINGS',
    -- HOLD_AMOUNT_IN_SAVINGS | TRANSFER_TO_SAVINGS
    on_account_closure_savings_account_id UUID  REFERENCES accounts(id),
    nominated_annual_interest_rate NUMERIC(8,4) NOT NULL,
    interest_compounding_period VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    interest_posting_period     VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',
    interest_calculation_type   VARCHAR(30)     NOT NULL DEFAULT 'DAILY_BALANCE',
    interest_earned             NUMERIC(19,4)   NOT NULL DEFAULT 0,
    pre_closure_penal_applicable BOOLEAN        NOT NULL DEFAULT FALSE,
    pre_closure_penal_interest  NUMERIC(8,4)    NOT NULL DEFAULT 0,
    submitted_on_date           DATE            NOT NULL DEFAULT CURRENT_DATE,
    approved_on_date            DATE,
    activated_on_date           DATE,
    rejected_on_date            DATE,
    withdrawn_on_date           DATE,
    closed_on_date              DATE,
    locale                      VARCHAR(10)     NOT NULL DEFAULT 'en',
    date_format                 VARCHAR(20)     NOT NULL DEFAULT 'yyyy-MM-dd',
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_fd_accounts_customer ON fixed_deposit_accounts(customer_id);
CREATE INDEX idx_fd_accounts_status   ON fixed_deposit_accounts(status);
CREATE INDEX idx_fd_accounts_tenant   ON fixed_deposit_accounts(tenant_id);

-- ── recurring_deposit_products ───────────────────────────────────────
CREATE TABLE recurring_deposit_products (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            REFERENCES tenants(id),
    name                            VARCHAR(100)    NOT NULL,
    short_name                      VARCHAR(20)     NOT NULL,
    description                     TEXT,
    currency_code                   CHAR(3)         NOT NULL DEFAULT 'USD',
    min_deposit_amount              NUMERIC(19,4)   NOT NULL,
    max_deposit_amount              NUMERIC(19,4),
    nominal_annual_interest_rate    NUMERIC(8,4)    NOT NULL,
    interest_compounding_period     VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    interest_posting_period         VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',
    interest_calculation_type       VARCHAR(30)     NOT NULL DEFAULT 'DAILY_BALANCE',
    interest_calculation_days_in_year INT           NOT NULL DEFAULT 365,
    recurring_deposit_amount        NUMERIC(19,4),
    recurring_deposit_frequency     INT             NOT NULL DEFAULT 1,
    recurring_deposit_frequency_type VARCHAR(20)    NOT NULL DEFAULT 'MONTHS',
    is_mandatory_deposit            BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_withdrawal                BOOLEAN         NOT NULL DEFAULT FALSE,
    adjust_advance_towards_future_payments BOOLEAN  NOT NULL DEFAULT FALSE,
    min_deposit_term                INT,
    max_deposit_term                INT,
    pre_closure_penal_applicable    BOOLEAN         NOT NULL DEFAULT FALSE,
    pre_closure_penal_interest      NUMERIC(8,4)    NOT NULL DEFAULT 0,
    withhold_tax                    BOOLEAN         NOT NULL DEFAULT FALSE,
    active                          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_rd_products_tenant ON recurring_deposit_products(tenant_id);

-- ── recurring_deposit_accounts ───────────────────────────────────────
CREATE TABLE recurring_deposit_accounts (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID            REFERENCES tenants(id),
    account_number                  VARCHAR(50)     UNIQUE NOT NULL,
    external_id                     VARCHAR(100),
    customer_id                     UUID            NOT NULL REFERENCES customers(id),
    product_id                      UUID            NOT NULL REFERENCES recurring_deposit_products(id),
    field_officer_id                UUID            REFERENCES staff(id),
    status                          VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    currency_code                   CHAR(3)         NOT NULL DEFAULT 'USD',
    deposit_amount                  NUMERIC(19,4),
    is_mandatory_deposit            BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_withdrawal                BOOLEAN         NOT NULL DEFAULT FALSE,
    recurring_deposit_amount        NUMERIC(19,4)   NOT NULL,
    recurring_deposit_frequency     INT             NOT NULL DEFAULT 1,
    recurring_deposit_frequency_type VARCHAR(20)    NOT NULL DEFAULT 'MONTHS',
    expected_first_deposit_on_date  DATE,
    maturity_date                   DATE,
    maturity_amount                 NUMERIC(19,4),
    nominated_annual_interest_rate  NUMERIC(8,4)    NOT NULL,
    interest_compounding_period     VARCHAR(20)     NOT NULL DEFAULT 'DAILY',
    interest_posting_period         VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',
    interest_calculation_type       VARCHAR(30)     NOT NULL DEFAULT 'DAILY_BALANCE',
    interest_earned                 NUMERIC(19,4)   NOT NULL DEFAULT 0,
    balance                         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    pre_closure_penal_applicable    BOOLEAN         NOT NULL DEFAULT FALSE,
    pre_closure_penal_interest      NUMERIC(8,4)    NOT NULL DEFAULT 0,
    submitted_on_date               DATE            NOT NULL DEFAULT CURRENT_DATE,
    approved_on_date                DATE,
    activated_on_date               DATE,
    rejected_on_date                DATE,
    withdrawn_on_date               DATE,
    closed_on_date                  DATE,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_rd_accounts_customer ON recurring_deposit_accounts(customer_id);
CREATE INDEX idx_rd_accounts_status   ON recurring_deposit_accounts(status);
CREATE INDEX idx_rd_accounts_tenant   ON recurring_deposit_accounts(tenant_id);

-- ── deposit_account_transactions ─────────────────────────────────────
-- Shared for both FD and RD; exactly one of fd_account_id / rd_account_id is set
CREATE TABLE deposit_account_transactions (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    fd_account_id               UUID            REFERENCES fixed_deposit_accounts(id),
    rd_account_id               UUID            REFERENCES recurring_deposit_accounts(id),
    transaction_type            VARCHAR(30)     NOT NULL,
    -- DEPOSIT | WITHDRAWAL | INTEREST_POSTING | OVERHEAD_FEE | WITHHOLDING_TAX |
    -- WAIVE_CHARGES | PRE_CLOSURE | CLOSURE | MATURITY | REINSTATE
    currency_code               CHAR(3)         NOT NULL DEFAULT 'USD',
    amount                      NUMERIC(19,4)   NOT NULL,
    running_balance             NUMERIC(19,4)   NOT NULL DEFAULT 0,
    transaction_date            DATE            NOT NULL DEFAULT CURRENT_DATE,
    submitted_on_date           DATE            NOT NULL DEFAULT CURRENT_DATE,
    reversed                    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dep_txn_fd  ON deposit_account_transactions(fd_account_id);
CREATE INDEX idx_dep_txn_rd  ON deposit_account_transactions(rd_account_id);
CREATE INDEX idx_dep_txn_tenant ON deposit_account_transactions(tenant_id);
