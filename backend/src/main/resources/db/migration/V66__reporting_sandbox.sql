-- Backlog 10 (set 1, PR C): report SQL runs in a sandbox.
--
-- Report SQL (the 7 core reports, and ADMIN-written ones) runs as the login role cba_reporting, which
-- can read only this `reporting` schema: views with explicit columns — no names or other PII, no
-- secrets, hashes, tokens or free-text descriptions, none of the user/partner/audit/consent tables.
-- The views are owned by the migration user, so they read the base tables with its rights; the role
-- itself has no privilege on any table in `public`.
--
-- The role is created NOLOGIN here (no password in git). The backend gives it LOGIN and a password
-- from REPORTING_DB_PASSWORD at startup (ReportingRoleInitializer, SCRAM verifier computed client-side).
-- Creating and altering the role needs CREATEROLE: the migration user is the database owner/superuser
-- in every environment here.

CREATE SCHEMA IF NOT EXISTS reporting;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cba_reporting') THEN
        CREATE ROLE cba_reporting NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;
    END IF;
END $$;

-- Read-only, time-boxed, and `loans` means reporting.loans. Member of no other role, so
-- set_config('role', …) can't switch it to anything else.
ALTER ROLE cba_reporting SET default_transaction_read_only = on;
ALTER ROLE cba_reporting SET statement_timeout = '30s';
ALTER ROLE cba_reporting SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE cba_reporting SET search_path = reporting;

-- ── Views (explicit columns only) ──────────────────────────────────────────────

CREATE OR REPLACE VIEW reporting.loans AS
SELECT id, customer_id, product_id, loan_account_number, principal_amount, approved_amount,
       outstanding_balance, interest_rate, term_months, status, application_date, approval_date,
       disbursement_date, maturity_date, written_off_on, created_at
FROM public.loans;

CREATE OR REPLACE VIEW reporting.loan_repayment_schedule AS
SELECT id, loan_id, installment_no, due_date, principal_due, interest_due, fees_due, total_due,
       principal_paid, interest_paid, fees_paid, total_paid, status, paid_date
FROM public.loan_repayment_schedule;

CREATE OR REPLACE VIEW reporting.loan_products AS
SELECT id, name, short_name, currency_code, min_principal, max_principal, default_principal,
       min_interest_rate, max_interest_rate, default_interest_rate, min_term_months, max_term_months,
       repayment_type, active, created_at
FROM public.loan_products;

CREATE OR REPLACE VIEW reporting.deposit_products AS
SELECT id, name, short_name, account_type, currency_code, minimum_balance, interest_rate,
       interest_compounding, allow_overdraft, overdraft_limit, active, created_at
FROM public.deposit_products;

-- No account number (masked everywhere else).
CREATE OR REPLACE VIEW reporting.accounts AS
SELECT id, customer_id, product_id, account_type, status, balance, currency_code,
       opened_date, closed_date, last_transaction_date, created_at
FROM public.accounts;

-- No description (free text naming accounts) or reference.
CREATE OR REPLACE VIEW reporting.transactions AS
SELECT id, account_id, transaction_type, amount, running_balance, currency_code, payment_id,
       transaction_date, value_date
FROM public.transactions;

-- No beneficiary details, description or references.
CREATE OR REPLACE VIEW reporting.payments AS
SELECT id, payment_type, source_account_id, destination_account_id, amount, currency_code, status,
       scheduled_date, executed_date, is_cross_currency, source_currency, source_amount,
       destination_currency, destination_amount, exchange_rate_used, external_network, reversal_of,
       created_at
FROM public.payments;

-- No names, email, phone, national id, date of birth or notes.
CREATE OR REPLACE VIEW reporting.customers AS
SELECT id, external_id, kyc_status, office_id, staff_id, activation_date, closure_date, created_at
FROM public.customers;

CREATE OR REPLACE VIEW reporting.offices AS
SELECT id, name, external_id, parent_id, hierarchy, opening_date, status, active
FROM public.offices;

