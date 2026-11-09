-- ─────────────────────────────────────────────────────────────────────────────
-- V17 — Prepaid wallet loads and unloads (backlog 15c)
-- ─────────────────────────────────────────────────────────────────────────────
-- A load moves money from the cardholder's backend account into the wallet; an unload
-- redeems it back (CBN stored-value guidelines §3.7: the holder may have the balance refunded).
-- The backend posts the money and the journal (DR deposit / CR 2500, or the reverse) and is
-- idempotent on the reference, so a movement whose answer was lost stays PENDING and is retried
-- with the same reference until the backend says yes or no.
CREATE TABLE IF NOT EXISTS wallet_movements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       UUID         REFERENCES prepaid_wallets(id),
    card_id         UUID         NOT NULL REFERENCES cards(id),
    movement_type   VARCHAR(10)  NOT NULL CHECK (movement_type IN ('LOAD','UNLOAD')),
    reference       VARCHAR(80)  NOT NULL UNIQUE,
    account_id      UUID         NOT NULL,
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency_code   VARCHAR(3)   NOT NULL,
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    failure_reason  VARCHAR(60),
    next_retry_at   TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_wallet_movements_card ON wallet_movements (card_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_movements_pending ON wallet_movements (next_retry_at) WHERE status = 'PENDING';
