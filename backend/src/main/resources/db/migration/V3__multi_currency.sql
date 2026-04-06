-- ═══════════════════════════════════════════════════════════════════
-- V3__multi_currency.sql — Multi-currency support
-- Changes:
--   1. Add currency_code to tenants (base/home currency per deployment)
--   2. Create exchange_rates table (admin-managed, simple rate table)
--   3. Extend payments with exchange rate audit columns
--   4. Add tenant_id index on accounts (performance for multi-tenant queries)
-- ═══════════════════════════════════════════════════════════════════

-- ── 1. Tenant base currency ───────────────────────────────────────────
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS currency_code CHAR(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN IF NOT EXISTS country_code  CHAR(2),
    ADD COLUMN IF NOT EXISTS locale_code   VARCHAR(10) DEFAULT 'en-US';

-- Set the default tenant to USD
UPDATE tenants SET currency_code = 'USD', country_code = 'US', locale_code = 'en-US'
WHERE code = 'DEFAULT';

-- ── 2. Exchange rates (admin-managed, simple table) ───────────────────
-- Convention: 1 from_currency = rate to_currency
-- Example: from=USD, to=KES, rate=135.00 means 1 USD = 135 KES
CREATE TABLE IF NOT EXISTS exchange_rates (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency   CHAR(3)         NOT NULL,
    to_currency     CHAR(3)         NOT NULL,
    rate            NUMERIC(19, 8)  NOT NULL CHECK (rate > 0),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_exchange_pair UNIQUE (from_currency, to_currency)
);

CREATE INDEX IF NOT EXISTS idx_exchange_rates_pair
    ON exchange_rates(from_currency, to_currency)
    WHERE active = TRUE;

-- ── 3. Extend payments for cross-currency audit trail ─────────────────
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS source_currency       CHAR(3),
    ADD COLUMN IF NOT EXISTS source_amount         NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS destination_currency  CHAR(3),
    ADD COLUMN IF NOT EXISTS destination_amount    NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS exchange_rate_used    NUMERIC(19, 8),
    ADD COLUMN IF NOT EXISTS is_cross_currency     BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill existing payments (all are same-currency USD)
UPDATE payments SET
    source_currency       = 'USD',
    source_amount         = amount,
    destination_currency  = 'USD',
    destination_amount    = amount,
    is_cross_currency     = FALSE
WHERE source_currency IS NULL;

-- Make source/destination currency NOT NULL after backfill
ALTER TABLE payments
    ALTER COLUMN source_currency      SET NOT NULL,
    ALTER COLUMN destination_currency SET NOT NULL;

-- ── 4. Supported currencies reference table ───────────────────────────
CREATE TABLE IF NOT EXISTS supported_currencies (
    code            CHAR(3)      PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    symbol          VARCHAR(10)  NOT NULL,
    decimal_places  INT          NOT NULL DEFAULT 2,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO supported_currencies (code, name, symbol, decimal_places) VALUES
    ('USD', 'US Dollar',               '$',    2),
    ('EUR', 'Euro',                    '€',    2),
    ('GBP', 'British Pound',           '£',    2),
    ('KES', 'Kenyan Shilling',         'KSh',  2),
    ('GHS', 'Ghanaian Cedi',           'GH₵',  2),
    ('NGN', 'Nigerian Naira',          '₦',    2),
    ('ZAR', 'South African Rand',      'R',    2),
    ('UGX', 'Ugandan Shilling',        'USh',  0),
    ('TZS', 'Tanzanian Shilling',      'TSh',  2),
    ('RWF', 'Rwandan Franc',           'RF',   0),
    ('ETB', 'Ethiopian Birr',          'Br',   2),
    ('XOF', 'West African CFA Franc',  'CFA',  0),
    ('JPY', 'Japanese Yen',            '¥',    0),
    ('CNY', 'Chinese Yuan',            '¥',    2),
    ('INR', 'Indian Rupee',            '₹',    2),
    ('AUD', 'Australian Dollar',       'A$',   2),
    ('CAD', 'Canadian Dollar',         'CA$',  2),
    ('CHF', 'Swiss Franc',             'CHF',  2)
ON CONFLICT (code) DO NOTHING;
