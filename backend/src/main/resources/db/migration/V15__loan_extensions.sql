-- ═══════════════════════════════════════════════════════════════════
-- V15__loan_extensions.sql — Guarantors, Collaterals, Rescheduling,
--   Re-aging, Re-amortization, Loan Transaction Processing Strategies
-- ═══════════════════════════════════════════════════════════════════

-- ── guarantors ───────────────────────────────────────────────────────
CREATE TABLE guarantors (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            REFERENCES tenants(id),
    loan_id                 UUID            NOT NULL REFERENCES loans(id),
    client_relation_type    VARCHAR(30)     NOT NULL,
    -- CUSTOMER | EXTERNAL
    customer_id             UUID            REFERENCES customers(id),
    -- Non-null for CUSTOMER guarantors
    firstname               VARCHAR(100),
    lastname                VARCHAR(100),
    dob                     DATE,
    address_line_1          VARCHAR(200),
    address_line_2          VARCHAR(200),
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    country                 VARCHAR(100),
    zip                     VARCHAR(20),
    mobile_no               VARCHAR(30),
    -- Guarantor funds (on-hold savings)
    savings_account_id      UUID            REFERENCES accounts(id),
    amount                  NUMERIC(19,4),
    amount_released         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_guarantors_loan    ON guarantors(loan_id);
CREATE INDEX idx_guarantors_client  ON guarantors(customer_id);
CREATE INDEX idx_guarantors_tenant  ON guarantors(tenant_id);

-- ── collaterals ───────────────────────────────────────────────────────
CREATE TABLE collaterals (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    loan_id             UUID            NOT NULL REFERENCES loans(id),
    collateral_type_id  UUID,
    -- References code_values where code = 'LoanCollateralType'
    description         VARCHAR(500),
    value               NUMERIC(19,4)   NOT NULL,
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_collaterals_loan   ON collaterals(loan_id);
CREATE INDEX idx_collaterals_tenant ON collaterals(tenant_id);

-- ── loan_reschedule_requests ─────────────────────────────────────────
-- Mifos rescheduleloans pattern — modify term/rate/grace period
CREATE TABLE loan_reschedule_requests (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    loan_id                     UUID            NOT NULL REFERENCES loans(id),
    status                      VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    -- SUBMITTED | APPROVED | REJECTED | DELETED
    reschedule_from_installment INT             NOT NULL,
    reschedule_from_date        DATE            NOT NULL,
    graceOnPrincipal            INT             NOT NULL DEFAULT 0,
    graceOnInterest             INT             NOT NULL DEFAULT 0,
    extraTerms                  INT             NOT NULL DEFAULT 0,
    new_interest_rate           NUMERIC(8,4),
    reschedule_reason_id        UUID,
    reschedule_reason_comment   TEXT,
    created_on_date             DATE            NOT NULL DEFAULT CURRENT_DATE,
    submitted_on_date           DATE            NOT NULL DEFAULT CURRENT_DATE,
    submitted_by_user_id        UUID,
    approved_on_date            DATE,
    approved_by_user_id         UUID,
    rejected_on_date            DATE,
    rejected_by_user_id         UUID,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_reschedule_loan    ON loan_reschedule_requests(loan_id);
CREATE INDEX idx_reschedule_status  ON loan_reschedule_requests(status);
CREATE INDEX idx_reschedule_tenant  ON loan_reschedule_requests(tenant_id);

-- ── loan_reaging_requests ────────────────────────────────────────────
-- Fineract 1.14.0 — re-aging moves overdue installments to the future
CREATE TABLE loan_reaging_requests (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    loan_id                     UUID            NOT NULL REFERENCES loans(id),
    frequency_type              VARCHAR(20)     NOT NULL DEFAULT 'MONTHS',
    -- DAYS | WEEKS | MONTHS
    frequency_number            INT             NOT NULL DEFAULT 1,
    number_of_installments      INT             NOT NULL,
    start_date                  DATE            NOT NULL,
    is_equal_amortization       BOOLEAN         NOT NULL DEFAULT FALSE,
    external_id                 VARCHAR(100),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_reaging_loan   ON loan_reaging_requests(loan_id);
CREATE INDEX idx_reaging_tenant ON loan_reaging_requests(tenant_id);

-- ── loan_reamortization_requests ─────────────────────────────────────
-- Fineract 1.14.0 — re-amortizes remaining installments from a date
CREATE TABLE loan_reamortization_requests (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            REFERENCES tenants(id),
    loan_id                     UUID            NOT NULL REFERENCES loans(id),
    external_id                 VARCHAR(100),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_reamortization_loan   ON loan_reamortization_requests(loan_id);
CREATE INDEX idx_reamortization_tenant ON loan_reamortization_requests(tenant_id);

-- ── transaction_processing_strategies ────────────────────────────────
-- Determines how repayments are allocated across principal/interest/fees
CREATE TABLE transaction_processing_strategies (
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
