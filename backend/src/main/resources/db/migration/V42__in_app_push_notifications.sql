-- In-app notification feed (global; per-user reads tracked via user_notification_prefs)
CREATE TABLE IF NOT EXISTS in_app_notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(40)  NOT NULL,
    severity    VARCHAR(10)  NOT NULL DEFAULT 'INFO',
    title       VARCHAR(200) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_in_app_notifications_created_at ON in_app_notifications(created_at DESC);

-- Per-user read horizon: unread count = notifications created after last_read_at
CREATE TABLE IF NOT EXISTS user_notification_prefs (
    user_id       VARCHAR(120) PRIMARY KEY,
    last_read_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Push device tokens (FCM) — for mobile push notifications
CREATE TABLE IF NOT EXISTS push_devices (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        VARCHAR(120) NOT NULL,
    fcm_token      TEXT         NOT NULL UNIQUE,
    platform       VARCHAR(10)  NOT NULL,   -- ANDROID | IOS | WEB
    device_label   VARCHAR(120),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ
);

CREATE INDEX idx_push_devices_user_id ON push_devices(user_id);
CREATE INDEX idx_push_devices_active  ON push_devices(user_id, active);

-- Seed a few demo in-app notifications
INSERT INTO in_app_notifications (type, severity, title, message, entity_type)
VALUES
    ('SYSTEM_ALERT', 'INFO',    'Platform started',                  'Core Banking Application is running and all services are healthy.', 'SYSTEM'),
    ('KYC_APPROVED', 'INFO',    'KYC approved — John Doe',           'Customer John Doe has completed KYC verification successfully.', 'CUSTOMER'),
    ('LOAN_IN_ARREARS', 'WARNING', 'Loan overdue — LN-000003',       'Loan LN-000003 has overdue installments. Action required.', 'LOAN'),
    ('LARGE_TRANSACTION', 'WARNING', 'Large transaction detected',    'A transaction of $45,000 was posted on account 001-SAV-0000001.', 'ACCOUNT');
