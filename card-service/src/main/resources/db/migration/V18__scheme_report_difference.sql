-- ─────────────────────────────────────────────────────────────────────────────
-- V18 — Scheme settlement report differences (backlog 15d, decision D7a)
-- ─────────────────────────────────────────────────────────────────────────────
-- A scheme's report no longer has to equal the expected net exactly: card-service sends both figures
-- to the backend, which records the report when reported − expected is within the currency's
-- tolerance (app.cards.settlement.difference-tolerance) and posts the difference to GL 1004 Card
-- Settlement Differences. The recorded report keeps that difference. Earlier reports matched exactly.
ALTER TABLE scheme_settlement_reports
    ADD COLUMN IF NOT EXISTS difference NUMERIC(19,4) NOT NULL DEFAULT 0;