CREATE OR REPLACE VIEW reporting.tellers AS
SELECT id, name, branch_code, office_id, status, start_date, end_date
FROM public.tellers;

CREATE OR REPLACE VIEW reporting.teller_sessions AS
SELECT id, teller_id, cashier_id, session_date, opening_balance, closing_balance, actual_cash,
       difference, currency_code, status, opened_at, closed_at
FROM public.teller_sessions;

CREATE OR REPLACE VIEW reporting.cash_transactions AS
SELECT id, session_id, teller_id, cashier_id, account_id, transaction_type, amount, currency_code,
       transaction_date
FROM public.cash_transactions;

CREATE OR REPLACE VIEW reporting.gl_accounts AS
SELECT id, gl_code, name, account_type, usage, parent_id, manual_entries_allowed, disabled
FROM public.gl_accounts;

-- No description (names accounts) or submitter.
CREATE OR REPLACE VIEW reporting.journal_entries AS
SELECT id, transaction_id, office_id, gl_account_id, entry_type, amount, currency_code,
       transaction_date, entity_type, entity_id, is_reversed, reversal_id, manual_entry, created_at
FROM public.journal_entries;

GRANT USAGE ON SCHEMA reporting TO cba_reporting;
GRANT SELECT ON ALL TABLES IN SCHEMA reporting TO cba_reporting;

-- ── Core reports onto the views, parameters bound (:name, not pasted text) ─────

UPDATE reports SET report_sql = $$SELECT l.loan_account_number, l.customer_id, l.principal_amount,
       l.outstanding_balance, l.status, l.disbursement_date,
       next_due.due_date AS next_due_date, next_due.total_due AS next_amount_due
FROM loans l
LEFT JOIN LATERAL (
    SELECT lrs.due_date, lrs.total_due FROM loan_repayment_schedule lrs
    WHERE lrs.loan_id = l.id AND lrs.status NOT IN ('PAID', 'WAIVED') AND lrs.due_date >= CURRENT_DATE
    ORDER BY lrs.due_date LIMIT 1) next_due ON TRUE
WHERE l.status IN ('ACTIVE', 'IN_ARREARS')
ORDER BY l.disbursement_date DESC$$, updated_at = now()
WHERE report_name = 'ActiveLoans';

UPDATE reports SET report_sql = $$SELECT l.loan_account_number, l.customer_id, l.outstanding_balance,
       CURRENT_DATE - MIN(lrs.due_date) AS days_overdue,
       SUM(lrs.total_due - lrs.total_paid) AS amount_overdue,
       CASE
         WHEN CURRENT_DATE - MIN(lrs.due_date) <= 30 THEN '1-30 days'
         WHEN CURRENT_DATE - MIN(lrs.due_date) <= 60 THEN '31-60 days'
         WHEN CURRENT_DATE - MIN(lrs.due_date) <= 90 THEN '61-90 days'
         ELSE '90+ days'
       END AS ageing_bucket
FROM loans l
JOIN loan_repayment_schedule lrs ON lrs.loan_id = l.id
 AND lrs.due_date < CURRENT_DATE
 AND lrs.status NOT IN ('PAID', 'WAIVED')
WHERE l.status IN ('ACTIVE', 'IN_ARREARS')
GROUP BY l.id, l.loan_account_number, l.customer_id, l.outstanding_balance
ORDER BY days_overdue DESC$$, updated_at = now()
WHERE report_name = 'LoansInArrears';

-- Was joined to an arbitrary platform user's office (SELECT office_id FROM platform_users LIMIT 1);
-- now the account holder's office.
UPDATE reports SET report_sql = $$SELECT o.name AS branch, a.account_type, a.currency_code,
       COUNT(*) AS account_count, SUM(a.balance) AS total_balance
FROM accounts a
LEFT JOIN customers c ON c.id = a.customer_id
LEFT JOIN offices o ON o.id = c.office_id
WHERE a.status = 'ACTIVE'
GROUP BY o.name, a.account_type, a.currency_code
ORDER BY o.name, a.account_type$$, description = 'Active savings account balances by the account holder''s branch', updated_at = now()
WHERE report_name = 'SavingsBalance';

