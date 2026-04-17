-- V36: Account holds + dormancy (Session 79)

-- Track last transaction date on accounts for dormancy detection
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS last_transaction_date DATE;

-- Fund holds: reservations against an account balance without moving funds
CREATE TABLE IF NOT EXISTS account_holds (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id        UUID         NOT NULL REFERENCES accounts(id),
    amount            NUMERIC(19,4) NOT NULL,
    reason            VARCHAR(255) NOT NULL,
    reference_number  VARCHAR(50),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | RELEASED | EXPIRED
    expiry_date       DATE,
    released_at       TIMESTAMPTZ,
    released_by       VARCHAR(100),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_hold_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_hold_status          CHECK (status IN ('ACTIVE','RELEASED','EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_account_holds_account_id ON account_holds (account_id);
CREATE INDEX IF NOT EXISTS idx_account_holds_status     ON account_holds (status);
CREATE INDEX IF NOT EXISTS idx_account_holds_expiry     ON account_holds (expiry_date) WHERE status = 'ACTIVE';

-- Dormancy CoB job history will appear automatically via CobJobHistory inserts.
-- No seed data needed — the job name is registered in CobSchedulerConfig.
