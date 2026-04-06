-- ═══════════════════════════════════════════════════════════════════
-- V2__demo_data.sql — Demo data for development and testing
-- DO NOT run in production (Flyway will run this automatically in dev)
-- Credentials: admin@cba.com / Admin@123 | teller@cba.com / Teller@123
-- ═══════════════════════════════════════════════════════════════════

-- ── Default tenant ───────────────────────────────────────────────────
INSERT INTO tenants (id, code, name, active)
VALUES ('00000000-0000-0000-0000-000000000001', 'DEFAULT', 'CBA Bank', TRUE);

-- ── Deposit Products ─────────────────────────────────────────────────
INSERT INTO deposit_products (id, name, description, account_type, currency_code,
    minimum_balance, interest_rate, interest_compounding)
VALUES
    ('10000000-0000-0000-0000-000000000001',
     'Standard Savings', 'Basic savings account with monthly interest',
     'SAVINGS', 'USD', 100.00, 2.50, 'MONTHLY'),

    ('10000000-0000-0000-0000-000000000002',
     'Current Account', 'Checking account for everyday transactions',
     'CHECKING', 'USD', 0.00, 0.00, 'MONTHLY'),

    ('10000000-0000-0000-0000-000000000003',
     '12-Month Fixed Deposit', 'Fixed deposit with guaranteed annual return',
     'FIXED_DEPOSIT', 'USD', 1000.00, 5.00, 'ANNUALLY');

-- ── Loan Products ────────────────────────────────────────────────────
INSERT INTO loan_products (id, name, description, currency_code,
    min_principal, max_principal,
    min_interest_rate, max_interest_rate, default_interest_rate,
    min_term_months, max_term_months, repayment_type,
    origination_fee, late_payment_fee)
VALUES
    ('20000000-0000-0000-0000-000000000001',
     'Personal Loan', 'Unsecured personal loan for any purpose',
     'USD', 500.00, 50000.00, 8.00, 24.00, 14.99,
     6, 60, 'ANNUITY', 50.00, 25.00),

    ('20000000-0000-0000-0000-000000000002',
     'Business Loan', 'Working capital and business expansion',
     'USD', 5000.00, 500000.00, 6.00, 18.00, 10.50,
     12, 84, 'ANNUITY', 250.00, 100.00),

    ('20000000-0000-0000-0000-000000000003',
     'Home Loan', 'Long-term mortgage for residential property',
     'USD', 50000.00, 2000000.00, 4.00, 10.00, 6.50,
     60, 360, 'ANNUITY', 500.00, 150.00);

-- ── Customers (PII columns contain placeholder encrypted values) ──────
-- NOTE: In a real scenario, these would be encrypted by the application.
-- The placeholder values below will be replaced when the app writes real data.
-- These are synthetic demo strings — not real encrypted ciphertext.
INSERT INTO customers (id, external_id,
    first_name_encrypted, last_name_encrypted,
    email_encrypted, phone_encrypted,
    date_of_birth, kyc_status, created_by)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'CUST-000001',
     'DEMO_ENC:John', 'DEMO_ENC:Doe',
     'DEMO_ENC:john.doe@example.com', 'DEMO_ENC:+1-555-0100',
     '1985-03-15', 'ACTIVE', 'system'),

    ('30000000-0000-0000-0000-000000000002', 'CUST-000002',
     'DEMO_ENC:Jane', 'DEMO_ENC:Smith',
     'DEMO_ENC:jane.smith@example.com', 'DEMO_ENC:+1-555-0200',
     '1990-07-22', 'ACTIVE', 'system'),

    ('30000000-0000-0000-0000-000000000003', 'CUST-000003',
     'DEMO_ENC:Robert', 'DEMO_ENC:Johnson',
     'DEMO_ENC:robert.j@example.com', 'DEMO_ENC:+1-555-0300',
     '1978-11-08', 'PENDING_KYC', 'system');

-- ── Accounts ─────────────────────────────────────────────────────────
INSERT INTO accounts (id, account_number, customer_id, product_id,
    account_type, status, balance, currency_code, opened_date, created_by)
VALUES
    -- John Doe's savings
    ('40000000-0000-0000-0000-000000000001', '001-SAV-0000001',
     '30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
     'SAVINGS', 'ACTIVE', 15420.75, 'USD', '2022-01-10', 'system'),

    -- John Doe's checking
    ('40000000-0000-0000-0000-000000000002', '001-CHK-0000001',
     '30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
     'CHECKING', 'ACTIVE', 3250.00, 'USD', '2022-01-10', 'system'),

    -- Jane Smith's savings
    ('40000000-0000-0000-0000-000000000003', '001-SAV-0000002',
     '30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001',
     'SAVINGS', 'ACTIVE', 8900.50, 'USD', '2023-03-05', 'system'),

    -- Jane Smith's checking
    ('40000000-0000-0000-0000-000000000004', '001-CHK-0000002',
     '30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002',
     'CHECKING', 'ACTIVE', 1200.00, 'USD', '2023-03-05', 'system');