UPDATE reports SET report_sql = $$SELECT t.name AS teller_name, ts.session_date, ts.currency_code,
       ts.opening_balance,
       COALESCE(SUM(CASE WHEN ct.transaction_type = 'CASH_IN'  THEN ct.amount ELSE 0 END), 0) AS total_cash_in,
       COALESCE(SUM(CASE WHEN ct.transaction_type = 'CASH_OUT' THEN ct.amount ELSE 0 END), 0) AS total_cash_out,
       ts.opening_balance
         + COALESCE(SUM(CASE WHEN ct.transaction_type = 'CASH_IN'  THEN ct.amount ELSE 0 END), 0)
         - COALESCE(SUM(CASE WHEN ct.transaction_type = 'CASH_OUT' THEN ct.amount ELSE 0 END), 0) AS current_balance
FROM teller_sessions ts
JOIN tellers t ON t.id = ts.teller_id
LEFT JOIN cash_transactions ct ON ct.session_id = ts.id
WHERE ts.status = 'OPEN'
GROUP BY t.name, ts.session_date, ts.currency_code, ts.opening_balance
ORDER BY t.name$$, updated_at = now()
WHERE report_name = 'TellerCashPosition';

-- Was `COALESCE(${R_startDate}::date, …)`: an empty parameter left `COALESCE(::date` (a syntax error)
-- and a value was pasted into the SQL. Now bound as a DATE (null when not given).
UPDATE reports SET report_sql = $$SELECT DATE_TRUNC('month', created_at) AS month,
       kyc_status,
       COUNT(*) AS customer_count
FROM customers
WHERE created_at >= COALESCE(CAST(:R_startDate AS date), CURRENT_DATE - INTERVAL '12 months')
  AND created_at <  COALESCE(CAST(:R_endDate AS date) + 1, CURRENT_DATE + 1)
GROUP BY 1, 2
ORDER BY 1 DESC, 2$$, updated_at = now()
WHERE report_name = 'CustomerAcquisition';

-- Reversal lines already cancel the originals: never filter is_reversed when summing (cont. 21).
-- Per account AND currency: amounts in different currencies are never added together.
UPDATE reports SET report_sql = $$SELECT ga.gl_code, ga.name, ga.account_type, je.currency_code,
       SUM(CASE WHEN je.entry_type = 'DEBIT'  THEN je.amount ELSE 0 END) AS total_debits,
       SUM(CASE WHEN je.entry_type = 'CREDIT' THEN je.amount ELSE 0 END) AS total_credits,
       SUM(CASE WHEN je.entry_type = 'DEBIT'  THEN je.amount ELSE 0 END)
         - SUM(CASE WHEN je.entry_type = 'CREDIT' THEN je.amount ELSE 0 END) AS net_balance
FROM gl_accounts ga
JOIN journal_entries je ON je.gl_account_id = ga.id
WHERE ga.usage = 'DETAIL'
GROUP BY ga.id, ga.gl_code, ga.name, ga.account_type, je.currency_code
ORDER BY ga.gl_code, je.currency_code$$,
    description = 'Trial balance — debits and credits per GL account and currency', updated_at = now()
WHERE report_name = 'TrialBalance';

UPDATE reports SET report_sql = $$SELECT lp.name AS product_name, lp.repayment_type,
       COUNT(l.id) AS loan_count,
       SUM(l.principal_amount) AS total_disbursed,
       SUM(l.outstanding_balance) AS total_outstanding,
       AVG(l.interest_rate) AS avg_interest_rate
FROM loan_products lp
LEFT JOIN loans l ON l.product_id = lp.id AND l.status NOT IN ('SUBMITTED', 'UNDER_REVIEW')
GROUP BY lp.id, lp.name, lp.repayment_type
ORDER BY lp.name$$, updated_at = now()
WHERE report_name = 'LoanProductSummary';
