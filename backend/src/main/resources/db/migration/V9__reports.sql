-- V9: Dynamic Report Engine

CREATE TABLE reports (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    report_name     VARCHAR(200)    NOT NULL UNIQUE,
    report_type     VARCHAR(20)     NOT NULL DEFAULT 'TABLE'
                        CHECK (report_type IN ('TABLE','CHART','SMS','PENTAHO')),
    category        VARCHAR(100)    NOT NULL DEFAULT 'General',
    description     TEXT,
    report_sql      TEXT            NOT NULL,   -- parameterised SQL; params as ${paramName}
    core_report     BOOLEAN         NOT NULL DEFAULT FALSE,  -- built-in; cannot be deleted
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    tenant_id       UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE report_parameters (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id       UUID            NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    parameter_name  VARCHAR(100)    NOT NULL,
    parameter_label VARCHAR(100),
    parameter_type  VARCHAR(20)     NOT NULL DEFAULT 'STRING',
    default_value   VARCHAR(255),
    required        BOOLEAN         NOT NULL DEFAULT FALSE,
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_report_params_report ON report_parameters(report_id);

-- ── Seed built-in reports ─────────────────────────────────────────────────────
INSERT INTO reports (id, report_name, report_type, category, description, report_sql, core_report) VALUES

('40000000-0000-0000-0000-000000000001',
 'ActiveLoans',
 'TABLE', 'Loans',
 'All active loans with customer details, outstanding balance, and next due date',
 'SELECT l.loan_account_number, c.id as customer_id, l.principal_amount,
         l.outstanding_balance, l.status, l.disbursement_date,
         lrs.due_date as next_due_date, lrs.total_due as next_amount_due
  FROM loans l
  JOIN customers c ON c.id = l.customer_id
  LEFT JOIN loan_repayment_schedule lrs ON lrs.loan_id = l.id
      AND lrs.status = ''PENDING'' AND lrs.due_date >= CURRENT_DATE
  WHERE l.status IN (''ACTIVE'',''IN_ARREARS'')
  ORDER BY l.disbursement_date DESC',
 TRUE),

('40000000-0000-0000-0000-000000000002',
 'LoansInArrears',
 'TABLE', 'Loans',
 'Loans in arrears grouped by ageing bucket (30/60/90/90+ days)',
 'SELECT l.loan_account_number, c.id as customer_id,
         l.outstanding_balance,
         CURRENT_DATE - MAX(lrs.due_date) as days_overdue,
         CASE
           WHEN CURRENT_DATE - MAX(lrs.due_date) <= 30  THEN ''0-30 days''
           WHEN CURRENT_DATE - MAX(lrs.due_date) <= 60  THEN ''31-60 days''
           WHEN CURRENT_DATE - MAX(lrs.due_date) <= 90  THEN ''61-90 days''
           ELSE ''90+ days''
         END as ageing_bucket
  FROM loans l
  JOIN customers c ON c.id = l.customer_id
  JOIN loan_repayment_schedule lrs ON lrs.loan_id = l.id AND lrs.status = ''OVERDUE''
  WHERE l.status = ''IN_ARREARS''
  GROUP BY l.id, l.loan_account_number, c.id, l.outstanding_balance
  ORDER BY days_overdue DESC',
 TRUE),

('40000000-0000-0000-0000-000000000003',
 'SavingsBalance',
 'TABLE', 'Savings',
 'Savings account balances by branch',
 'SELECT o.name as branch, a.account_type, a.currency_code,
         COUNT(*) as account_count, SUM(a.balance) as total_balance
  FROM accounts a
  LEFT JOIN offices o ON o.id = (SELECT office_id FROM platform_users LIMIT 1)
  WHERE a.status = ''ACTIVE''
  GROUP BY o.name, a.account_type, a.currency_code
  ORDER BY o.name, a.account_type',
 TRUE),

('40000000-0000-0000-0000-000000000004',
 'TellerCashPosition',
 'TABLE', 'Teller',
 'Cash position by teller desk for current open sessions',
 'SELECT t.name as teller_name, ts.session_date, ts.currency_code,
         ts.opening_balance,
         COALESCE(SUM(CASE WHEN ct.transaction_type=''CASH_IN'' THEN ct.amount ELSE 0 END),0) as total_cash_in,
         COALESCE(SUM(CASE WHEN ct.transaction_type=''CASH_OUT'' THEN ct.amount ELSE 0 END),0) as total_cash_out,
         ts.opening_balance
           + COALESCE(SUM(CASE WHEN ct.transaction_type=''CASH_IN'' THEN ct.amount ELSE 0 END),0)
           - COALESCE(SUM(CASE WHEN ct.transaction_type=''CASH_OUT'' THEN ct.amount ELSE 0 END),0) as current_balance
  FROM teller_sessions ts
  JOIN tellers t ON t.id = ts.teller_id
  LEFT JOIN cash_transactions ct ON ct.session_id = ts.id
  WHERE ts.status = ''OPEN''
  GROUP BY t.name, ts.session_date, ts.currency_code, ts.opening_balance
  ORDER BY t.name',
 TRUE),

('40000000-0000-0000-0000-000000000005',
 'CustomerAcquisition',
 'TABLE', 'Customers',
 'New customers acquired by month and KYC status',
 'SELECT DATE_TRUNC(''month'', created_at) as month,
         kyc_status,
         COUNT(*) as customer_count
  FROM customers
  WHERE created_at >= COALESCE(${R_startDate}::date, CURRENT_DATE - INTERVAL ''12 months'')
    AND created_at <  COALESCE(${R_endDate}::date,   CURRENT_DATE + INTERVAL ''1 day'')
  GROUP BY 1, 2
  ORDER BY 1 DESC, 2',
 TRUE),

('40000000-0000-0000-0000-000000000006',
 'TrialBalance',
 'TABLE', 'Accounting',
 'Trial balance — total debits and credits per GL account',
 'SELECT ga.gl_code, ga.name, ga.account_type,
         SUM(CASE WHEN je.entry_type=''DEBIT''  THEN je.amount ELSE 0 END) as total_debits,
         SUM(CASE WHEN je.entry_type=''CREDIT'' THEN je.amount ELSE 0 END) as total_credits,
         SUM(CASE WHEN je.entry_type=''DEBIT''  THEN je.amount ELSE 0 END)
           - SUM(CASE WHEN je.entry_type=''CREDIT'' THEN je.amount ELSE 0 END) as net_balance
  FROM gl_accounts ga
  LEFT JOIN journal_entries je ON je.gl_account_id = ga.id AND je.is_reversed = FALSE
  WHERE ga.usage = ''DETAIL''
  GROUP BY ga.id, ga.gl_code, ga.name, ga.account_type
  ORDER BY ga.gl_code',
 TRUE),

('40000000-0000-0000-0000-000000000007',
 'LoanProductSummary',
 'TABLE', 'Products',
 'Summary of loans by product: count, total disbursed, outstanding balance',
 'SELECT lp.name as product_name, lp.repayment_type,
         COUNT(l.id) as loan_count,
         SUM(l.principal_amount) as total_disbursed,
         SUM(l.outstanding_balance) as total_outstanding,
         AVG(l.interest_rate) as avg_interest_rate
  FROM loan_products lp
  LEFT JOIN loans l ON l.product_id = lp.id AND l.status NOT IN (''SUBMITTED'',''UNDER_REVIEW'')
  GROUP BY lp.id, lp.name, lp.repayment_type
  ORDER BY lp.name',
 TRUE);

-- ── Report parameters for date-range reports ──────────────────────────────────
INSERT INTO report_parameters (report_id, parameter_name, parameter_label, parameter_type, required, sort_order) VALUES
('40000000-0000-0000-0000-000000000005', 'R_startDate', 'Start Date', 'DATE', FALSE, 1),
('40000000-0000-0000-0000-000000000005', 'R_endDate',   'End Date',   'DATE', FALSE, 2);
