CREATE TABLE IF NOT EXISTS field_configurations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(50)  NOT NULL,
    field_name          VARCHAR(100) NOT NULL,
    field_label         VARCHAR(150) NOT NULL,
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    is_mandatory        BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order       INTEGER      NOT NULL DEFAULT 0,
    description         VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_field_config_entity_field UNIQUE (entity_type, field_name)
);

CREATE INDEX IF NOT EXISTS idx_field_config_entity ON field_configurations (entity_type);

-- Seed CLIENT fields
INSERT INTO field_configurations (entity_type, field_name, field_label, is_enabled, is_mandatory, display_order, description) VALUES
    ('CLIENT', 'firstName',          'First Name',          TRUE,  TRUE,  1,  'Customer first name'),
    ('CLIENT', 'lastName',           'Last Name',           TRUE,  TRUE,  2,  'Customer last name'),
    ('CLIENT', 'middleName',         'Middle Name',         TRUE,  FALSE, 3,  'Customer middle name'),
    ('CLIENT', 'dateOfBirth',        'Date of Birth',       TRUE,  FALSE, 4,  'Customer date of birth'),
    ('CLIENT', 'gender',             'Gender',              TRUE,  FALSE, 5,  'Customer gender'),
    ('CLIENT', 'email',              'Email Address',       TRUE,  TRUE,  6,  'Customer email address'),
    ('CLIENT', 'phoneNumber',        'Phone Number',        TRUE,  FALSE, 7,  'Customer phone number'),
    ('CLIENT', 'nationalId',         'National ID',         TRUE,  FALSE, 8,  'National identity document number'),
    ('CLIENT', 'externalId',         'External ID',         FALSE, FALSE, 9,  'External system reference ID'),
    ('CLIENT', 'staffId',            'Assigned Staff',      TRUE,  FALSE, 10, 'Loan officer or relationship manager'),
    ('CLIENT', 'officeId',           'Branch Office',       TRUE,  TRUE,  11, 'Home branch for the customer'),
    ('CLIENT', 'activationDate',     'Activation Date',     TRUE,  FALSE, 12, 'Date customer account was activated')
ON CONFLICT (entity_type, field_name) DO NOTHING;

-- Seed ADDRESS fields
INSERT INTO field_configurations (entity_type, field_name, field_label, is_enabled, is_mandatory, display_order, description) VALUES
    ('ADDRESS', 'addressLine1',  'Address Line 1', TRUE,  TRUE,  1, 'Street address line 1'),
    ('ADDRESS', 'addressLine2',  'Address Line 2', TRUE,  FALSE, 2, 'Street address line 2'),
    ('ADDRESS', 'city',          'City',           TRUE,  TRUE,  3, 'City or town'),
    ('ADDRESS', 'stateProvince', 'State/Province', TRUE,  FALSE, 4, 'State or province'),
    ('ADDRESS', 'postalCode',    'Postal Code',    TRUE,  FALSE, 5, 'Postal or ZIP code'),
    ('ADDRESS', 'countryCode',   'Country',        TRUE,  TRUE,  6, 'ISO 3166 country code'),
    ('ADDRESS', 'addressType',   'Address Type',   TRUE,  FALSE, 7, 'HOME, WORK, or MAILING')
ON CONFLICT (entity_type, field_name) DO NOTHING;

-- Seed LOAN fields
INSERT INTO field_configurations (entity_type, field_name, field_label, is_enabled, is_mandatory, display_order, description) VALUES
    ('LOAN', 'loanPurpose',      'Loan Purpose',       TRUE,  FALSE, 1, 'Purpose of the loan application'),
    ('LOAN', 'externalId',       'External Reference', FALSE, FALSE, 2, 'External system loan reference'),
    ('LOAN', 'linkAccountId',    'Linked Account',     TRUE,  FALSE, 3, 'Savings account for repayment auto-debit'),
    ('LOAN', 'expectedDisbDate', 'Expected Disbursal', TRUE,  FALSE, 4, 'Expected disbursement date')
ON CONFLICT (entity_type, field_name) DO NOTHING;
