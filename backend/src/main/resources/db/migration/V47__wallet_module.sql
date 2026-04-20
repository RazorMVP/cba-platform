-- ── Wallet Module: Pockets + QR Payments ─────────────────────────────────────

-- Pockets: named sub-wallet envelopes that group savings accounts
CREATE TABLE pockets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID         NOT NULL REFERENCES customers(id),
    name         VARCHAR(100) NOT NULL,
    description  TEXT,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  DEFAULT now(),
    updated_at   TIMESTAMPTZ  DEFAULT now(),
    created_by   VARCHAR(100),
    updated_by   VARCHAR(100),
    version      BIGINT       DEFAULT 0
);

CREATE INDEX idx_pockets_customer ON pockets(customer_id);

-- Pocket-to-account links (many-to-many; one account can belong to one pocket at a time)
CREATE TABLE pocket_accounts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pocket_id  UUID        NOT NULL REFERENCES pockets(id) ON DELETE CASCADE,
    account_id UUID        NOT NULL REFERENCES accounts(id),
    linked_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE (account_id)   -- each savings account belongs to at most one pocket
);

CREATE INDEX idx_pocket_accounts_pocket  ON pocket_accounts(pocket_id);
CREATE INDEX idx_pocket_accounts_account ON pocket_accounts(account_id);

-- QR payment tokens: stateful, single-use QR codes for receiving payments
CREATE TABLE qr_payment_tokens (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token          VARCHAR(600) NOT NULL UNIQUE,
    account_id     UUID         NOT NULL REFERENCES accounts(id),
    preset_amount  NUMERIC(19,4),
    reference      VARCHAR(200),
    expires_at     TIMESTAMPTZ,
    used           BOOLEAN      DEFAULT false,
    created_at     TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX idx_qr_tokens_token   ON qr_payment_tokens(token);
CREATE INDEX idx_qr_tokens_account ON qr_payment_tokens(account_id);
