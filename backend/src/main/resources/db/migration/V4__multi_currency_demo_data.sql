-- ═══════════════════════════════════════════════════════════════════
-- V4__multi_currency_demo_data.sql
-- Three tenant deployments showing multi-currency capability:
--   Tenant 1: CBA United States (USD) — existing, enriched
--   Tenant 2: CBA Kenya          (KES) — new
--   Tenant 3: CBA Ghana          (GHS) — new
-- ═══════════════════════════════════════════════════════════════════

-- ── Rename / update existing DEFAULT tenant ───────────────────────────
UPDATE tenants SET
    name          = 'CBA United States',
    currency_code = 'USD',
    country_code  = 'US',
    locale_code   = 'en-US'
WHERE code = 'DEFAULT';

-- ── New tenants ───────────────────────────────────────────────────────
INSERT INTO tenants (id, code, name, currency_code, country_code, locale_code, active)
VALUES
    ('00000000-0000-0000-0000-000000000002',
     'KE', 'CBA Kenya', 'KES', 'KE', 'sw-KE', TRUE),

    ('00000000-0000-0000-0000-000000000003',
     'GH', 'CBA Ghana', 'GHS', 'GH', 'en-GH', TRUE);

-- ── Exchange rates (admin-managed: 1 from = rate to) ─────────────────
INSERT INTO exchange_rates (from_currency, to_currency, rate, created_by)
VALUES
    -- USD ↔ KES
    ('USD', 'KES', 135.50000000, 'system'),
    ('KES', 'USD',   0.00738010, 'system'),

    -- USD ↔ GHS
    ('USD', 'GHS',  15.80000000, 'system'),
    ('GHS', 'USD',   0.06329110, 'system'),

    -- KES ↔ GHS
    ('KES', 'GHS',   0.11660000, 'system'),
    ('GHS', 'KES',   8.57700000, 'system'),

    -- USD ↔ EUR (common reference)
    ('USD', 'EUR',   0.92500000, 'system'),
    ('EUR', 'USD',   1.08100000, 'system'),

    -- USD ↔ GBP
    ('USD', 'GBP',   0.79200000, 'system'),
    ('GBP', 'USD',   1.26300000, 'system');

-- ═══════════════════════════════════════════════════════════════════
-- KES TENANT — CBA Kenya
-- ═══════════════════════════════════════════════════════════════════

-- Deposit products (KES)
INSERT INTO deposit_products
    (id, tenant_id, name, description, account_type, currency_code,
     minimum_balance, interest_rate, interest_compounding)
VALUES
    ('10000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000002',
     'Akaunti ya Akiba', 'Basic savings account — KES',
     'SAVINGS', 'KES', 1000.00, 3.50, 'MONTHLY'),

    ('10000000-0000-0000-0000-000000000005',
     '00000000-0000-0000-0000-000000000002',
     'Akaunti ya Sasa', 'Current/checking account — KES',
     'CHECKING', 'KES', 0.00, 0.00, 'MONTHLY'),

    ('10000000-0000-0000-0000-000000000006',
     '00000000-0000-0000-0000-000000000002',
     'Amana ya Muda', '12-month fixed deposit — KES',
     'FIXED_DEPOSIT', 'KES', 10000.00, 7.00, 'ANNUALLY');

-- Loan products (KES)
INSERT INTO loan_products
    (id, tenant_id, name, description, currency_code,
     min_principal, max_principal,
     min_interest_rate, max_interest_rate, default_interest_rate,
     min_term_months, max_term_months, repayment_type,
     origination_fee, late_payment_fee)
VALUES
    ('20000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000002',
     'Mkopo wa Kibinafsi', 'Personal loan — KES',
     'KES', 10000.00, 5000000.00, 10.00, 28.00, 18.00,
     3, 60, 'ANNUITY', 500.00, 300.00),

    ('20000000-0000-0000-0000-000000000005',
     '00000000-0000-0000-0000-000000000002',
     'Mkopo wa Biashara', 'Business loan — KES',
     'KES', 50000.00, 50000000.00, 8.00, 20.00, 13.00,
     6, 84, 'ANNUITY', 2500.00, 1000.00);

-- Customers (KES tenant — PII placeholder encrypted values)
INSERT INTO customers
    (id, tenant_id, external_id,
     first_name_encrypted, last_name_encrypted,
     email_encrypted, phone_encrypted,
     date_of_birth, kyc_status, created_by)
VALUES
    ('30000000-0000-0000-0000-000000000004',
     '00000000-0000-0000-0000-000000000002',
     'KE-CUST-000001',
     'DEMO_ENC:Amina', 'DEMO_ENC:Wanjiru',
     'DEMO_ENC:amina.w@cba.co.ke', 'DEMO_ENC:+254-700-100001',
     '1988-05-12', 'ACTIVE', 'system'),

    ('30000000-0000-0000-0000-000000000005',
     '00000000-0000-0000-0000-000000000002',
     'KE-CUST-000002',
     'DEMO_ENC:Kamau', 'DEMO_ENC:Njoroge',
     'DEMO_ENC:k.njoroge@cba.co.ke', 'DEMO_ENC:+254-700-100002',
     '1975-09-30', 'ACTIVE', 'system');

-- Accounts (KES)
INSERT INTO accounts
    (id, tenant_id, account_number, customer_id, product_id,
     account_type, status, balance, currency_code, opened_date, created_by)