-- Update account number sequences to reflect demo data
UPDATE account_number_sequences SET last_sequence = 2 WHERE account_type = 'SAV';
UPDATE account_number_sequences SET last_sequence = 2 WHERE account_type = 'CHK';

-- ── Sample Transactions ───────────────────────────────────────────────
INSERT INTO transactions (id, account_id, transaction_type, amount,
    running_balance, currency_code, description, reference_number, value_date, created_by)
VALUES
    -- John opening deposit
    ('50000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000001',
     'DEPOSIT', 10000.00, 10000.00, 'USD',
     'Initial deposit', 'REF-000001', '2022-01-10', 'system'),

    -- John salary credit
    ('50000000-0000-0000-0000-000000000002',
     '40000000-0000-0000-0000-000000000001',
     'DEPOSIT', 5500.00, 15500.00, 'USD',
     'Salary credit - March 2024', 'REF-000002', '2024-03-01', 'system'),

    -- John interest credit
    ('50000000-0000-0000-0000-000000000003',
     '40000000-0000-0000-0000-000000000001',
     'INTEREST_CREDIT', 32.25, 15532.25, 'USD',
     'Monthly interest', 'REF-000003', '2024-03-31', 'system'),

    -- John card withdrawal (brings to current 15420.75)
    ('50000000-0000-0000-0000-000000000004',
     '40000000-0000-0000-0000-000000000001',
     'WITHDRAWAL', 111.50, 15420.75, 'USD',
     'ATM withdrawal', 'REF-000004', '2024-04-01', 'system');

-- ── Demo Loan (active) ────────────────────────────────────────────────
INSERT INTO loans (id, loan_account_number, customer_id, product_id,
    linked_account_id, principal_amount, approved_amount, outstanding_balance,
    interest_rate, term_months, status,
    application_date, approval_date, disbursement_date, maturity_date,
    approved_by, created_by)
VALUES
    ('60000000-0000-0000-0000-000000000001', '001-LN-0000001',
     '30000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000002',
     10000.00, 10000.00, 7842.31,
     14.99, 24, 'ACTIVE',
     '2023-06-01', '2023-06-03', '2023-06-05', '2025-06-05',
     'system', 'system');

UPDATE account_number_sequences SET last_sequence = 1 WHERE account_type = 'LN';

-- ── Repayment Schedule (first 3 of 24 installments, rest abbreviated) ─
INSERT INTO loan_repayment_schedule (loan_id, installment_no,
    due_date, principal_due, interest_due, fees_due, total_due,
    principal_paid, interest_paid, fees_paid, total_paid, status, paid_date)
VALUES
    ('60000000-0000-0000-0000-000000000001', 1,
     '2023-07-05', 368.03, 124.92, 0, 492.95,
     368.03, 124.92, 0, 492.95, 'PAID', '2023-07-03'),

    ('60000000-0000-0000-0000-000000000001', 2,
     '2023-08-05', 372.62, 120.33, 0, 492.95,
     372.62, 120.33, 0, 492.95, 'PAID', '2023-08-04'),

    ('60000000-0000-0000-0000-000000000001', 3,
     '2023-09-05', 377.26, 115.69, 0, 492.95,
     377.26, 115.69, 0, 492.95, 'PAID', '2023-09-05'),

    -- Current due (installment 4 onwards marked PENDING for demo)
    ('60000000-0000-0000-0000-000000000001', 4,
     '2024-04-05', 381.97, 110.98, 0, 492.95,
     0, 0, 0, 0, 'PENDING', NULL),

    ('60000000-0000-0000-0000-000000000001', 5,
     '2024-05-05', 386.74, 106.21, 0, 492.95,
     0, 0, 0, 0, 'PENDING', NULL);

-- ── Demo Payment ──────────────────────────────────────────────────────
INSERT INTO payments (id, reference_number, payment_type,
    source_account_id, destination_account_id,
    amount, currency_code, description, status, executed_date, created_by)
VALUES
    ('70000000-0000-0000-0000-000000000001', 'PAY-000001', 'INTERNAL_TRANSFER',
     '40000000-0000-0000-0000-000000000001',
     '40000000-0000-0000-0000-000000000003',
     500.00, 'USD', 'Transfer to Jane Smith', 'COMPLETED',
     now() - INTERVAL '7 days', 'system');
