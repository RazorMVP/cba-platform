-- ─────────────────────────────────────────────────────────────────────────────
-- V22 — Prepaid wallet limits per card product (backlog 15c, decision D4a)
-- ─────────────────────────────────────────────────────────────────────────────
-- CBN Revised Guidelines on Stored Value/Prepaid Card Issuance and Operations (2012):
-- a stored-value card holds at most N250,000 and loads at most N50,000 a day (§4.2, §4.3);
-- a prepaid card stays within those limits until the holder has full KYC (§5.2), above which
-- the issuer sets them (§5.1, §5.3). card-service applies the per-currency defaults in
-- card.prepaid.limits (NGN 250000 / 50000); these columns override them for a product
-- (for example a full-KYC product). Major units, in the wallet's currency; NULL = use the
-- currency default. Enforced on loads only — money coming back is never refused (§3.7).
ALTER TABLE card_products ADD COLUMN IF NOT EXISTS max_wallet_balance NUMERIC(19,4)
    CHECK (max_wallet_balance IS NULL OR max_wallet_balance > 0);
ALTER TABLE card_products ADD COLUMN IF NOT EXISTS daily_wallet_load NUMERIC(19,4)
    CHECK (daily_wallet_load IS NULL OR daily_wallet_load > 0);

-- A card's loads by time: today's (the daily limit) and its pending ones (the balance limit).
CREATE INDEX IF NOT EXISTS idx_wallet_movements_card_loads
    ON wallet_movements (card_id, created_at) WHERE movement_type = 'LOAD' AND status <> 'FAILED';
