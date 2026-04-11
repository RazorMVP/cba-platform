-- ─────────────────────────────────────────────────────────────────────────────
-- V6 — Scheme-Compliant Chargeback Module
--
-- Upgrades the basic dispute module to a full scheme-compliant chargeback
-- workflow matching Visa, Mastercard, Verve, Afrigo, and UnionPay standards.
--
-- New state machine:
--   RAISED → RETRIEVAL_REQUESTED → CHARGEBACK_INITIATED
--          → REPRESENTMENT → PRE_ARBITRATION → RESOLVED
--   Any state → WITHDRAWN
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Chargeback Reason Codes ───────────────────────────────────────────────────
-- Per-scheme reason code catalogue. Seeded below with standard codes for
-- Visa, Mastercard, Verve, Afrigo, and UnionPay.
CREATE TABLE chargeback_reason_codes (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme                      VARCHAR(20)  NOT NULL,  -- VISA/MASTERCARD/VERVE/AFRIGO/UNIONPAY
    code                        VARCHAR(15)  NOT NULL,  -- e.g. '10.1', '4853', 'AFR-01'
    description                 TEXT         NOT NULL,
    category                    VARCHAR(50)  NOT NULL,  -- FRAUD/AUTHORIZATION/PROCESSING_ERROR/CONSUMER_DISPUTES
    max_days_to_chargeback      INT          NOT NULL,  -- from transaction date to initiate chargeback
    max_days_to_respond         INT          NOT NULL,  -- acquirer deadline to respond / representment
    max_days_pre_arbitration    INT          NOT NULL,  -- days issuer has to escalate after representment
    UNIQUE (scheme, code)
);

-- ── Retrieval Requests ────────────────────────────────────────────────────────
-- Issued when the issuer formally requests transaction documentation from
-- the acquirer before initiating a chargeback.
CREATE TABLE retrieval_requests (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id   UUID        NOT NULL REFERENCES card_disputes(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline     DATE        NOT NULL,   -- acquirer must respond by this date
    responded_at TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/FULFILLED/EXPIRED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_retrieval_requests_dispute ON retrieval_requests(dispute_id);
CREATE INDEX idx_retrieval_requests_status  ON retrieval_requests(status);
CREATE INDEX idx_retrieval_requests_deadline ON retrieval_requests(deadline);

-- ── Representments ────────────────────────────────────────────────────────────
-- Created when the acquirer disputes a chargeback (counters with evidence).
-- The issuer must then accept, escalate to pre-arbitration, or let the
-- deadline pass (acquirer wins by default).
CREATE TABLE representments (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id   UUID        NOT NULL REFERENCES card_disputes(id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deadline     DATE        NOT NULL,   -- issuer deadline to escalate or accept
    reason       TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/ACCEPTED/REJECTED/ESCALATED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_representments_dispute  ON representments(dispute_id);
CREATE INDEX idx_representments_status   ON representments(status);
CREATE INDEX idx_representments_deadline ON representments(deadline);

-- ── Extend card_disputes ──────────────────────────────────────────────────────
-- Add scheme chargeback fields to the existing dispute table.
ALTER TABLE card_disputes
    ADD COLUMN scheme_reason_code_id     UUID        REFERENCES chargeback_reason_codes(id),
    ADD COLUMN currency_code             VARCHAR(3),           -- ISO 4217 alpha code
    ADD COLUMN chargeback_deadline       DATE,                 -- deadline to initiate formal chargeback
    ADD COLUMN response_deadline         DATE,                 -- acquirer deadline after chargeback
    ADD COLUMN pre_arbitration_deadline  DATE,                 -- escalation deadline after representment
    ADD COLUMN resolution_favor          VARCHAR(10);          -- ISSUER or ACQUIRER on RESOLVED

-- ── Seed Chargeback Reason Codes ──────────────────────────────────────────────

-- Visa
INSERT INTO chargeback_reason_codes (scheme, code, description, category,
    max_days_to_chargeback, max_days_to_respond, max_days_pre_arbitration)
VALUES
    ('VISA', '10.1', 'EMV Liability Shift Counterfeit Fraud',
     'FRAUD', 120, 45, 30),
    ('VISA', '10.4', 'Other Fraud – Card-Present Environment',
     'FRAUD', 120, 45, 30),
    ('VISA', '11.2', 'Declined Authorization',
     'AUTHORIZATION', 75, 45, 30),
    ('VISA', '12.6', 'Duplicate Processing',
     'PROCESSING_ERROR', 120, 45, 30);

-- Mastercard
INSERT INTO chargeback_reason_codes (scheme, code, description, category,
    max_days_to_chargeback, max_days_to_respond, max_days_pre_arbitration)
VALUES
    ('MASTERCARD', '4853', 'Cardholder Dispute – Goods/Services Not as Described or Defective',
     'CONSUMER_DISPUTES', 120, 45, 30),
    ('MASTERCARD', '4837', 'No Cardholder Authorization',
     'FRAUD', 120, 45, 30),
    ('MASTERCARD', '4863', 'Cardholder Does Not Recognize – Potential Fraud',
     'FRAUD', 120, 45, 30);

-- Verve (Interswitch) — mirrors Mastercard codes, tighter deadlines
INSERT INTO chargeback_reason_codes (scheme, code, description, category,
    max_days_to_chargeback, max_days_to_respond, max_days_pre_arbitration)
VALUES
    ('VERVE', '4853', 'Goods/Services Not Delivered or Not as Described',
     'CONSUMER_DISPUTES', 90, 30, 20),
    ('VERVE', '4837', 'No Cardholder Authorization',
     'FRAUD', 90, 30, 20),
    ('VERVE', '4863', 'Cardholder Does Not Recognize Transaction',
     'FRAUD', 90, 30, 20);

-- Afrigo (PAPSS)
INSERT INTO chargeback_reason_codes (scheme, code, description, category,
    max_days_to_chargeback, max_days_to_respond, max_days_pre_arbitration)
VALUES
    ('AFRIGO', 'AFR-01', 'Unauthorized Transaction',
     'FRAUD', 60, 30, 15),
    ('AFRIGO', 'AFR-02', 'Goods or Services Not Received',
     'CONSUMER_DISPUTES', 60, 30, 15),
    ('AFRIGO', 'AFR-03', 'Duplicate Processing',
     'PROCESSING_ERROR', 60, 30, 15);

-- UnionPay (CUP)
INSERT INTO chargeback_reason_codes (scheme, code, description, category,
    max_days_to_chargeback, max_days_to_respond, max_days_pre_arbitration)
VALUES
    ('UNIONPAY', 'CUP-01', 'Unauthorized Transaction',
     'FRAUD', 90, 30, 20),
    ('UNIONPAY', 'CUP-02', 'Goods or Services Not Provided',
     'CONSUMER_DISPUTES', 90, 30, 20),
    ('UNIONPAY', 'CUP-03', 'Duplicate Processing',
     'PROCESSING_ERROR', 90, 30, 20);
