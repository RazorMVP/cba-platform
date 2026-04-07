-- V6: Offices, Staff, and User Management
-- Organisational hierarchy + platform user accounts with Keycloak sync

-- ── Offices / Branches ───────────────────────────────────────────────────────
CREATE TABLE offices (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200)    NOT NULL,
    external_id     VARCHAR(100)    UNIQUE,
    parent_id       UUID            REFERENCES offices(id),
    opening_date    DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','INACTIVE','CLOSED')),
    hierarchy       VARCHAR(500),       -- materialised path e.g. ".1.3.7."
    tenant_id       UUID,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_offices_parent    ON offices(parent_id);
CREATE INDEX idx_offices_hierarchy ON offices(hierarchy);

-- ── Staff ────────────────────────────────────────────────────────────────────
CREATE TABLE staff (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    office_id           UUID            NOT NULL REFERENCES offices(id),
    firstname           VARCHAR(100)    NOT NULL,
    lastname            VARCHAR(100)    NOT NULL,
    display_name        VARCHAR(200)    GENERATED ALWAYS AS (firstname || ' ' || lastname) STORED,
    mobile_no           VARCHAR(30),
    email               VARCHAR(255),
    is_loan_officer     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    joining_date        DATE,
    tenant_id           UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_office       ON staff(office_id);
CREATE INDEX idx_staff_loan_officer ON staff(is_loan_officer) WHERE is_loan_officer = TRUE;

-- ── Platform Users ───────────────────────────────────────────────────────────
CREATE TABLE platform_users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(100)    NOT NULL UNIQUE,
    email               VARCHAR(255)    NOT NULL UNIQUE,
    firstname           VARCHAR(100)    NOT NULL,
    lastname            VARCHAR(100)    NOT NULL,
    keycloak_id         VARCHAR(100)    UNIQUE,         -- Keycloak user UUID
    office_id           UUID            REFERENCES offices(id),
    staff_id            UUID            REFERENCES staff(id),
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    is_self_service     BOOLEAN         NOT NULL DEFAULT FALSE,
    password_never_expires BOOLEAN      NOT NULL DEFAULT FALSE,
    tenant_id           UUID,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_keycloak ON platform_users(keycloak_id);
CREATE INDEX idx_users_office   ON platform_users(office_id);

-- ── User Roles (join table) ──────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id     UUID        NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    role_name   VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role_name)
);

-- ── Self-Service User — Customer links ───────────────────────────────────────
CREATE TABLE self_service_user_clients (
    user_id     UUID    NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    customer_id UUID    NOT NULL REFERENCES customers(id),
    PRIMARY KEY (user_id, customer_id)
);

-- ── Demo office and admin user ────────────────────────────────────────────────
INSERT INTO offices (id, name, external_id, opening_date, hierarchy)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Head Office', 'HO-001', '2020-01-01', '.1.'),
    ('10000000-0000-0000-0000-000000000002', 'Main Branch',  'BR-001', '2020-01-01', '.1.2.');

INSERT INTO platform_users (id, username, email, firstname, lastname, keycloak_id, office_id, is_active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'admin', 'admin@cba.com', 'System', 'Administrator',
     'admin-keycloak-id', '10000000-0000-0000-0000-000000000001', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'teller1', 'teller@cba.com', 'Jane', 'Teller',
     'teller-keycloak-id', '10000000-0000-0000-0000-000000000002', TRUE);

INSERT INTO user_roles (user_id, role_name) VALUES
    ('20000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('20000000-0000-0000-0000-000000000002', 'TELLER');
