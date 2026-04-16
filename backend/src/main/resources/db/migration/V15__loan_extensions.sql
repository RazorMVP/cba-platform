-- ═══════════════════════════════════════════════════════════════════
-- V15__loan_extensions.sql — Guarantors, Collaterals, Rescheduling,
--   Re-aging, Re-amortization, Loan Transaction Processing Strategies
-- ═══════════════════════════════════════════════════════════════════

-- ── guarantors ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS guarantors (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            REFERENCES tenants(id),
    loan_id                 UUID            NOT NULL REFERENCES loans(id),
    guarantor_type          VARCHAR(30)     NOT NULL DEFAULT 'EXTERNAL',
    -- EXISTING_CUSTOMER | EXTERNAL
    customer_id             UUID            REFERENCES customers(id),
    first_name              VARCHAR(100),
    last_name               VARCHAR(100),
    email                   VARCHAR(100),
    mobile_number           VARCHAR(20),
    address_line_1          VARCHAR(255),
    address_line_2          VARCHAR(255),
    city                    VARCHAR(100),
    country                 VARCHAR(100),
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_guarantors_loan    ON guarantors(loan_id);
CREATE INDEX idx_guarantors_client  ON guarantors(customer_id);
CREATE INDEX idx_guarantors_tenant  ON guarantors(tenant_id);

-- ── collaterals ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS collaterals (
    id                              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id                         UUID            NOT NULL REFERENCES loans(id),
    collateral_type_code_value_id   UUID,
    value                           NUMERIC(19,4)   NOT NULL,
    description                     TEXT,
    currency_code                   VARCHAR(3)      NOT NULL DEFAULT 'USD',
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_collaterals_loan   ON collaterals(loan_id);

-- ── loan_reschedule_requests ─────────────────────────────────────────
-- Mifos rescheduleloans pattern — modify term/rate/grace period
CREATE TABLE IF NOT EXISTS loan_reschedule_requests (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id                     UUID            NOT NULL REFERENCES loans(id),
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    reschedule_from_installment INT,
    adjust_repayment_date       DATE,
    grace_on_principal          INT,
    grace_on_interest           INT,
    extra_terms                 INT,
    new_interest_rate           NUMERIC(19,6),
    recalculate_interest        BOOLEAN         NOT NULL DEFAULT TRUE,
    comment                     TEXT,
    requested_on_date           DATE,
    approved_on_date            DATE,
    submitted_by_user_id        UUID,
    approved_by_user_id         UUID,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_reschedule_loan    ON loan_reschedule_requests(loan_id);
CREATE INDEX idx_reschedule_status  ON loan_reschedule_requests(status);

-- ── loan_reaging_requests ────────────────────────────────────────────
-- Fineract 1.14.0 — re-aging moves overdue installments to the future
CREATE TABLE IF NOT EXISTS loan_reaging_requests (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id                     UUID            NOT NULL REFERENCES loans(id),
    frequency_type              VARCHAR(20)     DEFAULT 'MONTHS',
    frequency_number            INT,
    number_of_installments      INT,
    start_date                  DATE,
    is_preview                  BOOLEAN         NOT NULL DEFAULT FALSE,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    requested_on_date           DATE,
    approved_on_date            DATE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_reaging_loan   ON loan_reaging_requests(loan_id);

-- ── loan_reamortization_requests ─────────────────────────────────────
-- Fineract 1.14.0 — re-amortizes remaining installments from a date
CREATE TABLE IF NOT EXISTS loan_reamortization_requests (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id                     UUID            NOT NULL REFERENCES loans(id),
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    requested_on_date           DATE,
    approved_on_date            DATE,
    comment                     TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_reamortization_loan   ON loan_reamortization_requests(loan_id);

-- ── transaction_processing_strategies ────────────────────────────────
-- Determines how repayments are allocated across principal/interest/fees
CREATE TABLE IF NOT EXISTS transaction_processing_strategies (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(100)    UNIQUE NOT NULL,
    name        VARCHAR(200)    NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

INSERT INTO transaction_processing_strategies (code, name) VALUES
    ('mifos-standard-strategy', 'Mifos style (broadly: Interest first, then Principal)'),
    ('heavensfamily-strategy', 'Heavens Family (same: Interest first, then Principal)'),
    ('creocore-strategy', 'CreoCore (same: Interest first, then Principal)'),
    ('rbi-india-strategy', 'RBI (India) (Interest first, then Principal)'),
    ('principal-interest-in-advance-strategy', 'Principal + Interest in advance (Jlg lending method, Internal)'),
    ('interest-principal-penalties-fees-order-strategy', 'Interest, Principal, Penalties, Fees Order'),
    ('early-repayment-strategy', 'Early repayment strategy');

-- Add transaction_processing_strategy_code to loans table
ALTER TABLE loans ADD COLUMN IF NOT EXISTS
    transaction_processing_strategy_code VARCHAR(100)
    REFERENCES transaction_processing_strategies(code)
    DEFAULT 'mifos-standard-strategy';
