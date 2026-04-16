-- ═══════════════════════════════════════════════════════════════════
-- V19__accounting_rules_provisioning.sql
-- Covers: Accounting Rules, Provisioning Criteria
-- ═══════════════════════════════════════════════════════════════════

-- ── accounting_rules ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS accounting_rules (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(200)    UNIQUE NOT NULL,
    description             TEXT,
    debit_account_id        UUID            REFERENCES gl_accounts(id),
    credit_account_id       UUID            REFERENCES gl_accounts(id),
    allow_multiple_debits   BOOLEAN         NOT NULL DEFAULT FALSE,
    allow_multiple_credits  BOOLEAN         NOT NULL DEFAULT FALSE,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounting_rules_active ON accounting_rules(active);

-- ── provisioning_criteria ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS provisioning_criteria (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    criteria_name   VARCHAR(200)    UNIQUE NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS provisioning_criteria_definitions (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    criteria_id             UUID            NOT NULL REFERENCES provisioning_criteria(id),
    category_name           VARCHAR(100)    NOT NULL,
    -- e.g. STANDARD, WATCH, SUB_STANDARD, DOUBTFUL, LOSS
    min_age                 INT             NOT NULL DEFAULT 0,
    max_age                 INT             NOT NULL DEFAULT 30,
    provision_percentage    NUMERIC(5,2)    NOT NULL DEFAULT 0.00,
    liability_account_id    UUID            REFERENCES gl_accounts(id),
    expense_account_id      UUID            REFERENCES gl_accounts(id)
);

CREATE INDEX idx_prov_defs_criteria ON provisioning_criteria_definitions(criteria_id);
