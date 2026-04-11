-- ============================================================
-- CBA Card Service — V3 Interchange Management
-- ============================================================
-- Three tables:
--   interchange_rates  — per-scheme/card-type/MCC/channel rate tiers
--   scheme_fees        — per-scheme assessment & network fees
--   interchange_log    — calculated interchange record per auth
-- ============================================================

-- ── Interchange Rates ─────────────────────────────────────────────────────────
-- Each row defines the interchange % + fixed fee for a specific combination of:
--   scheme × card_type × mcc_category × transaction_type × channel
--
-- mcc_category = NULL means the rate applies to all MCCs not covered by a
-- more specific row. The qualification engine picks the highest-specificity match.
CREATE TABLE interchange_rates (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme           VARCHAR(20)   NOT NULL CHECK (scheme IN ('VISA','MASTERCARD','VERVE','AFRIGO','UNION_PAY')),
    card_type        VARCHAR(20)   NOT NULL CHECK (card_type IN ('DEBIT','PREPAID','CREDIT')),
    mcc_category     VARCHAR(50),                           -- NULL = all MCCs; specific = e.g. '5411' or 'SUPERMARKET'
    transaction_type VARCHAR(20)   NOT NULL CHECK (transaction_type IN ('PURCHASE','CASH','REFUND')),
    channel          VARCHAR(20)   NOT NULL CHECK (channel IN ('CARD_PRESENT','CNP')),
    rate_percent     NUMERIC(6,4)  NOT NULL DEFAULT 0,      -- e.g. 1.7500 = 1.75%
    fixed_fee        NUMERIC(10,4) NOT NULL DEFAULT 0,      -- flat fee in minor units (same currency as txn)
    currency_code    VARCHAR(3)    NOT NULL DEFAULT 'USD',  -- base currency for fixed_fee
    effective_from   DATE          NOT NULL,
    effective_to     DATE,                                  -- NULL = still active
    active           BOOLEAN       NOT NULL DEFAULT true,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_interchange_rates_scheme ON interchange_rates(scheme, card_type, transaction_type, channel)
    WHERE active = true;
CREATE INDEX idx_interchange_rates_mcc    ON interchange_rates(mcc_category)
    WHERE active = true AND mcc_category IS NOT NULL;

-- ── Scheme Fees ────────────────────────────────────────────────────────────────
-- Fees charged by the scheme itself (Visa, Mastercard, etc.) on every transaction,
-- separate from interchange (which flows between issuer and acquirer).
-- Types: ASSESSMENT (basis-point levy), NETWORK (per-transaction fixed),
--        CROSS_BORDER (additional % for international transactions),
--        INTERNATIONAL_SERVICE (ISA — additional % for currency conversion)
CREATE TABLE scheme_fees (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme         VARCHAR(20)   NOT NULL CHECK (scheme IN ('VISA','MASTERCARD','VERVE','AFRIGO','UNION_PAY')),
    fee_type       VARCHAR(30)   NOT NULL CHECK (fee_type IN ('ASSESSMENT','NETWORK','CROSS_BORDER','INTERNATIONAL_SERVICE')),
    rate_percent   NUMERIC(6,4)  NOT NULL DEFAULT 0,
    fixed_fee      NUMERIC(10,4) NOT NULL DEFAULT 0,
    effective_from DATE          NOT NULL,
    effective_to   DATE,
    active         BOOLEAN       NOT NULL DEFAULT true,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_scheme_fees_scheme ON scheme_fees(scheme, fee_type) WHERE active = true;

-- ── Interchange Log ────────────────────────────────────────────────────────────
-- Immutable record of the interchange calculation applied to each authorization.
-- Created when SettlementService processes a batch or when an admin explicitly
-- triggers a calculation via GET /api/v1/interchange/calculate?authId=...
CREATE TABLE interchange_log (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    authorization_log_id  UUID          REFERENCES authorization_log(id),
    scheme                VARCHAR(20)   NOT NULL,
    interchange_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    scheme_fee_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_settlement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    rate_applied          VARCHAR(200),                    -- human-readable description for audit
    calculated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_interchange_log_auth ON interchange_log(authorization_log_id);

-- ============================================================
-- Demo Seed Data — Realistic representative interchange tiers
-- ============================================================
-- All effective_from = current date for demo purposes.
-- Real deployments load these from scheme membership agreements.

-- ── Visa Interchange Rates ────────────────────────────────────────────────────
INSERT INTO interchange_rates (scheme, card_type, mcc_category, transaction_type, channel, rate_percent, fixed_fee, effective_from) VALUES
  -- Visa Debit — Card Present (EMV chip / contactless)
  ('VISA', 'DEBIT',   NULL,   'PURCHASE',  'CARD_PRESENT', 0.9000, 0.0500, CURRENT_DATE),
  ('VISA', 'DEBIT',   NULL,   'CASH',      'CARD_PRESENT', 1.0000, 0.2500, CURRENT_DATE),
  ('VISA', 'DEBIT',   NULL,   'REFUND',    'CARD_PRESENT', 0.0000, 0.0000, CURRENT_DATE),
  -- Visa Debit — CNP (e-commerce / MOTO)
  ('VISA', 'DEBIT',   NULL,   'PURCHASE',  'CNP',          1.6000, 0.0500, CURRENT_DATE),
  -- Visa Credit — Card Present
  ('VISA', 'CREDIT',  NULL,   'PURCHASE',  'CARD_PRESENT', 1.7500, 0.1000, CURRENT_DATE),
  ('VISA', 'CREDIT',  NULL,   'CASH',      'CARD_PRESENT', 1.9000, 0.2500, CURRENT_DATE),
  ('VISA', 'CREDIT',  NULL,   'REFUND',    'CARD_PRESENT', 0.0000, 0.0000, CURRENT_DATE),
  -- Visa Credit — CNP
  ('VISA', 'CREDIT',  NULL,   'PURCHASE',  'CNP',          2.3000, 0.1000, CURRENT_DATE),
  -- Visa Prepaid — same as Debit
  ('VISA', 'PREPAID', NULL,   'PURCHASE',  'CARD_PRESENT', 0.9000, 0.0500, CURRENT_DATE),
  ('VISA', 'PREPAID', NULL,   'PURCHASE',  'CNP',          1.6000, 0.0500, CURRENT_DATE),
  -- Visa supermarket (MCC 5411) — preferred lower rate
  ('VISA', 'DEBIT',   '5411', 'PURCHASE',  'CARD_PRESENT', 0.0500, 0.2200, CURRENT_DATE),
  ('VISA', 'CREDIT',  '5411', 'PURCHASE',  'CARD_PRESENT', 1.1500, 0.0500, CURRENT_DATE);

-- ── Mastercard Interchange Rates ──────────────────────────────────────────────
INSERT INTO interchange_rates (scheme, card_type, mcc_category, transaction_type, channel, rate_percent, fixed_fee, effective_from) VALUES
  ('MASTERCARD', 'DEBIT',   NULL,   'PURCHASE',  'CARD_PRESENT', 0.9000, 0.0500, CURRENT_DATE),
  ('MASTERCARD', 'DEBIT',   NULL,   'CASH',      'CARD_PRESENT', 0.9000, 0.3000, CURRENT_DATE),
  ('MASTERCARD', 'DEBIT',   NULL,   'REFUND',    'CARD_PRESENT', 0.0000, 0.0000, CURRENT_DATE),
  ('MASTERCARD', 'DEBIT',   NULL,   'PURCHASE',  'CNP',          1.6000, 0.0800, CURRENT_DATE),
  ('MASTERCARD', 'CREDIT',  NULL,   'PURCHASE',  'CARD_PRESENT', 1.8900, 0.1000, CURRENT_DATE),
  ('MASTERCARD', 'CREDIT',  NULL,   'CASH',      'CARD_PRESENT', 1.9000, 0.2500, CURRENT_DATE),
  ('MASTERCARD', 'CREDIT',  NULL,   'REFUND',    'CARD_PRESENT', 0.0000, 0.0000, CURRENT_DATE),
  ('MASTERCARD', 'CREDIT',  NULL,   'PURCHASE',  'CNP',          2.2000, 0.1000, CURRENT_DATE),
  ('MASTERCARD', 'PREPAID', NULL,   'PURCHASE',  'CARD_PRESENT', 0.9000, 0.0500, CURRENT_DATE),
  ('MASTERCARD', 'PREPAID', NULL,   'PURCHASE',  'CNP',          1.6000, 0.0800, CURRENT_DATE),
  -- Mastercard supermarket (MCC 5411) — Merit III preferred rate
  ('MASTERCARD', 'DEBIT',   '5411', 'PURCHASE',  'CARD_PRESENT', 0.0500, 0.2200, CURRENT_DATE),
  ('MASTERCARD', 'CREDIT',  '5411', 'PURCHASE',  'CARD_PRESENT', 1.2200, 0.0500, CURRENT_DATE);

-- ── Verve Interchange Rates (Nigeria domestic) ────────────────────────────────
INSERT INTO interchange_rates (scheme, card_type, mcc_category, transaction_type, channel, rate_percent, fixed_fee, effective_from) VALUES
  ('VERVE', 'DEBIT',   NULL,   'PURCHASE',  'CARD_PRESENT', 0.7500, 0.0000, CURRENT_DATE),
  ('VERVE', 'DEBIT',   NULL,   'CASH',      'CARD_PRESENT', 0.5000, 0.0000, CURRENT_DATE),
  ('VERVE', 'DEBIT',   NULL,   'PURCHASE',  'CNP',          1.0000, 0.0000, CURRENT_DATE),
  ('VERVE', 'PREPAID', NULL,   'PURCHASE',  'CARD_PRESENT', 0.7500, 0.0000, CURRENT_DATE);

-- ── Afrigo / PAPSS Interchange Rates (African cross-border) ──────────────────
INSERT INTO interchange_rates (scheme, card_type, mcc_category, transaction_type, channel, rate_percent, fixed_fee, effective_from) VALUES
  ('AFRIGO', 'DEBIT',  NULL,   'PURCHASE',  'CARD_PRESENT', 0.5000, 0.0000, CURRENT_DATE),
  ('AFRIGO', 'DEBIT',  NULL,   'PURCHASE',  'CNP',          0.7500, 0.0000, CURRENT_DATE),
  ('AFRIGO', 'CREDIT', NULL,   'PURCHASE',  'CARD_PRESENT', 0.8000, 0.0000, CURRENT_DATE);

-- ── UnionPay Interchange Rates ────────────────────────────────────────────────
INSERT INTO interchange_rates (scheme, card_type, mcc_category, transaction_type, channel, rate_percent, fixed_fee, effective_from) VALUES
  ('UNION_PAY', 'DEBIT',  NULL, 'PURCHASE',  'CARD_PRESENT', 0.6000, 0.0000, CURRENT_DATE),
  ('UNION_PAY', 'DEBIT',  NULL, 'PURCHASE',  'CNP',          1.0000, 0.0000, CURRENT_DATE),
  ('UNION_PAY', 'CREDIT', NULL, 'PURCHASE',  'CARD_PRESENT', 1.2500, 0.0000, CURRENT_DATE),
  ('UNION_PAY', 'CREDIT', NULL, 'PURCHASE',  'CNP',          1.8000, 0.0000, CURRENT_DATE);

-- ── Scheme Assessment Fees ────────────────────────────────────────────────────
-- These are charged by the card network on top of interchange.
INSERT INTO scheme_fees (scheme, fee_type, rate_percent, fixed_fee, effective_from) VALUES
  -- Visa
  ('VISA', 'ASSESSMENT',          0.1100, 0.0000, CURRENT_DATE),  -- 0.11% Visa Assessment
  ('VISA', 'NETWORK',             0.0000, 0.0195, CURRENT_DATE),  -- $0.0195 Visa Acquirer Processing
  ('VISA', 'CROSS_BORDER',        0.4000, 0.0000, CURRENT_DATE),  -- 0.40% cross-border fee
  ('VISA', 'INTERNATIONAL_SERVICE', 0.9000, 0.0000, CURRENT_DATE), -- 0.90% ISA (currency conversion)
  -- Mastercard
  ('MASTERCARD', 'ASSESSMENT',    0.1100, 0.0000, CURRENT_DATE),
  ('MASTERCARD', 'NETWORK',       0.0000, 0.0185, CURRENT_DATE),  -- NABU fee
  ('MASTERCARD', 'CROSS_BORDER',  0.6000, 0.0000, CURRENT_DATE),
  ('MASTERCARD', 'INTERNATIONAL_SERVICE', 1.0000, 0.0000, CURRENT_DATE),
  -- Verve (NIBSS assessment)
  ('VERVE', 'ASSESSMENT',         0.0300, 0.0000, CURRENT_DATE),
  ('VERVE', 'NETWORK',            0.0000, 0.0100, CURRENT_DATE),
  -- Afrigo (PAPSS fee — African Union cross-border initiative, intentionally low)
  ('AFRIGO', 'ASSESSMENT',        0.0200, 0.0000, CURRENT_DATE),
  ('AFRIGO', 'NETWORK',           0.0000, 0.0050, CURRENT_DATE),
  ('AFRIGO', 'CROSS_BORDER',      0.1000, 0.0000, CURRENT_DATE),  -- PAPSS charges minimal cross-border
  -- UnionPay
  ('UNION_PAY', 'ASSESSMENT',     0.0800, 0.0000, CURRENT_DATE),
  ('UNION_PAY', 'NETWORK',        0.0000, 0.0130, CURRENT_DATE),
  ('UNION_PAY', 'CROSS_BORDER',   0.5000, 0.0000, CURRENT_DATE);
