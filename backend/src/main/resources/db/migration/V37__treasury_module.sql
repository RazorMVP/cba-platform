-- V37: Treasury Module — placements, interbank positions
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS treasury_placements (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    reference           VARCHAR(50)     NOT NULL UNIQUE,
    counterparty_name   VARCHAR(200)    NOT NULL,
    counterparty_bic    VARCHAR(20),
    placement_type      VARCHAR(30)     NOT NULL, -- FIXED_DEPOSIT | TREASURY_BILL | BOND | CALL_MONEY | REPO
    principal           NUMERIC(19,4)   NOT NULL,
    interest_rate       NUMERIC(7,4)    NOT NULL,  -- annual %
    currency_code       VARCHAR(10)     NOT NULL DEFAULT 'USD',
    start_date          DATE            NOT NULL,
    maturity_date       DATE            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING | ACTIVE | MATURED | CANCELLED
    expected_return     NUMERIC(19,4),
    actual_return       NUMERIC(19,4),
    gl_source_account   UUID,           -- funding GL account
    gl_income_account   UUID,           -- interest income GL account
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS treasury_interbank_positions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    reference           VARCHAR(50)     NOT NULL UNIQUE,
    counterparty_name   VARCHAR(200)    NOT NULL,
    counterparty_bic    VARCHAR(20),
    direction           VARCHAR(15)     NOT NULL, -- LENDING | BORROWING
    amount              NUMERIC(19,4)   NOT NULL,
    currency_code       VARCHAR(10)     NOT NULL DEFAULT 'USD',
    interest_rate       NUMERIC(7,4)    NOT NULL,
    start_date          DATE            NOT NULL,
    maturity_date       DATE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SETTLED | CANCELLED
    settlement_gl       UUID,           -- GL account for settlement
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100),
    version             BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tp_status       ON treasury_placements(status);
CREATE INDEX IF NOT EXISTS idx_tp_maturity     ON treasury_placements(maturity_date);
CREATE INDEX IF NOT EXISTS idx_tip_status      ON treasury_interbank_positions(status);
CREATE INDEX IF NOT EXISTS idx_tip_direction   ON treasury_interbank_positions(direction);

-- Seed demo data
INSERT INTO treasury_placements (reference, counterparty_name, counterparty_bic, placement_type,
    principal, interest_rate, currency_code, start_date, maturity_date, status, expected_return, notes)
VALUES
  ('TP-2026-001', 'First National Bank', 'FNBKUSXX', 'FIXED_DEPOSIT',
   1000000.0000, 5.25, 'USD', '2026-01-15', '2026-07-15', 'ACTIVE', 26250.0000,
   '6-month placement with FNB'),
  ('TP-2026-002', 'Central Treasury', 'CTRYUSXX', 'TREASURY_BILL',
   500000.0000, 4.80, 'USD', '2026-02-01', '2026-05-01', 'ACTIVE', 6000.0000,
   'Q1 T-bill allocation'),
  ('TP-2026-003', 'Metro Bank', 'MROBUSXX', 'CALL_MONEY',
   250000.0000, 3.50, 'USD', '2026-04-01', '2026-04-30', 'MATURED', 729.1700,
   'Short overnight call deposit — matured');

INSERT INTO treasury_interbank_positions (reference, counterparty_name, counterparty_bic,
    direction, amount, currency_code, interest_rate, start_date, maturity_date, status, notes)
VALUES
  ('IB-2026-001', 'Alliance Bank', 'ALBNUSXX', 'LENDING',
   2000000.0000, 'USD', 4.50, '2026-03-01', '2026-09-01', 'ACTIVE',
   'Bilateral lending agreement — 6 months'),
  ('IB-2026-002', 'Continental Bank', 'CONBUSXX', 'BORROWING',
   750000.0000, 'USD', 3.75, '2026-04-01', '2026-07-01', 'ACTIVE',
   'Overnight borrowing facility — renewing quarterly');
