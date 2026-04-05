# CBA Architecture Reference

## System Architecture Overview

The platform follows a **layered hexagonal architecture** per Fineract conventions, adapted for FAPI 2.0 and modern cloud deployment.

```
┌─────────────────────────────────────────────────────────┐
│                    API Gateway / Ingress                  │
│              (Nginx / Kong / AWS ALB)                     │
└────────────┬───────────────────────┬────────────────────┘
             │                       │
    ┌────────▼────────┐    ┌─────────▼──────────┐
    │   Angular Web   │    │   Flutter Mobile    │
    │   (Port 4200)   │    │   (iOS / Android)   │
    └────────┬────────┘    └─────────┬──────────┘
             │                       │
             └───────────┬───────────┘
                         │  HTTPS / OpenAPI
             ┌───────────▼────────────┐
             │   Spring Boot Backend   │
             │   (Port 8080)           │
             │                         │
             │  ┌─────────────────┐   │
             │  │  REST / FAPI 2.0 │   │
             │  │  Controllers     │   │
             │  └────────┬────────┘   │
             │  ┌────────▼────────┐   │
             │  │  Service Layer  │   │
             │  └────────┬────────┘   │
             │  ┌────────▼────────┐   │
             │  │  Domain Model   │   │
             │  │  (JPA Entities) │   │
             │  └────────┬────────┘   │
             └───────────┼────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
   ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐
   │ PostgreSQL  │ │  Keycloak  │ │   Redis   │
   │ (Port 5432) │ │ (Port 8180) │ │ (Cache)   │
   └─────────────┘ └────────────┘ └───────────┘
```

## Package Structure (Backend)

```
backend/src/main/java/com/cba/
├── CbaApplication.java
├── config/
│   ├── SecurityConfig.java          # Keycloak + FAPI 2.0
│   ├── DatabaseConfig.java
│   ├── OpenApiConfig.java
│   └── AuditConfig.java
├── common/
│   ├── response/ApiResponse.java    # Standard envelope
│   ├── exception/GlobalExceptionHandler.java
│   ├── audit/AuditableEntity.java   # Base class
│   ├── crypto/FieldEncryptor.java   # PII encryption
│   └── pagination/PageResponse.java
├── customer/
│   ├── CustomerController.java
│   ├── CustomerService.java
│   ├── CustomerRepository.java
│   ├── Customer.java                # JPA Entity
│   └── dto/
├── account/
│   ├── AccountController.java
│   ├── AccountService.java
│   ├── AccountRepository.java
│   ├── Account.java
│   ├── AccountType.java             # Enum: SAVINGS, CHECKING, FIXED_DEPOSIT
│   └── dto/
├── loan/
│   ├── LoanController.java
│   ├── LoanService.java
│   ├── LoanRepository.java
│   ├── Loan.java
│   ├── LoanRepaymentSchedule.java
│   ├── LoanStatus.java              # Enum: PENDING, ACTIVE, CLOSED, WRITTEN_OFF
│   └── dto/
├── payment/
│   ├── PaymentController.java
│   ├── PaymentService.java
│   ├── PaymentRepository.java
│   ├── Payment.java
│   ├── PaymentStatus.java
│   └── dto/
├── product/
│   ├── LoanProduct.java
│   ├── DepositProduct.java
│   └── InterestRate.java
├── notification/
│   ├── NotificationService.java
│   └── NotificationEvent.java
├── audit/
│   ├── AuditLog.java
│   └── AuditLogRepository.java
└── openbanking/
    ├── AccountInfoController.java   # FAPI 2.0 /accounts
    ├── PaymentInitiationController.java
    ├── ConsentController.java
    └── dto/
```

## Database Schema Design

### Core Tables

```sql
-- customers
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(50) UNIQUE NOT NULL,
    first_name_encrypted TEXT NOT NULL,   -- AES-256 encrypted
    last_name_encrypted TEXT NOT NULL,
    email_encrypted TEXT NOT NULL,
    phone_encrypted TEXT,
    date_of_birth DATE,
    kyc_status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    created_by VARCHAR(100),
    version BIGINT DEFAULT 0              -- Optimistic locking
);

-- accounts
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20) UNIQUE NOT NULL,
    customer_id UUID REFERENCES customers(id),
    product_id UUID REFERENCES deposit_products(id),
    account_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    balance NUMERIC(19,4) DEFAULT 0,
    currency_code CHAR(3) DEFAULT 'USD',
    opened_date DATE DEFAULT CURRENT_DATE,
    closed_date DATE,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    version BIGINT DEFAULT 0
);

-- transactions
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES accounts(id),
    transaction_type VARCHAR(30) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    running_balance NUMERIC(19,4),
    currency_code CHAR(3) DEFAULT 'USD',
    description TEXT,
    reference_number VARCHAR(50),
    transaction_date TIMESTAMPTZ DEFAULT now(),
    value_date DATE,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- loans
CREATE TABLE loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_account_number VARCHAR(20) UNIQUE NOT NULL,
    customer_id UUID REFERENCES customers(id),
    product_id UUID REFERENCES loan_products(id),
    principal_amount NUMERIC(19,4) NOT NULL,
    approved_amount NUMERIC(19,4),
    outstanding_balance NUMERIC(19,4),
    interest_rate NUMERIC(8,4),
    term_months INT,
    status VARCHAR(30) DEFAULT 'PENDING',
    disbursement_date DATE,
    maturity_date DATE,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    version BIGINT DEFAULT 0
);

-- audit_log (append-only, never update/delete)
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ DEFAULT now(),
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_time ON audit_log(changed_at);
```

## Multi-Tenancy Preparation

Although the initial build is single-tenant, every table includes `tenant_id` (nullable now, required later):

```sql
-- Add to every core table
tenant_id UUID REFERENCES tenants(id) -- NULL = default tenant for v1
```

A `TenantContext` ThreadLocal holder is scaffolded from day one:

```java
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    public static void setTenant(String tenantId) { CURRENT_TENANT.set(tenantId); }
    public static String getTenant() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
```

Future activation requires only a Hibernate filter + a request interceptor to populate the context.

## API Design Conventions

- Base path: `/api/v1/`
- Open Banking endpoints: `/open-banking/v3.1/` (UK OB standard)
- All responses wrapped in standard envelope:
```json
{
  "data": { ... },
  "meta": { "page": 1, "size": 20, "total": 150 },
  "errors": []
}
```
- Error format:
```json
{
  "data": null,
  "meta": {},
  "errors": [{ "code": "ACCOUNT_NOT_FOUND", "message": "Account 123 not found", "field": null }]
}
```
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- Versioning: URL path (`/v1/`, `/v2/`) — do not use headers
