-- ═══════════════════════════════════════════════════════════════════
-- V5__teller_module.sql — Teller / Cash Management Module
-- Mirrors Mifos teller pattern: Teller → Cashier → Session → CashTransaction
-- All monetary amounts: NUMERIC(19,4) → BigDecimal in Java
-- ═══════════════════════════════════════════════════════════════════

-- ── tellers ──────────────────────────────────────────────────────────
-- A physical cash desk at a branch. Tellers are enabled/disabled by ADMIN.
CREATE TABLE tellers (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            REFERENCES tenants(id),
    name            VARCHAR(100)    NOT NULL,
    description     TEXT,
    branch_code     VARCHAR(10)     NOT NULL,
    office_id       VARCHAR(50),
    status          VARCHAR(20)     NOT NULL DEFAULT 'INACTIVE',
    -- INACTIVE | ACTIVE | CLOSED
    start_date      DATE            NOT NULL DEFAULT CURRENT_DATE,
    end_date        DATE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_tellers_branch  ON tellers(branch_code);
CREATE INDEX idx_tellers_status  ON tellers(status);
CREATE INDEX idx_tellers_tenant  ON tellers(tenant_id);

-- ── cashiers ─────────────────────────────────────────────────────────
-- A staff member assigned to operate a teller.
CREATE TABLE cashiers (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    teller_id       UUID            NOT NULL REFERENCES tellers(id),
    staff_id        VARCHAR(100)    NOT NULL,
    -- Username / employee ID of the assigned staff
    description     TEXT,
    start_date      DATE            NOT NULL DEFAULT CURRENT_DATE,
    end_date        DATE,
    full_day        BOOLEAN         NOT NULL DEFAULT TRUE,
    start_time      TIME,
    end_time        TIME,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_cashiers_teller  ON cashiers(teller_id);
CREATE INDEX idx_cashiers_staff   ON cashiers(staff_id);

-- ── teller_sessions ──────────────────────────────────────────────────
-- One session per cashier per working day. Opening float → transactions → settlement.
CREATE TABLE teller_sessions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    teller_id           UUID            NOT NULL REFERENCES tellers(id),
    cashier_id          UUID            NOT NULL REFERENCES cashiers(id),
    session_date        DATE            NOT NULL DEFAULT CURRENT_DATE,
    opening_balance     NUMERIC(19,4)   NOT NULL DEFAULT 0,
    closing_balance     NUMERIC(19,4),
    actual_cash         NUMERIC(19,4),
    -- Physical cash counted at settlement
    difference          NUMERIC(19,4),
    -- actual_cash - closing_balance (variance)
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    -- OPEN | CLOSED
    settlement_note     TEXT,
    opened_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version             BIGINT          NOT NULL DEFAULT 0,
    UNIQUE (cashier_id, session_date)
    -- One session per cashier per day
);

CREATE INDEX idx_sessions_teller  ON teller_sessions(teller_id);
CREATE INDEX idx_sessions_cashier ON teller_sessions(cashier_id);
CREATE INDEX idx_sessions_date    ON teller_sessions(session_date DESC);
CREATE INDEX idx_sessions_status  ON teller_sessions(status);

-- ── cash_transactions ─────────────────────────────────────────────────
-- Individual cash-in / cash-out operations within a session.
CREATE TABLE cash_transactions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID            NOT NULL REFERENCES teller_sessions(id),
    teller_id           UUID            NOT NULL REFERENCES tellers(id),
    cashier_id          UUID            NOT NULL REFERENCES cashiers(id),
    account_id          UUID            REFERENCES accounts(id),
    -- Linked customer account (nullable for teller-to-teller vault transfers)
    transaction_type    VARCHAR(20)     NOT NULL,
    -- CASH_IN | CASH_OUT
    amount              NUMERIC(19,4)   NOT NULL,
    currency_code       CHAR(3)         NOT NULL DEFAULT 'USD',
    description         TEXT,
    reference_number    VARCHAR(50)     UNIQUE NOT NULL,
    transaction_date    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(100)
);

CREATE INDEX idx_cash_txn_session  ON cash_transactions(session_id);
CREATE INDEX idx_cash_txn_account  ON cash_transactions(account_id);
CREATE INDEX idx_cash_txn_date     ON cash_transactions(transaction_date DESC);
