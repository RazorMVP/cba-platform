-- ============================================================
-- CBA Card Service — V5 Bureau Module
-- ============================================================
-- Tables: bureau_jobs, bureau_job_items
-- Purpose: Track card personalization batches sent to the
--          card production bureau (Thales / HID / Idemia).
-- ============================================================

-- ── Bureau Jobs (batch-level) ─────────────────────────────────────────────────
-- Each row represents one production batch submitted to the bureau.
-- A batch can contain many physical cards (bureau_job_items).
CREATE TABLE bureau_jobs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_ref       VARCHAR(40) NOT NULL UNIQUE,         -- bureau-assigned or internal batch reference
    bureau_name     VARCHAR(100) NOT NULL,                -- configured bureau name (e.g. "THALES_HID")
    card_count      INT          NOT NULL DEFAULT 0,      -- number of cards in this batch
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','SENT','CONFIRMED','FAILED')),
    submitted_at    TIMESTAMPTZ,                          -- when batch was transmitted to bureau
    confirmed_at    TIMESTAMPTZ,                          -- when bureau acknowledged production complete
    notes           TEXT,                                 -- ops notes / error detail on failure
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_bureau_jobs_status     ON bureau_jobs(status);
CREATE INDEX idx_bureau_jobs_batch_ref  ON bureau_jobs(batch_ref);

-- ── Bureau Job Items (per-card) ───────────────────────────────────────────────
-- One row per card in a bureau batch.
-- personalization_data_hash = SHA-256 of the full CDP record — used by bureau
-- to verify file integrity on receipt.  The actual CDP payload is transmitted
-- as an encrypted file attachment, never stored in the DB.
CREATE TABLE bureau_job_items (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id                      UUID        NOT NULL REFERENCES bureau_jobs(id),
    card_id                     UUID        NOT NULL REFERENCES cards(id),
    physical_order_id           UUID        NOT NULL REFERENCES physical_card_orders(id),
    personalization_data_hash   VARCHAR(64) NOT NULL,  -- SHA-256 hex of the CDP record bytes
    chip_serial_no              VARCHAR(30),            -- returned by bureau after production
    scheme_aid                  VARCHAR(32) NOT NULL,   -- EMV Application ID used for chip personalization
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','PERSONALIZED','FAILED')),
    failure_reason              TEXT,                   -- populated on FAILED
    version                     BIGINT      NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bureau_job_items_job_id  ON bureau_job_items(job_id);
CREATE INDEX idx_bureau_job_items_card_id ON bureau_job_items(card_id);
CREATE INDEX idx_bureau_job_items_status  ON bureau_job_items(status);
