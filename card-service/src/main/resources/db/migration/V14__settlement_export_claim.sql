-- V14 — Settlement file export: one live transmission per batch+scheme, retries without a sleep (backlog 21).
-- The export used to retry inside one transaction with Thread.sleep between attempts (up to ~10 minutes
-- holding a connection), and only TRANSMITTED rows were unique — so the nightly run and a manual export could
-- both send the same file. Now inserting the PENDING row is the claim; a failed attempt goes back to PENDING
-- with next_retry_at and a scheduled poller sends it again.

ALTER TABLE settlement_transmissions ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

-- The old code committed PENDING only together with its outcome, so a PENDING row here was left by a run that
-- died mid-transaction. Whether its file reached the scheme isn't known: close it rather than resend it.
UPDATE settlement_transmissions
SET status = 'FAILED',
    error_message = 'INTERRUPTED before V14: confirm with the scheme whether ' || file_name
                    || ' arrived before re-exporting'
WHERE status = 'PENDING';

DROP INDEX IF EXISTS idx_transmissions_batch_scheme_success;

-- One live transmission per batch+scheme. FAILED rows don't count, so a manual export can try again.
CREATE UNIQUE INDEX idx_transmissions_batch_scheme_live
    ON settlement_transmissions (batch_id, scheme)
    WHERE status IN ('PENDING', 'SENDING', 'TRANSMITTED', 'ACKNOWLEDGED');

CREATE INDEX IF NOT EXISTS idx_transmissions_retry
    ON settlement_transmissions (next_retry_at)
    WHERE status = 'PENDING';