VALUES
    ('40000000-0000-0000-0000-000000000005',
     '00000000-0000-0000-0000-000000000002',
     '002-SAV-0000001',
     '30000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000004',
     'SAVINGS', 'ACTIVE', 185000.00, 'KES', '2022-06-01', 'system'),

    ('40000000-0000-0000-0000-000000000006',
     '00000000-0000-0000-0000-000000000002',
     '002-CHK-0000001',
     '30000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000005',
     'CHECKING', 'ACTIVE', 42500.00, 'KES', '2022-06-01', 'system'),

    -- Amina also has a USD foreign currency account (teller override)
    ('40000000-0000-0000-0000-000000000007',
     '00000000-0000-0000-0000-000000000002',
     '002-SAV-0000002',
     '30000000-0000-0000-0000-000000000004',
     '10000000-0000-0000-0000-000000000004',
     'SAVINGS', 'ACTIVE', 500.00, 'USD', '2023-01-15', 'system'),

    ('40000000-0000-0000-0000-000000000008',
     '00000000-0000-0000-0000-000000000002',
     '002-SAV-0000003',
     '30000000-0000-0000-0000-000000000005',
     '10000000-0000-0000-0000-000000000004',
     'SAVINGS', 'ACTIVE', 320000.00, 'KES', '2021-03-10', 'system');

UPDATE account_number_sequences SET last_sequence = 3 WHERE branch_code = '001' AND account_type = 'SAV';

INSERT INTO account_number_sequences (branch_code, account_type, last_sequence)
VALUES
    ('002', 'SAV', 3),
    ('002', 'CHK', 1),
    ('002', 'LN',  0),
    ('003', 'SAV', 0),
    ('003', 'CHK', 0),
    ('003', 'LN',  0)
ON CONFLICT (branch_code, account_type) DO NOTHING;

-- KES loan
INSERT INTO loans
    (id, tenant_id, loan_account_number, customer_id, product_id,
     linked_account_id, principal_amount, approved_amount, outstanding_balance,
     interest_rate, term_months, status,
     application_date, approval_date, disbursement_date, maturity_date,
     approved_by, created_by)
VALUES
    ('60000000-0000-0000-0000-000000000002',
     '00000000-0000-0000-0000-000000000002',
     '002-LN-0000001',
     '30000000-0000-0000-0000-000000000005',
     '20000000-0000-0000-0000-000000000004',
     '40000000-0000-0000-0000-000000000008',
     500000.00, 500000.00, 392115.50,
     18.00, 24, 'ACTIVE',
     '2023-09-01', '2023-09-03', '2023-09-05', '2025-09-05',
     'system', 'system');

UPDATE account_number_sequences SET last_sequence = 1 WHERE branch_code = '002' AND account_type = 'LN';

-- ═══════════════════════════════════════════════════════════════════
-- GHS TENANT — CBA Ghana
-- ═══════════════════════════════════════════════════════════════════

INSERT INTO deposit_products
    (id, tenant_id, name, description, account_type, currency_code,
     minimum_balance, interest_rate, interest_compounding)
VALUES
    ('10000000-0000-0000-0000-000000000007',
     '00000000-0000-0000-0000-000000000003',
     'Savings Account', 'Basic savings account — GHS',
     'SAVINGS', 'GHS', 50.00, 4.00, 'MONTHLY'),

    ('10000000-0000-0000-0000-000000000008',
     '00000000-0000-0000-0000-000000000003',
     'Current Account', 'Current/checking account — GHS',
     'CHECKING', 'GHS', 0.00, 0.00, 'MONTHLY');

INSERT INTO loan_products
    (id, tenant_id, name, description, currency_code,
     min_principal, max_principal,
     min_interest_rate, max_interest_rate, default_interest_rate,
     min_term_months, max_term_months, repayment_type,
     origination_fee, late_payment_fee)
VALUES
    ('20000000-0000-0000-0000-000000000006',
     '00000000-0000-0000-0000-000000000003',
     'Personal Loan', 'Personal loan — GHS',
     'GHS', 500.00, 500000.00, 15.00, 35.00, 22.00,
     3, 48, 'ANNUITY', 50.00, 25.00);

INSERT INTO customers
    (id, tenant_id, external_id,
     first_name_encrypted, last_name_encrypted,
     email_encrypted, phone_encrypted,
     date_of_birth, kyc_status, created_by)
VALUES
    ('30000000-0000-0000-0000-000000000006',
     '00000000-0000-0000-0000-000000000003',
     'GH-CUST-000001',
     'DEMO_ENC:Kofi', 'DEMO_ENC:Mensah',
     'DEMO_ENC:k.mensah@cba.com.gh', 'DEMO_ENC:+233-24-100001',
     '1992-03-18', 'ACTIVE', 'system');

INSERT INTO accounts
    (id, tenant_id, account_number, customer_id, product_id,
     account_type, status, balance, currency_code, opened_date, created_by)
VALUES
    ('40000000-0000-0000-0000-000000000009',
     '00000000-0000-0000-0000-000000000003',
     '003-SAV-0000001',
     '30000000-0000-0000-0000-000000000006',
     '10000000-0000-0000-0000-000000000007',
     'SAVINGS', 'ACTIVE', 8500.00, 'GHS', '2023-05-20', 'system'),

    ('40000000-0000-0000-0000-000000000010',
     '00000000-0000-0000-0000-000000000003',
     '003-CHK-0000001',
     '30000000-0000-0000-0000-000000000006',
     '10000000-0000-0000-0000-000000000008',
     'CHECKING', 'ACTIVE', 1200.00, 'GHS', '2023-05-20', 'system');

INSERT INTO account_number_sequences (branch_code, account_type, last_sequence)
VALUES ('003', 'SAV', 1), ('003', 'CHK', 1)
ON CONFLICT (branch_code, account_type) DO UPDATE SET last_sequence = EXCLUDED.last_sequence;
