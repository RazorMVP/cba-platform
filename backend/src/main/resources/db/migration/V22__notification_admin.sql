-- Notification Admin API
-- Templates define the content for email/SMS events.
-- Logs track every delivery attempt.

CREATE TABLE notification_templates (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(120)  NOT NULL UNIQUE,
    event_type       VARCHAR(80)   NOT NULL,
    delivery_method  VARCHAR(10)   NOT NULL CHECK (delivery_method IN ('EMAIL','SMS')),
    subject          VARCHAR(250),                   -- email only
    body             TEXT          NOT NULL,          -- supports {{customerName}}, {{amount}}, etc.
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE notification_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id      UUID REFERENCES notification_templates(id),
    event_type       VARCHAR(80)   NOT NULL,
    recipient_id     UUID,                            -- customer UUID (nullable for system events)
    recipient_ref    VARCHAR(200),                    -- email address or phone number (masked in API)
    delivery_method  VARCHAR(10)   NOT NULL,
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('SENT','FAILED','SKIPPED')),
    error_message    TEXT,
    sent_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_notif_logs_event_type ON notification_logs(event_type);
CREATE INDEX idx_notif_logs_recipient  ON notification_logs(recipient_id);
CREATE INDEX idx_notif_logs_sent_at    ON notification_logs(sent_at DESC);

-- Seed standard templates
INSERT INTO notification_templates (name, event_type, delivery_method, subject, body) VALUES
('Account Opened — Email',    'ACCOUNT_OPENED',    'EMAIL', 'Your CBA account is ready',
 'Dear {{customerName}}, your {{accountType}} account {{accountNumber}} has been opened successfully.'),
('Account Opened — SMS',      'ACCOUNT_OPENED',    'SMS',   NULL,
 'CBA: Account {{accountNumber}} opened. Welcome, {{customerName}}!'),
('Large Transaction Alert',   'LARGE_TRANSACTION', 'SMS',   NULL,
 'CBA Alert: A transaction of {{amount}} {{currency}} was posted to account {{accountNumber}}.'),
('Loan Approved — Email',     'LOAN_APPROVED',     'EMAIL', 'Your loan has been approved',
 'Dear {{customerName}}, your loan of {{amount}} {{currency}} has been approved. Disbursement will follow shortly.'),
('Loan Disbursed — SMS',      'LOAN_DISBURSED',    'SMS',   NULL,
 'CBA: Loan {{loanNumber}} disbursed. {{amount}} {{currency}} credited to account {{accountNumber}}.'),
('Loan Due Reminder — SMS',   'LOAN_DUE',          'SMS',   NULL,
 'CBA Reminder: Installment of {{amount}} {{currency}} is due on {{dueDate}} for loan {{loanNumber}}.'),
('Failed Login Alert — Email','FAILED_LOGIN',      'EMAIL', 'Security alert: Failed login attempt',
 'Dear {{customerName}}, a failed login attempt was detected on your account at {{timestamp}}. If this was not you, contact us immediately.');
