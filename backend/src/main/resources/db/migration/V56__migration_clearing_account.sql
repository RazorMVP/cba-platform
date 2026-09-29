-- ─────────────────────────────────────────────────────────────────────────────
-- V56 — Legacy balance migration clearing account (opening-balance journal)
-- ─────────────────────────────────────────────────────────────────────────────
-- The opening-balance journal brings existing customer balances into the ledger
-- (ISA 510: prior closing balances "correctly brought forward"). Each balance is
-- offset against this clearing account; the rest of the legacy trial balance (cash,
-- nostro, capital, retained earnings) is then loaded against it as manual journals.
-- When migration is complete its balance must be zero in every currency — the same
-- control as SAP's "offsetting account for legacy data transfer". A remaining balance
-- would be an unexplained equity figure, so it is reported, never left in place.
--
-- Added only where the V8 chart exists (its "Equity" header row), like V54; a bank
-- running its own chart maps EQUITY_MIGRATION_CLEARING on the Financial Activity
-- Accounts screen. Manual journals are allowed: that is how the legacy balances land.

INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT '30000000-0000-0000-0000-000000000029'::uuid, 'Legacy Balance Migration Clearing', '3900',
       'EQUITY', 'DETAIL', '30000000-0000-0000-0000-000000000020'::uuid,
       'Offset for the opening-balance journal and legacy trial balance. Must be zero after migration.',
       TRUE
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = '30000000-0000-0000-0000-000000000020')
ON CONFLICT DO NOTHING;

INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT 'EQUITY_MIGRATION_CLEARING', g.id
FROM gl_accounts g
WHERE g.id = '30000000-0000-0000-0000-000000000029'
ON CONFLICT (financial_activity) DO NOTHING;
