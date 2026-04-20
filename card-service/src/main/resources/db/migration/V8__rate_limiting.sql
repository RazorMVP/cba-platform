-- ── Rate Limiting — API Key Tier ───────────────────────────────────────────
-- Adds a tier column to api_keys so each key carries its own rate limit band.
-- Sandbox keys (sk_test_ prefix, managed at application layer) default to SANDBOX.
-- Production keys default to BASIC and can be upgraded by ADMIN.

ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS tier VARCHAR(20) NOT NULL DEFAULT 'BASIC';

CREATE INDEX IF NOT EXISTS idx_api_keys_tier ON api_keys (tier);

-- Update any pre-existing test/sandbox key names to SANDBOX tier if identifiable.
-- (Conservative: only updates keys whose name contains 'sandbox' or 'test'.)
UPDATE api_keys SET tier = 'SANDBOX'
WHERE active = TRUE
  AND LOWER(name) LIKE '%sandbox%' OR LOWER(name) LIKE '%test%';
