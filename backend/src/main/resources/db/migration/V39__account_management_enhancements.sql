-- Performance index for filtering transactions by type (e.g. INTEREST_CREDIT for interest tab)
CREATE INDEX IF NOT EXISTS idx_transactions_account_type
    ON transactions (account_id, transaction_type, transaction_date DESC);

-- Index for listing interest-only transactions quickly across all accounts (CoB reporting)
CREATE INDEX IF NOT EXISTS idx_transactions_type_date
    ON transactions (transaction_type, transaction_date DESC);
