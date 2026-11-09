-- ─────────────────────────────────────────────────────────────────────────────
-- V67 — External payments settle asynchronously and post to the GL (backlog 1)
-- ─────────────────────────────────────────────────────────────────────────────
-- Debiting the customer for an outbound SWIFT/SEPA/ACH payment does not extinguish the
-- bank's obligation: it becomes an obligation to remit to the beneficiary's bank, which
-- ends only when the bank pays out of its nostro (IFRS 9 §3.3.1, B3.3.1(a); IAS 32 §11).
--   submit:   DR customer deposit / CR 2400 Outgoing Payments in Transit
--   settled:  DR 2400 / CR nostro (default 1002 Cash at Bank — remap per correspondent)
--   returned: DR 2400 / CR customer deposit
-- External payments posted no journal at all before this.

-- 1. GL account 2400, only where the V8 chart exists (like V63/V64).
INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, FALSE
FROM (VALUES
    ('30000000-0000-0000-0000-000000000015', 'Outgoing Payments in Transit', '2400', 'LIABILITY',
     '30000000-0000-0000-0000-000000000010',
     'External payments debited to customers and not yet paid out of the nostro (IFRS 9 §3.3.1; IAS 32 §11).')
) AS v(id, name, gl_code, account_type, parent_id, description)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = v.parent_id::uuid)
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('LIABILITY_EXTERNAL_PAYMENT_IN_TRANSIT', '30000000-0000-0000-0000-000000000015'),
    -- The nostro: the bank's account at its correspondent / settlement bank.
    ('ASSET_EXTERNAL_PAYMENT_NOSTRO',         '30000000-0000-0000-0000-000000000003')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 2. The payment's settlement state.
ALTER TABLE payments
    -- The gateway's own reference (e.g. SWIFT UETR); the caller's reference stays in external_reference.
    ADD COLUMN IF NOT EXISTS network_reference VARCHAR(64),
    -- ACCEPTED: the gateway took it. UNKNOWN: timeout / 5xx — it may or may not have gone out.
    ADD COLUMN IF NOT EXISTS submit_outcome    VARCHAR(10),
    ADD COLUMN IF NOT EXISTS settled_at        TIMESTAMPTZ,
    -- GATEWAY (settled at submit, simulated), CALLBACK (the gateway told us later) or MANUAL (ops, with a reason).
    ADD COLUMN IF NOT EXISTS outcome_source    VARCHAR(10);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_network_reference
    ON payments (network_reference) WHERE network_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_external_processing
    ON payments (status) WHERE payment_type = 'EXTERNAL_PAYMENT' AND status = 'PROCESSING';

-- 3. Every callback the gateway sends, accepted or not (an audit trail of the money path).
CREATE TABLE IF NOT EXISTS external_payment_callbacks (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference         VARCHAR(50),
    network_reference VARCHAR(64),
    outcome           VARCHAR(20),
    reason            TEXT,
    signature_ok      BOOLEAN      NOT NULL,
    result            VARCHAR(40)  NOT NULL,
    payment_id        UUID REFERENCES payments(id),
    remote_address    VARCHAR(64),
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ext_callbacks_payment ON external_payment_callbacks (payment_id);
CREATE INDEX IF NOT EXISTS idx_ext_callbacks_received ON external_payment_callbacks (received_at);
