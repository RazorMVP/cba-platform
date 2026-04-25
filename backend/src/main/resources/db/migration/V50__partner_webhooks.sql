-- Partner webhooks: per-org webhook registrations for partner portal event subscriptions
CREATE TABLE IF NOT EXISTS partner_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES partner_organizations(id),
    name VARCHAR(255) NOT NULL,
    callback_url TEXT NOT NULL,
    secret VARCHAR(255),
    events JSONB NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_partner_webhooks_org ON partner_webhooks(organization_id);

-- Partner webhook delivery log
CREATE TABLE IF NOT EXISTS partner_webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id UUID NOT NULL REFERENCES partner_webhooks(id),
    event_type VARCHAR(100) NOT NULL,
    delivery_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    payload JSONB,
    http_status INT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_partner_deliveries_webhook ON partner_webhook_deliveries(webhook_id);
