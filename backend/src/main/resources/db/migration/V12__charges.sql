-- ═══════════════════════════════════════════════════════════════════
-- V12__charges.sql — Charges Module (Loan + Client Charges)
-- Mirrors Mifos chargeDefinition → loanCharge / clientCharge pattern
-- ═══════════════════════════════════════════════════════════════════

-- ── charge_definitions ───────────────────────────────────────────────
-- Global charge catalogue (templates) managed by ADMIN
CREATE TABLE IF NOT EXISTS charge_definitions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    name                VARCHAR(100)    NOT NULL,
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    charge_applies_to   VARCHAR(20)     NOT NULL,
    -- LOAN | SAVINGS | CLIENT | SHARE
    charge_time_type    VARCHAR(30)     NOT NULL,
    -- DISBURSEMENT | SPECIFIED_DUE_DATE | INSTALLMENT_FEE | OVERDUE_INSTALLMENT |
    -- ANNUAL_FEE | MONTHLY_FEE | WITHDRAWAL_FEE | SAVINGS_ACTIVATION | SHARE_PURCHASE
    charge_calculation  VARCHAR(30)     NOT NULL DEFAULT 'FLAT',
    -- FLAT | PERCENT_OF_AMOUNT | PERCENT_OF_INTEREST | PERCENT_OF_AMOUNT_AND_INTEREST
    amount              NUMERIC(19,4)   NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    penalty             BOOLEAN         NOT NULL DEFAULT FALSE,
    free_withdrawal     BOOLEAN         NOT NULL DEFAULT FALSE,
    free_withdrawal_charge_frequency INT,
    restart_frequency   INT,
    restart_frequency_enum VARCHAR(20),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_charge_defs_applies ON charge_definitions(charge_applies_to);
CREATE INDEX idx_charge_defs_tenant  ON charge_definitions(tenant_id);

-- ── loan_charges ──────────────────────────────────────────────────────
-- Charges applied to a specific loan (derived from a charge_definition)
CREATE TABLE IF NOT EXISTS loan_charges (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    loan_id             UUID            NOT NULL REFERENCES loans(id),
    charge_definition_id UUID           REFERENCES charge_definitions(id),
    name                VARCHAR(100)    NOT NULL,
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    charge_time_type    VARCHAR(30)     NOT NULL,
    charge_calculation  VARCHAR(30)     NOT NULL DEFAULT 'FLAT',
    amount              NUMERIC(19,4)   NOT NULL,
    amount_paid         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    amount_waived       NUMERIC(19,4)   NOT NULL DEFAULT 0,
    amount_outstanding  NUMERIC(19,4)   NOT NULL DEFAULT 0,
    amount_through_charge_payment NUMERIC(19,4) NOT NULL DEFAULT 0,
    penalty             BOOLEAN         NOT NULL DEFAULT FALSE,
    paid                BOOLEAN         NOT NULL DEFAULT FALSE,
    waived              BOOLEAN         NOT NULL DEFAULT FALSE,
    due_for_collection_as_of_date DATE,
    installment_number  INT,
    -- Links to a specific repayment schedule installment (null = loan-level)
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_loan_charges_loan   ON loan_charges(loan_id);
CREATE INDEX idx_loan_charges_tenant ON loan_charges(tenant_id);

-- ── client_charges ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS client_charges (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    customer_id         UUID            NOT NULL REFERENCES customers(id),
    charge_definition_id UUID           REFERENCES charge_definitions(id),
    name                VARCHAR(100)    NOT NULL,
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    charge_time_type    VARCHAR(30)     NOT NULL,
    charge_calculation  VARCHAR(30)     NOT NULL DEFAULT 'FLAT',
    amount              NUMERIC(19,4)   NOT NULL,
    amount_paid         NUMERIC(19,4)   NOT NULL DEFAULT 0,
    amount_waived       NUMERIC(19,4)   NOT NULL DEFAULT 0,
    amount_outstanding  NUMERIC(19,4)   NOT NULL DEFAULT 0,
    penalty             BOOLEAN         NOT NULL DEFAULT FALSE,
    paid                BOOLEAN         NOT NULL DEFAULT FALSE,
    waived              BOOLEAN         NOT NULL DEFAULT FALSE,
    due_date            DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_client_charges_customer ON client_charges(customer_id);
CREATE INDEX idx_client_charges_tenant   ON client_charges(tenant_id);

-- ── charge_transactions ───────────────────────────────────────────────
-- Payment records for both loan and client charges
CREATE TABLE IF NOT EXISTS charge_transactions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            REFERENCES tenants(id),
    loan_charge_id      UUID            REFERENCES loan_charges(id),
    client_charge_id    UUID            REFERENCES client_charges(id),
    amount              NUMERIC(19,4)   NOT NULL,
    transaction_type    VARCHAR(20)     NOT NULL,
    -- PAYMENT | WAIVER | ADJUSTMENT
    submitted_on_date   DATE            NOT NULL DEFAULT CURRENT_DATE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);
