-- Backlog set 1, PR A (items 24, 23, 3).

-- ── 24: a payment consent carries its initiation and is used once ─────────────
-- UK Open Banking domestic-payment-consent: InstructedAmount + CreditorAccount (+ optional
-- DebtorAccount, RemittanceInformation). A payment must match it, and consumes it.
ALTER TABLE open_banking_consents
    ADD COLUMN IF NOT EXISTS instructed_amount   NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS instructed_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS creditor_account_id UUID,
    ADD COLUMN IF NOT EXISTS debtor_account_id   UUID,
    ADD COLUMN IF NOT EXISTS payment_reference   VARCHAR(140),
    ADD COLUMN IF NOT EXISTS payment_id          UUID,
    ADD COLUMN IF NOT EXISTS consumed_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version             BIGINT NOT NULL DEFAULT 0;

-- Amount, currency and creditor are set together or not at all.
ALTER TABLE open_banking_consents
    ADD CONSTRAINT chk_consent_initiation_complete CHECK (
        (instructed_amount IS NULL AND instructed_currency IS NULL AND creditor_account_id IS NULL)
        OR (instructed_amount > 0 AND instructed_currency IS NOT NULL AND creditor_account_id IS NOT NULL));

-- CONSUMED (the payment was made) is a new status value; the column is VARCHAR(30), no type change.

-- ── 23b: a suspended partner organisation is refused; reactivation restores its status ──
ALTER TABLE partner_organizations
    ADD COLUMN IF NOT EXISTS status_before_suspension VARCHAR(50),
    ADD COLUMN IF NOT EXISTS suspension_reason        TEXT;

-- ── 3: ACCOUNT.BALANCE_UPDATED, coalesced per partner organisation and account ──
-- One row per (org, account). A balance change marks it pending; the dispatcher sends at most one
-- event per window and always sends after the last change (trailing edge).
CREATE TABLE IF NOT EXISTS partner_balance_notifications (
    org_id         UUID        NOT NULL,
    account_id     UUID        NOT NULL REFERENCES accounts(id),
    consent_id     VARCHAR(50) NOT NULL,
    pending_since  TIMESTAMPTZ,
    last_sent_at   TIMESTAMPTZ,
    PRIMARY KEY (org_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_balance_notifications_pending
    ON partner_balance_notifications (pending_since) WHERE pending_since IS NOT NULL;
