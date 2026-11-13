-- ─────────────────────────────────────────────────────────────────────────────
-- V19 — Any number of refunds per settlement item (backlog 15d, decision D1a)
-- ─────────────────────────────────────────────────────────────────────────────
-- A cleared card purchase can now be refunded in parts: each partial reversal is its own refund on the
-- backend (account_holds → card_hold_refunds, or a prepaid REFUND movement). card-service kept one refund
-- slot per settlement item (refund_gl_net, refund_settlement_date, refund_report_id …), so a second refund
-- had nowhere to go. Each refund is now a row here, keyed by the reversal reference card-service sends the
-- backend (a retried reversal maps to the same row), with what it takes back from the scheme
-- (refund_gl_net, negative, minor units) on the day it happened — the scheme report of that day counts it.
-- settlement_items.refunded_amount stays, as the running total of its refunds.

CREATE TABLE IF NOT EXISTS settlement_refunds (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id                UUID          NOT NULL REFERENCES settlement_items(id),
    reversal_reference     VARCHAR(80)   NOT NULL UNIQUE,
    amount_minor           NUMERIC(19,4) NOT NULL CHECK (amount_minor > 0),
    interchange_reversed   NUMERIC(19,4),
    scheme_fee_reversed    NUMERIC(19,4),
    refund_gl_net          NUMERIC(19,4) NOT NULL,
    refund_settlement_date DATE          NOT NULL,
    refund_report_id       UUID          REFERENCES scheme_settlement_reports(id),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_settlement_refunds_item ON settlement_refunds(item_id);
-- The scheme report's expected net: a date's refunds not yet in a report.
CREATE INDEX IF NOT EXISTS idx_settlement_refunds_unreported
    ON settlement_refunds(refund_settlement_date) WHERE refund_report_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_settlement_refunds_report ON settlement_refunds(refund_report_id);

-- The one refund each item could hold until now. Its reversal reference was never stored here.
INSERT INTO settlement_refunds (item_id, reversal_reference, amount_minor, interchange_reversed, scheme_fee_reversed,
                                refund_gl_net, refund_settlement_date, refund_report_id)
SELECT id, 'LEGACY-' || id, refunded_amount, interchange_reversed, scheme_fee_reversed,
       refund_gl_net, refund_settlement_date, refund_report_id
FROM settlement_items
WHERE refund_gl_net IS NOT NULL AND refund_settlement_date IS NOT NULL
  AND refunded_amount IS NOT NULL AND refunded_amount > 0
ON CONFLICT (reversal_reference) DO NOTHING;

DROP INDEX IF EXISTS idx_settlement_items_refund_date;
ALTER TABLE settlement_items
    DROP COLUMN IF EXISTS interchange_reversed,
    DROP COLUMN IF EXISTS scheme_fee_reversed,
    DROP COLUMN IF EXISTS refund_gl_net,
    DROP COLUMN IF EXISTS refund_settlement_date,
    DROP COLUMN IF EXISTS refund_report_id;

COMMENT ON COLUMN settlement_items.refunded_amount IS 'Running total of settlement_refunds.amount_minor (minor units)';

-- ─────────────────────────────────────────────────────────────────────────────
-- What reversals have given back of each approval (card-side minor units: DE6 when the original had it, else
-- DE4). A reversal now finds its original through DE90's STAN, so several reversals — each with a STAN of its
-- own — can name one original; the 0400 guard (one per reversal STAN) no longer bounds the total, this does.
-- A reversal locks the original row, refuses more than is left (RC 13) and adds itself only once every step
-- (backend release / refund, wallet credit) succeeded.
ALTER TABLE authorization_log ADD COLUMN IF NOT EXISTS reversed_amount NUMERIC(19,4) NOT NULL DEFAULT 0;

-- Before V19 an approval could be reversed once (a 0400 with the original's STAN): count that reversal.
UPDATE authorization_log o
SET reversed_amount = LEAST(COALESCE(o.billing_amount, o.amount),
                            COALESCE(r.billing_amount, r.amount, o.billing_amount, o.amount))
FROM authorization_log r
WHERE r.card_id = o.card_id AND r.stan = o.stan AND r.mti = '0400'
  AND o.mti <> '0400' AND o.response_code = '00'
  AND COALESCE(o.billing_amount, o.amount) IS NOT NULL;
