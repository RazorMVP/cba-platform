-- ─────────────────────────────────────────────────────────────────────────────
-- V7 — Settlement File Export Framework
--
-- Tracks every file transmission attempt to a scheme clearinghouse network.
-- One record per (batch × scheme × attempt). Supports retry, idempotency,
-- and audit trail for scheme reconciliation disputes.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE settlement_transmissions (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The settlement batch this transmission covers
    batch_id            UUID         NOT NULL REFERENCES settlement_batches(id),

    -- Scheme this file is destined for
    scheme              VARCHAR(20)  NOT NULL,  -- VISA/MASTERCARD/VERVE/AFRIGO/UNIONPAY

    -- File metadata
    file_name           VARCHAR(100) NOT NULL,  -- scheme-mandated filename
    record_count        INT          NOT NULL DEFAULT 0,
    total_amount        NUMERIC(19,4) NOT NULL DEFAULT 0,
    settlement_date     DATE         NOT NULL,

    -- Transmission infrastructure
    transmission_method VARCHAR(10)  NOT NULL DEFAULT 'SFTP',  -- SFTP/HTTPS
    endpoint            VARCHAR(255),           -- scheme host/URL (from config at time of send)

    -- Status lifecycle: PENDING → TRANSMITTED → ACKNOWLEDGED | FAILED
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count       INT          NOT NULL DEFAULT 0,
    last_attempt_at     TIMESTAMPTZ,
    transmitted_at      TIMESTAMPTZ,            -- when file successfully reached scheme server
    acknowledged_at     TIMESTAMPTZ,            -- when scheme sent ACK (async, if applicable)
    error_message       TEXT,                   -- last failure reason (overwritten on retry)

    -- Audit
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_transmissions_batch    ON settlement_transmissions(batch_id);
CREATE INDEX idx_transmissions_scheme   ON settlement_transmissions(scheme);
CREATE INDEX idx_transmissions_status   ON settlement_transmissions(status);
CREATE INDEX idx_transmissions_date     ON settlement_transmissions(settlement_date);

-- Prevent duplicate successful transmissions of the same batch+scheme
CREATE UNIQUE INDEX idx_transmissions_batch_scheme_success
    ON settlement_transmissions(batch_id, scheme)
    WHERE status = 'TRANSMITTED';
