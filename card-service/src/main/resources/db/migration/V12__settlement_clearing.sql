-- V12 — Card clearing (backlog 15a).
-- Every approved authorization that reserved funds now gets a settlement item, in the same
-- transaction as the approval. A debit-card purchase (0100) is cleared at batch close: the
-- backend turns its hold into a debit (POST …/card-holds/{ref}/capture, which posts
-- DR deposit / CR 2300 Card Scheme Settlement Payable). Single-message debits (0200) and
-- prepaid approvals are already completed when approved, so their items start SETTLED.
-- Statuses: PENDING (awaiting clearing) → SETTLED | FAILED (refused, reason kept) | REVERSED.

ALTER TABLE settlement_items
    ADD COLUMN IF NOT EXISTS card_id        UUID,
    ADD COLUMN IF NOT EXISTS account_id     UUID,          -- backend account (debit cards)
    ADD COLUMN IF NOT EXISTS card_type      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS hold_reference VARCHAR(64),
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cleared_at     TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_settlement_items_auth   ON settlement_items(authorization_log_id);
CREATE INDEX IF NOT EXISTS idx_settlement_items_status ON settlement_items(status);
