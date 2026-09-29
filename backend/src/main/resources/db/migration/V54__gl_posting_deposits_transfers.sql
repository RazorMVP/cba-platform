-- ─────────────────────────────────────────────────────────────────────────────
-- V54 — GL accounts and settings for posting deposits, withdrawals, transfers,
--       reversals and teller cash to the general ledger
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Accounts the seeded chart of accounts (V8) lacks. Added only where the V8
--    chart exists (its "Assets" header row), so a bank running its own chart is
--    untouched and maps these activities on the Financial Activity Accounts screen.
--    Posting rejects a transaction whose activity has no mapping.
--
--    1102 Overdrafts: an overdrawn deposit account is a loan to the customer, an
--         asset, not a negative deposit (FFIEC Call Report RC-E; IAS 32 §42 allows
--         offsetting only with a legal right and intent to settle net).
--    1200/1201 FX position / position equivalent: the two sides of a cross-currency
--         transfer, recorded at the spot rate (IAS 21 §21). Position is kept in each
--         foreign currency, its equivalent in the functional currency.
--    4003 Net FX gains: exchange differences go to profit or loss (IAS 21 §28) and
--         are disclosed (IAS 21 §52).
--    5003 Cash over and short: teller count differences at session close.
--    Position and overdraft accounts are sub-ledger controls: no manual journals.
INSERT INTO gl_accounts (id, name, gl_code, account_type, usage, parent_id, description, manual_entries_allowed)
SELECT v.id::uuid, v.name, v.gl_code, v.account_type, 'DETAIL', v.parent_id::uuid, v.description, v.manual
FROM (VALUES
    ('30000000-0000-0000-0000-000000000006', 'Overdrafts (Loans and Advances to Customers)', '1102', 'ASSET',
     '30000000-0000-0000-0000-000000000001', 'Debit balances on overdrawn deposit accounts', FALSE),
    ('30000000-0000-0000-0000-000000000007', 'Foreign Exchange Position', '1200', 'ASSET',
     '30000000-0000-0000-0000-000000000001', 'Cross-currency position, held in each foreign currency', FALSE),
    ('30000000-0000-0000-0000-000000000008', 'Foreign Exchange Position Equivalent', '1201', 'ASSET',
     '30000000-0000-0000-0000-000000000001', 'Functional-currency equivalent of the FX position', FALSE),
    ('30000000-0000-0000-0000-000000000033', 'Net Foreign Exchange Gains', '4003', 'INCOME',
     '30000000-0000-0000-0000-000000000030', 'Realised and revaluation exchange differences (IAS 21 §28)', TRUE),
    ('30000000-0000-0000-0000-000000000043', 'Cash Over and Short', '5003', 'EXPENSE',
     '30000000-0000-0000-0000-000000000040', 'Teller cash count differences at session close', TRUE)
) AS v(id, name, gl_code, account_type, parent_id, description, manual)
WHERE EXISTS (SELECT 1 FROM gl_accounts WHERE id = '30000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

-- 2. Map the new activities, only to the rows inserted above (matched by id, so a
--    bank's own account that happens to use the same code is never picked up).
INSERT INTO financial_activity_accounts (financial_activity, gl_account_id)
SELECT m.activity, g.id
FROM (VALUES
    ('ASSET_OVERDRAFT_PORTFOLIO',     '30000000-0000-0000-0000-000000000006'),
    ('ASSET_FX_POSITION',             '30000000-0000-0000-0000-000000000007'),
    ('ASSET_FX_POSITION_EQUIVALENT',  '30000000-0000-0000-0000-000000000008'),
    ('INCOME_FX_GAIN_LOSS',           '30000000-0000-0000-0000-000000000033'),
    ('EXPENSE_CASH_OVER_SHORT',       '30000000-0000-0000-0000-000000000043')
) AS m(activity, gl_id)
JOIN gl_accounts g ON g.id = m.gl_id::uuid
ON CONFLICT (financial_activity) DO NOTHING;

-- 3. The ledger's functional currency (IAS 21 §8, §17: one per entity). Until now it
--    was derived per request from the X-Tenant-ID header, so two requests could value
--    the same FX position in different currencies. Seeded from the default tenant.
--    Trap door: changing it later is a change of functional currency (IAS 21 §35),
--    not a settings edit.
INSERT INTO global_configurations (name, string_value, is_enabled, trap_door, description)
SELECT 'functional-currency',
       COALESCE((SELECT currency_code FROM tenants WHERE code = 'DEFAULT'), 'USD'),
       TRUE, TRUE,
       'Functional currency of the general ledger (IAS 21). FX positions are valued in it.'
ON CONFLICT (name) DO NOTHING;
