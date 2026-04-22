-- Partner Organizations
CREATE TABLE partner_organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    website VARCHAR(500),
    business_type VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'SANDBOX',
    tier VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    environment VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
    application_status VARCHAR(50),
    use_case TEXT,
    estimated_monthly_calls VARCHAR(50),
    technical_contact VARCHAR(255),
    compliance_notes TEXT,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Partner Users
CREATE TABLE partner_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES partner_organizations(id),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'DEVELOPER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Partner Applications (production upgrade requests)
CREATE TABLE partner_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES partner_organizations(id),
    business_type VARCHAR(100),
    use_case TEXT,
    estimated_monthly_calls VARCHAR(50),
    website VARCHAR(500),
    technical_contact VARCHAR(255),
    compliance_notes TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Partner API Keys (delegated from card-service api_keys concept but scoped to partner)
CREATE TABLE partner_api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES partner_organizations(id),
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(255) NOT NULL UNIQUE,
    key_prefix VARCHAR(20) NOT NULL,
    scopes JSONB NOT NULL DEFAULT '[]',
    tier VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Partner Webhooks
CREATE TABLE partner_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES partner_organizations(id),
    name VARCHAR(255) NOT NULL,
    callback_url VARCHAR(1000) NOT NULL,
    events JSONB NOT NULL DEFAULT '[]',
    secret_hash VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- Partner Webhook Delivery Log
CREATE TABLE partner_webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id UUID NOT NULL REFERENCES partner_webhooks(id),
    event_type VARCHAR(100) NOT NULL,
    delivery_uuid VARCHAR(100) NOT NULL UNIQUE,
    payload JSONB,
    http_status INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Partner Usage Snapshots (daily aggregated per org)
CREATE TABLE partner_usage_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES partner_organizations(id),
    snapshot_date DATE NOT NULL,
    total_calls INTEGER NOT NULL DEFAULT 0,
    success_calls INTEGER NOT NULL DEFAULT 0,
    error_calls INTEGER NOT NULL DEFAULT 0,
    top_endpoints JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE (organization_id, snapshot_date)
);

-- Indexes
CREATE INDEX idx_partner_users_org ON partner_users(organization_id);
CREATE INDEX idx_partner_api_keys_org ON partner_api_keys(organization_id);
CREATE INDEX idx_partner_api_keys_hash ON partner_api_keys(key_hash);
CREATE INDEX idx_partner_webhooks_org ON partner_webhooks(organization_id);
CREATE INDEX idx_partner_deliveries_webhook ON partner_webhook_deliveries(webhook_id);
CREATE INDEX idx_partner_usage_org_date ON partner_usage_snapshots(organization_id, snapshot_date);
CREATE INDEX idx_partner_applications_org ON partner_applications(organization_id);
