-- Backlog 17 (set 1, PR B): card authorizations in a foreign currency.
-- When the scheme sends the cardholder billing amount (DE6) and its currency (DE51), the card's money
-- moves in that amount and currency; DE4/DE49 (amount / currency_code) stay the merchant's transaction.
ALTER TABLE authorization_log
    ADD COLUMN IF NOT EXISTS billing_amount        NUMERIC(19,4),   -- DE6, minor units of billing_currency_code
    ADD COLUMN IF NOT EXISTS billing_currency_code VARCHAR(3),      -- DE51, ISO 4217 numeric
    ADD COLUMN IF NOT EXISTS conversion_rate       VARCHAR(8);      -- DE10 as received (first digit = decimal places), audit only
