-- V13 — Interchange income, scheme fees and the scheme cash leg (backlog 15b).
-- As issuer the bank earns interchange on a purchase and pays it on cash at another bank's ATM;
-- it pays the scheme its fees either way. At clearing card-service computes them, sends them with
-- the backend capture (which posts them in the same journal) and keeps on each settlement item what
-- the bank owes the scheme for it (gl_net). Operations record each scheme's settlement report; when
-- it equals the expected net of the day's items and refunds, the backend posts the cash leg and the
-- batch becomes SETTLED.

-- 1. UNIONPAY is the canonical scheme name (the FEP, exporters and chargebacks use it); V3 seeded
--    UNION_PAY, so a UnionPay authorization never matched a rate.
ALTER TABLE interchange_rates DROP CONSTRAINT IF EXISTS interchange_rates_scheme_check;
ALTER TABLE scheme_fees       DROP CONSTRAINT IF EXISTS scheme_fees_scheme_check;
UPDATE interchange_rates SET scheme = 'UNIONPAY' WHERE scheme = 'UNION_PAY';
UPDATE scheme_fees       SET scheme = 'UNIONPAY' WHERE scheme = 'UNION_PAY';
UPDATE authorization_log SET scheme = 'UNIONPAY' WHERE scheme = 'UNION_PAY';
ALTER TABLE interchange_rates ADD CONSTRAINT interchange_rates_scheme_check
    CHECK (scheme IN ('VISA','MASTERCARD','VERVE','AFRIGO','UNIONPAY'));
ALTER TABLE scheme_fees ADD CONSTRAINT scheme_fees_scheme_check
    CHECK (scheme IN ('VISA','MASTERCARD','VERVE','AFRIGO','UNIONPAY'));

-- 2. Fixed fees are major units of their row's currency and apply only to transactions in that
--    currency (card-service has no FX). Verve and Afrigo are Naira schemes.
ALTER TABLE scheme_fees ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) NOT NULL DEFAULT 'USD';
UPDATE scheme_fees       SET currency_code = 'NGN' WHERE scheme IN ('VERVE','AFRIGO');
UPDATE interchange_rates SET currency_code = 'NGN' WHERE scheme IN ('VERVE','AFRIGO');
COMMENT ON COLUMN interchange_rates.fixed_fee IS 'Major units of currency_code; applied only to transactions in that currency';
COMMENT ON COLUMN scheme_fees.fixed_fee       IS 'Major units of currency_code; applied only to transactions in that currency';

-- 3. interchange_log from V13: the issuer's view in major units — net_settlement_amount is what the
--    issuer owes the scheme (gross − interchange earned + interchange paid + fees). Rows before V13
--    hold the acquirer view in DE4 minor units.
ALTER TABLE interchange_log
    ADD COLUMN IF NOT EXISTS gross_amount          NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS currency_code         VARCHAR(3),
    ADD COLUMN IF NOT EXISTS interchange_direction VARCHAR(6),
    ADD COLUMN IF NOT EXISTS settlement_item_id    UUID;

-- 4. Scheme settlement reports, as operations record them. RECORDED once the backend posted the
--    cash leg; POSTING while that call is outstanding (a retry of the same report finishes it).
CREATE TABLE IF NOT EXISTS scheme_settlement_reports (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme                 VARCHAR(20)   NOT NULL,
    settlement_date        DATE          NOT NULL,
    currency_code          VARCHAR(3)    NOT NULL,          -- ISO 4217 alphabetic
    reported_net           NUMERIC(19,4) NOT NULL,          -- major units, signed (+ = bank pays)
    expected_net           NUMERIC(19,4) NOT NULL,
    item_count             INT           NOT NULL DEFAULT 0,
    refund_count           INT           NOT NULL DEFAULT 0,
    status                 VARCHAR(10)   NOT NULL CHECK (status IN ('POSTING','RECORDED')),
    backend_reference      VARCHAR(64)   NOT NULL UNIQUE,
    journal_transaction_id VARCHAR(64),
    created_by             VARCHAR(100),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    recorded_at            TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (scheme, settlement_date, currency_code)
);

-- 5. Settlement items: the scheme and the figures the backend posted (DE4 minor units, like amount),
--    and what the bank owes the scheme for the item at clearing (gl_net; null = no GL movement, e.g.
--    prepaid) and back at a refund (refund_gl_net, on the refund's date).
ALTER TABLE settlement_items
    ADD COLUMN IF NOT EXISTS scheme                 VARCHAR(20),
    ADD COLUMN IF NOT EXISTS interchange_amount     NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS interchange_direction  VARCHAR(6),
    ADD COLUMN IF NOT EXISTS scheme_fee_amount      NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS gl_net                 NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS refunded_amount        NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS interchange_reversed   NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS scheme_fee_reversed    NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS refund_gl_net          NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS refund_settlement_date DATE,
    ADD COLUMN IF NOT EXISTS settlement_report_id   UUID REFERENCES scheme_settlement_reports(id),
    ADD COLUMN IF NOT EXISTS refund_report_id       UUID REFERENCES scheme_settlement_reports(id);

CREATE INDEX IF NOT EXISTS idx_settlement_items_scheme        ON settlement_items(scheme);
CREATE INDEX IF NOT EXISTS idx_settlement_items_refund_date   ON settlement_items(scheme, refund_settlement_date);
CREATE INDEX IF NOT EXISTS idx_settlement_items_report        ON settlement_items(settlement_report_id);

-- 6. Items from before V13 (15a): the scheme from the authorization; cleared debits owed the scheme
--    their amount (no interchange or fees were posted); a cleared debit later reversed in full was
--    refunded in full (its refund date isn't known — the clearing date stands in). Partial refunds of
--    cleared debits made before V13 aren't known here: operations reconcile them by hand.
UPDATE settlement_items si SET scheme = CASE WHEN UPPER(al.scheme) = 'UNION_PAY' THEN 'UNIONPAY'
                                             ELSE COALESCE(UPPER(al.scheme), 'UNKNOWN') END
FROM authorization_log al
WHERE al.id = si.authorization_log_id AND si.scheme IS NULL;

UPDATE settlement_items SET gl_net = amount
WHERE gl_net IS NULL AND card_type = 'DEBIT' AND hold_reference IS NOT NULL AND account_id IS NOT NULL
  AND cleared_at IS NOT NULL;

UPDATE settlement_items SET refunded_amount = amount, refund_gl_net = -amount,
                            refund_settlement_date = cleared_at::date
WHERE status = 'REVERSED' AND gl_net IS NOT NULL AND refund_gl_net IS NULL;
