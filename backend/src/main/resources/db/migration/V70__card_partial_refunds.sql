-- ─────────────────────────────────────────────────────────────────────────────
-- V70 — Any number of partial refunds per card purchase (backlog 15d, decision D1a)
-- ─────────────────────────────────────────────────────────────────────────────
-- A captured card debit (account_holds CAPTURED) could be refunded only once: a second partial
-- reversal of the same purchase was refused ALREADY_REFUNDED. Now each refund is its own row here,
-- keyed by card-service's reversal reference (a retry of the same reference is a repeat; any other
-- reference is a new refund), until the captured amount is used up.
--
-- account_holds.refunded_amount, interchange_reversed and scheme_fee_reversed become running totals
-- of these rows. Interchange and scheme fees are reversed cumulatively, so the refunds' shares add up
-- to exactly what the debit posted once it is fully refunded (IFRS 15 §38, as 15b). No new accounting:
-- each refund posts the 15b refund journal (DR 2300 / CR deposit, plus the fee lines) for its share.
--
-- Prepaid clearings need no new table: every refund is already its own prepaid_card_movements REFUND
-- row; the CLEARING row's refunded_amount becomes the running total.

CREATE TABLE IF NOT EXISTS card_hold_refunds (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    hold_id                UUID          NOT NULL REFERENCES account_holds (id),
    refund_reference       VARCHAR(64)   NOT NULL UNIQUE,
    amount                 NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    interchange_reversed   NUMERIC(19,4),
    scheme_fee_reversed    NUMERIC(19,4),
    journal_transaction_id VARCHAR(64),
    created_by             VARCHAR(100),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_card_hold_refunds_hold ON card_hold_refunds (hold_id);

-- One row per hold refunded before V70 (its one refund). A refund made without a reversal key is
-- keyed LEGACY-<hold id>, the key a keyless release still uses, so its retry stays a repeat.
INSERT INTO card_hold_refunds (hold_id, refund_reference, amount, interchange_reversed, scheme_fee_reversed,
                               created_by, created_at)
SELECT h.id, COALESCE(h.refund_reference, 'LEGACY-' || h.id), h.refunded_amount,
       h.interchange_reversed, h.scheme_fee_reversed, h.released_by, COALESCE(h.released_at, now())
FROM account_holds h
WHERE h.status = 'REFUNDED' AND h.refunded_amount IS NOT NULL AND h.refunded_amount > 0
ON CONFLICT (refund_reference) DO NOTHING;

COMMENT ON COLUMN account_holds.refunded_amount IS 'Running total of card_hold_refunds.amount (at most captured_amount)';
COMMENT ON COLUMN account_holds.refund_reference IS 'The latest refund''s reversal reference (every refund: card_hold_refunds)';
COMMENT ON COLUMN account_holds.interchange_reversed IS 'Running total of interchange reversed by the hold''s refunds';
COMMENT ON COLUMN account_holds.scheme_fee_reversed IS 'Running total of scheme fees reversed by the hold''s refunds';
