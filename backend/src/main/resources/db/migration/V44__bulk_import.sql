-- V44: Bulk import job history table

CREATE TABLE IF NOT EXISTS bulk_import_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50)  NOT NULL,  -- CUSTOMERS | LOANS
    file_name       VARCHAR(255) NOT NULL,
    total_rows      INT          NOT NULL DEFAULT 0,
    success_count   INT          NOT NULL DEFAULT 0,
    failure_count   INT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',  -- COMPLETED | PARTIAL | FAILED
    error_summary   TEXT,
    imported_by     VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_bulk_import_entity ON bulk_import_jobs (entity_type, created_at DESC);
