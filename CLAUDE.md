# Core Banking Application (CBA) — Body of Knowledge

This file is the single source of truth for Claude when working on the CBA platform. Read it fully at the start of every session before generating any code.

---

## Project Overview

A production-grade, full-stack Core Banking Application modelled on Apache Fineract / Mifos X conventions.

### Monorepo Structure

```
cba-platform/
├── backend/          # Java 21 + Spring Boot 3 REST API
├── web/              # Angular 17+ backoffice portal
├── mobile/           # Flutter 3+ customer mobile app
├── infrastructure/   # Docker Compose + Kubernetes + Keycloak
├── docs/             # OpenAPI specs, architecture diagrams
└── CLAUDE.md         # This file
```

---

## Reference Systems (Read Before Coding)

| System | URL | Purpose |
|--------|-----|---------|
| Apache Fineract | https://github.com/apache/fineract | Domain model, module structure, service patterns |
| Mifos API Live Docs | https://demo.mifos.io/api-docs/apiLive.htm | Complete REST API reference for all banking modules |
| OpenMF GitHub Org | https://github.com/openMF | Angular web-app reference, mobile app, KMP templates |
| Mifos Web App (Angular) | https://github.com/openMF/web-app | Feature module routing, resolver, lazy-load patterns |
| Mifos Mobile (Flutter) | https://github.com/openMF/mifos-mobile | Flutter feature-first structure, AppAuth integration |
| Mifos Architecture Docs | https://mifos.org/resources/technical-resources/architecture/ | High-level system architecture |

### Key OpenMF Repositories
- `openMF/web-app` — Angular frontend (stars: 358). Use as reference for feature routing, resolvers, data tables
- `openMF/mifos-mobile` — Flutter client app (stars: 346). Use for mobile feature structure, auth flows
- `openMF/mifos-pay` — Payment wallet reference (stars: 319). Study for payment hub patterns
- `openMF/mcp-mifosx` — MCP server for Mifos X (stars: 20). Reference for AI agent integration
- `openMF/kmp-project-template` — Kotlin Multiplatform template (stars: 68). Alternative mobile reference

### Apache Fineract Module Structure (Gradle multi-module)
```
fineract-core           — shared domain model, base entities
fineract-loan           — loan lifecycle, repayment scheduling
fineract-savings        — savings/deposit accounts
fineract-accounting     — GL entries, journal, closures
fineract-security       — auth, roles, permissions
fineract-report         — reporting engine
fineract-command        — command/event pattern (CQRS-lite)
fineract-progressive-loan — modern progressive repayment engine
fineract-cob            — Close of Business batch processing
```

---

## System Architecture

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

---

## Tech Stack

### Backend — Java 21 + Spring Boot 3
- **Java 21** — use records, sealed classes, pattern matching where appropriate
- **Spring Boot 3.2.x** — web, data-jpa, validation, actuator, cache, security, oauth2-resource-server
- **PostgreSQL 16** — primary database; schema managed by Flyway
- **Flyway** — database migrations (never use `ddl-auto: create/update` in prod)
- **Keycloak 23+** — OIDC authorization server, FAPI 2.0 profile
- **Jasypt** — field-level encryption for PII (AES-256)
- **springdoc-openapi 2.x** — OpenAPI 3.1 docs at `/swagger-ui.html`
- **Lombok + MapStruct** — boilerplate reduction and DTO mapping
- **Testcontainers** — real database in integration tests (never mock the DB)

### Web Frontend — Angular 17+
- Standalone components (`--standalone`)
- Angular Material + PrimeNG for UI components
- NGRx for state management
- keycloak-angular for OIDC
- Lazy-loaded feature modules with resolvers (see `openMF/web-app` for patterns)
- Chart.js / ng2-charts for dashboards

### Mobile Frontend — Flutter 3+
- Feature-first folder structure: `features/{auth,dashboard,accounts,loans,payments,profile}/`
- Riverpod 2.x for state management
- flutter_appauth for OIDC / Keycloak
- Dio + Retrofit for HTTP client
- local_auth for biometrics
- go_router for navigation

### Infrastructure
- **Docker Compose** for local dev (postgres, keycloak, backend, web, mailhog)
- **Kubernetes** for production (namespace: `cba-platform`, HPA on backend)
- **Eclipse Temurin 21** as the JRE base image
- **Sealed Secrets or Vault** for k8s secret management

---

## Banking Module Catalogue

Each module follows the pattern: Entity → Repository → Service (@Transactional) → Controller (thin) → DTOs.

### 1. Customer Module
- KYC onboarding, profile management, identity verification
- Status flow: `PENDING_KYC → ACTIVE → SUSPENDED → CLOSED`
- All PII (name, email, phone, national ID) encrypted with `EncryptedStringConverter`
- Key endpoints: `POST /api/v1/customers`, `PUT /api/v1/customers/{id}/kyc-status`

### 2. Account Module
- Savings, Checking, Fixed Deposit accounts
- Account number format: `{branch_code}-{type_code}-{sequence}` (e.g. `001-SAV-0001234`)
- Balance never goes below `minimum_balance` (configurable per product)
- Closed accounts are read-only; balance must be zero first
- All debits/credits produce an immutable `Transaction` record
- Interest calculated via daily scheduled job

### 3. Loan Module
- Full lifecycle: origination → disbursement → repayment → collections → close
- Status flow: `SUBMITTED → UNDER_REVIEW → APPROVED → DISBURSED → ACTIVE → CLOSED_OBLIGATIONS_MET`
- Sub-state: `IN_ARREARS`; terminal states: `WRITTEN_OFF`, `CLOSED_OBLIGATIONS_MET`
- Default repayment: annuity (equal installments). Ask if balloon or Islamic (murabaha) needed
- EMI formula: `P × r × (1+r)^n / ((1+r)^n - 1)`
- Fineract service pattern: publish pre-event → validate → process → publish post-event → audit

### 4. Payment Module
- Internal transfers, external payments (SWIFT/SEPA stubs), standing orders, bill payments
- Payment states: `PENDING → PROCESSING → COMPLETED / FAILED → REVERSED`
- Double-entry ledger: every transfer debits source and credits destination atomically
- Use `SELECT FOR UPDATE` on both accounts to prevent race conditions
- `AuditLogService` called with `PROPAGATION.REQUIRES_NEW` so audit survives rollback

### 5. Product Module
- Loan products: principal range, interest rate range, term range, repayment type, fees
- Deposit products: account type, minimum balance, interest rate, compounding frequency
- Repayment types: `ANNUITY`, `FLAT`, `DECLINING_BALANCE`
- Interest compounding: `DAILY`, `MONTHLY`, `ANNUALLY`

### 6. Open Banking Module (FAPI 2.0)
- UK Open Banking v3.1 compliant endpoints
- Base path: `/open-banking/v3.1/`
- AISP: accounts, balances, transactions
- PISP: domestic-payments initiation
- CBPII: funds-confirmation
- Each TPP access requires a `Consent` record with scopes and expiry
- Consent flow: `AWAITING_AUTHORISATION → AUTHORISED → REVOKED`

### 7. Notification Module
- Event-driven via Spring `@EventListener` + `@Async`
- Events: account opened/closed, large transaction, loan approved/disbursed/due, failed login, profile change
- Email via MailHog in dev; configure SMTP in prod

### 8. Audit Module
- **NEVER update or delete audit log records** — append-only
- Log every state-changing operation
- Fields: entity_type, entity_id, action, changed_by, timestamp, old_values (JSONB), new_values (JSONB), IP, user agent
- Retention: minimum 7 years
- `AuditLogService` always uses `@Transactional(propagation = REQUIRES_NEW)`

### 9. Teller / Cash Management Module (from Mifos)
- Teller creation and management
- Cashier management and cash allocation
- Cash settlement and transaction tracking
- Endpoints: `POST /api/v1/tellers`, `POST /api/v1/tellers/{id}/cashiers`

### 10. Group & Center Module (from Mifos — for microfinance use cases)
- Group creation, activation, staff assignment
- Collection sheet generation and processing
- Client association with groups
- GLIM (Group Loan) support

---

## Multi-Currency Architecture

The platform supports multi-currency deployment. Each tenant has a **base currency** (ISO 4217). Accounts default to the tenant's base currency but tellers can override per account for foreign-currency accounts.

### Tenant Base Currency

- Currency is configured per tenant (branch/country deployment)
- Stored in `tenants.currency_code` (ISO 4217, e.g. `KES`, `GHS`, `USD`)
- Demo tenants: `CBA United States` (USD), `CBA Kenya` (KES), `CBA Ghana` (GHS)
- Passed per request via `X-Tenant-ID` header → `TenantInterceptor` → `TenantContext` (ThreadLocal)
- `TenantService.getBaseCurrency(tenantCode)` returns the ISO code; falls back to USD on error

### Exchange Rate Management

- Rates are admin-managed (simple table, manual update via `POST /api/v1/exchange-rates`)
- Convention: 1 `fromCurrency` = rate `toCurrency` (e.g. USD/KES = 135.50)
- Inverse rate is **auto-generated** on every `setRate()` call — admins only set one direction
- Stored with 8 decimal places (`NUMERIC(19,8)`) for precision on exotic pairs
- Rates can be deactivated (`DELETE /api/v1/exchange-rates/{from}/{to}`) — cross-currency transfers will fail until re-set
- See `ExchangeRateService.getRate()`: throws `EXCHANGE_RATE_NOT_CONFIGURED` when no active rate exists

### Cross-Currency Transfers

Flow for a transfer from a KES account to a USD account:
1. `PaymentService.transfer()` detects `srcCcy != dstCcy`
2. Calls `ExchangeRateService.convert(amount, "KES", "USD")`
3. Source account debited by `amount` in KES
4. Destination account credited by `convertedAmount` in USD
5. `Payment` record stores: `sourceCurrency`, `sourceAmount`, `destinationCurrency`, `destinationAmount`, `exchangeRateUsed`, `isCrossCurrency = true`
6. Two `Transaction` records created — each in the account's own currency

### Key Files

| File | Purpose |
|------|---------|
| `V3__multi_currency.sql` | Adds `currency_code` to tenants, `exchange_rates` table, cross-currency columns to payments |
| `V4__multi_currency_demo_data.sql` | 3 tenant demo deployments + exchange rates (USD↔KES, USD↔GHS, KES↔GHS, USD↔EUR, USD↔GBP) |
| `com.cba.tenant.TenantInterceptor` | Reads `X-Tenant-ID` header, sets `TenantContext`, clears on completion |
| `com.cba.tenant.TenantService` | `getBaseCurrency(code)` cached with `@Cacheable("tenants")` |
| `com.cba.currency.ExchangeRateService` | `setRate`, `getRate`, `convert`, `getAllRates`, `deactivateRate` |
| `com.cba.currency.ExchangeRateController` | `POST/GET/DELETE /api/v1/exchange-rates` — ADMIN/TELLER roles |

### Build Notes (Java 25 Compatibility)

- **Lombok 1.18.38** — minimum version for Java 25 `TypeTag` fix (upgraded from 1.18.36)
- **`.mvn/jvm.config`** — `--add-opens jdk.compiler/...` flags for Lombok's javac access
- **Spring Security 6.1+** — `XssProtectionConfig` removed; X-XSS-Protection header dropped; CSP handles it
- Always run `./mvnw clean compile` before committing to catch annotation processor regressions

---

## Mifos API Conventions (from demo.mifos.io)

These conventions are used in the Mifos/Fineract reference system. Mirror them in the CBA backend:

### Authentication
- HTTP Basic Auth OR OAuth2 (Resource Owner Password Credentials Grant)
- Two-factor authentication via OTP (SMS or email)
- All endpoints require authentication except health/docs

### Request / Response Patterns
- All responses JSON
- Date format in responses: array `[YYYY, M, D]`; in requests: string with `locale` + `dateFormat` params
- Number/currency fields require `locale` parameter for parsing
- Template resources (URL ending `/template`) return allowed values and field defaults
- Use `?pretty=true` for formatted output in dev

### Query Parameters
- `fields={list}` — restrict fields returned
- `template=true` — append allowed value lists to response
- `offset` + `limit` — pagination
- `orderBy` + `sortOrder` — sorting

### Batch API
- `POST /api/v1/batches` — execute multiple requests in one HTTP call
- Dependent requests reference prior results using JSON Path: `$.id`, `$.resourceId`
- `enclosingTransaction=true` — wrap all requests in a single transaction
- Use for workflows like: create customer → open account → apply for loan

### HTTP Status Codes
- `200` Success | `400` Validation | `401` Unauthenticated | `403` Unauthorized | `404` Not Found | `500` Server error

---

## API Design Conventions (CBA-specific)

- Base path: `/api/v1/`
- Open Banking: `/open-banking/v3.1/`
- Standard response envelope:
```json
{
  "data": { ... },
  "meta": { "page": 0, "size": 20, "total": 150 },
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
- Versioning: URL path (`/v1/`, `/v2/`) — never use headers

---

## Security Architecture

```
Client (Web/Mobile)
    │  HTTPS + mTLS (optional for FAPI 2.0)
    ▼
Keycloak (OIDC/OAuth 2.0)
    │  FAPI 2.0: PAR + DPoP + PKCE enforced
    ▼
Spring Boot (Resource Server)
    │  JWT validation + RBAC
    │  Field-level encryption (PII)
    │  Audit logging
    ▼
PostgreSQL (encrypted at rest)
```

### RBAC Roles
- `ADMIN` — full platform access
- `TELLER` — customer/account/loan operations
- `CUSTOMER` — own account/loan data via self-service
- `API_CLIENT` — third-party TPP access via Open Banking

### Keycloak Realm: `cba`
- Clients: `cba-backend` (bearer-only), `cba-web` (confidential + PKCE), `cba-mobile` (public + PKCE)
- Brute force protection enabled; lock after 5 failures
- Access token lifespan: 300s

### Field Encryption
- All PII columns stored encrypted: `first_name_encrypted`, `last_name_encrypted`, `email_encrypted`, etc.
- Encryption via JPA `AttributeConverter` using Jasypt
- Algorithm: AES-256 (use `PBEWITHHMACSHA512ANDAES_256` not `PBEWithMD5AndDES` — upgrade from skill default)
- Secret key from environment variable `ENCRYPTION_KEY` — never in code

### PCI-DSS Checklist
- No PAN stored unencrypted
- All PII encrypted at field level
- Audit trail on every data access and modification
- Session tokens never logged
- TLS 1.2+ enforced
- Failed logins tracked and locked (Keycloak)
- Sensitive data masked in API responses (`****1234` for account numbers)
- DB credentials in environment variables only

---

## Database Schema Conventions

- UUIDs as primary keys (`gen_random_uuid()`)
- All monetary amounts: `NUMERIC(19,4)` → `BigDecimal` in Java
- Optimistic locking: `version BIGINT DEFAULT 0` on every mutable table
- Timestamps: `TIMESTAMPTZ` with `DEFAULT now()`
- Multi-tenancy prep: `tenant_id UUID` (nullable in v1, required in v2)
- Indexes: all foreign keys indexed; audit_log indexed on `(entity_type, entity_id)` and `changed_at`
- Flyway conventions: `V{n}__{description}.sql`; `V1__init_schema.sql`, `V2__demo_data.sql`
- Never use `ddl-auto: create` in any non-test profile — Flyway owns the schema

### Core Tables Summary
| Table | Key Columns |
|-------|-------------|
| `customers` | id, external_id, *_encrypted PII, kyc_status, version |
| `accounts` | id, account_number, customer_id, product_id, account_type, status, balance, currency_code |
| `transactions` | id, account_id, transaction_type, amount, running_balance, reference_number |
| `loans` | id, loan_account_number, customer_id, product_id, principal_amount, outstanding_balance, status |
| `loan_repayment_schedule` | id, loan_id, due_date, principal_due, interest_due, total_due, status |
| `payments` | id, source_account_id, destination_account_id, amount, status, payment_type |
| `audit_log` | id, entity_type, entity_id, action, changed_by, old_values (JSONB), new_values (JSONB) |
| `loan_products` | id, name, min/max principal, interest rate range, repayment_type |
| `deposit_products` | id, name, account_type, minimum_balance, interest_rate |

---

## Design System — Nubeero (Stitch)

**Design Engine**: [Stitch](https://stitch.design) — Figma is NOT used for this project.
**Project**: `CoreBanking-Nubeero` in Nubeero workspace
**Token source**: `.claude/skills/cba/designs/tokens.scss` — this is the source of truth

### Design Language
- **App shell** (sidebar, background): near-black `#040609`
- **Content panels** (cards, tables): clean white `#ffffff`
- **CTAs**: deep navy `#1e2833` pill-shaped buttons
- **Typography**: `Instrument Sans` — Regular/Medium/SemiBold/Bold
- Pattern: dark-shell with white cards — high contrast, professional banking aesthetic

### Key Tokens
| Token | Value | Usage |
|-------|-------|-------|
| `--bg-app` | `#040609` | App shell background |
| `--bg-sidebar` | `#0a1628` | Left navigation |
| `--bg-card` | `#ffffff` | Content cards |
| `--color-primary` | `#1e2833` | CTA buttons, active states |
| `--color-text` | `#000314` | Primary text |
| `--color-muted` | `#888888` | Labels, secondary text |

### Available Screen Prototypes
Located in `.claude/skills/cba/designs/screens/backoffice/`:
- `dashboard.html` — KPIs, transaction table, loan portfolio, charts
- `customers.html` — Customer list, KYC badges, search/filter
- `loans.html` — Loan pipeline, detail panel, repayment schedule

Use these as pixel-level references when building Angular components. Apply tokens from `tokens.scss` exactly.

### Stitch Artboard Sections
1. Design Tokens — swatches, type scale, spacing, shadows
2. Auth — Login, OTP, Reset
3. Backoffice / Dashboard
4. Backoffice / Customers
5. Backoffice / Accounts
6. Backoffice / Loans
7. Backoffice / Payments
8. Backoffice / Reports
9. Mobile / Customer App

---

## Service Layer Patterns (from Fineract)

### Event-Driven Lifecycle
Every state-changing service method should follow this pattern:
```java
// 1. Publish pre-event (for validation hooks)
businessEventNotifierService.notifyPreBusinessEvent(new LoanApproveBusinessEvent(loan));
// 2. Validate business rules
// 3. Execute state change
// 4. Persist
// 5. Publish post-event (for downstream processing)
businessEventNotifierService.notifyPostBusinessEvent(new LoanApprovedBusinessEvent(loan));
// 6. Audit log (in REQUIRES_NEW transaction)
auditLogService.log(...)
```

### Transaction Conventions
- All service methods: `@Transactional` by default
- Read-only queries: `@Transactional(readOnly = true)`
- Audit writes: `@Transactional(propagation = REQUIRES_NEW)` — persists even if main TX rolls back
- Money transfers: always lock both accounts with `SELECT FOR UPDATE` before modifying balances

### Close of Business (CoB) Processing
Fineract runs nightly batch jobs (Close of Business) for:
- Interest accrual on savings and loans
- Arrears classification
- Standing order execution
- Scheduled fee charges
Use `@Scheduled` + Spring Batch or Quartz for CBA equivalent.

---

## Angular Patterns (from openMF/web-app)

### Module / Routing Structure
- Every feature is a lazy-loaded module with `RouterModule.forChild(routes)`
- Use resolvers to pre-fetch data before component activation
- Example: `LoanDetailsResolver`, `LoanDatatablesResolver` loaded in route `resolve` object
- Deep link structure: `loans/:loanId/charges/:id/action/:action`

### Angular Component Map (Nubeero Design)
| Design Component | Angular Component | Module |
|-----------------|-------------------|--------|
| Sidebar nav | `SidebarComponent` | `LayoutModule` |
| Topbar | `TopbarComponent` | `LayoutModule` |
| KPI card | `KpiCardComponent` | `SharedModule` |
| Data table | `DataTableComponent` | `SharedModule` |
| Status badge | `StatusBadgeComponent` | `SharedModule` |
| Loan detail panel | `LoanDetailPanelComponent` | `LoansModule` |

---

## Coding Standards

### Java
- Use Java 21 features: records for DTOs, sealed interfaces for status enums, pattern matching in switch
- No business logic in controllers — controllers call service, return `ResponseEntity<ApiResponse<T>>`
- All monetary amounts: `BigDecimal` (never `double` or `float`)
- Never store raw PII in logs — mask card/account numbers, national IDs
- Service method naming: verb + noun, e.g. `approveLoan()`, `disburseToAccount()`
- Custom exceptions per domain: `AccountNotFoundException`, `InsufficientBalanceException`

### General
- Never commit secrets — use environment variables or Vault
- Never update/delete `audit_log` records
- Never use `ddl-auto: create/update` outside test containers
- All new tables must include `tenant_id`, `version`, `created_at`, `updated_at`

---

## Local Development — Getting Started

```bash
# 1. Start infrastructure
docker-compose up postgres keycloak -d

# 2. Wait ~60s for Keycloak, then start backend
docker-compose up backend -d

# 3. Start web portal
docker-compose up web -d

# Service URLs
http://localhost:4200          # Angular backoffice portal
http://localhost:8080/swagger-ui.html  # API docs (Swagger UI)
http://localhost:8180          # Keycloak admin console
http://localhost:8025          # MailHog (dev email)

# Demo credentials (from V2__demo_data.sql)
# Admin:    admin@cba.com / Admin@123
# Teller:   teller@cba.com / Teller@123
# Customer: customer@cba.com / Customer@123
```

### Flutter Mobile
```bash
cd mobile
flutter pub get
flutter run    # Connects to localhost:8080 in dev mode
```

---

## Build Phases (from /cba skill)

Work through phases in order. Always run Phase D (Design) before any frontend code.

| Phase | Name | Description |
|-------|------|-------------|
| D | Design | Apply Nubeero design tokens in Stitch; scaffold HTML prototypes as Angular/Flutter components |
| 0 | Orient | Confirm scope; read architecture.md; create monorepo root |
| 1 | Backend | Spring Boot 3 API — all modules, Flyway, security, OpenAPI |
| 2 | Web Frontend | Angular 17 portal — all feature modules, Keycloak auth |
| 3 | Mobile | Flutter 3 app — feature-first structure, biometric auth |
| 4 | Infrastructure | Docker Compose + Kubernetes + Keycloak realm |
| 5 | Handoff | Getting started guide, module walkthrough, next steps |

---

## Reference Files in This Project

| File | Contents |
|------|----------|
| `.claude/skills/cba/references/architecture.md` | Package structure, DB schema DDL, API conventions, multi-tenancy |
| `.claude/skills/cba/references/modules.md` | Full module spec: endpoints, business rules, code samples |
| `.claude/skills/cba/references/security.md` | Keycloak config, Spring Security, encryption, FAPI 2.0, PCI-DSS |
| `.claude/skills/cba/references/stack.md` | pom.xml, application.yml, Angular packages, Flutter pubspec.yaml |
| `.claude/skills/cba/references/deployment.md` | Docker Compose, Dockerfiles, Kubernetes manifests, Getting Started |
| `.claude/skills/cba/references/design.md` | Nubeero design tokens, layout system, Stitch workflow |
| `.claude/skills/cba/designs/tokens.scss` | Complete SCSS token set (source of truth for all UI) |
| `.claude/skills/cba/designs/screens/backoffice/` | dashboard.html, customers.html, loans.html prototypes |

---

## CI/CD & Deployment Pipeline

### Multi-Component Repository Structure

```
CoreBanking/                          ← monorepo root (this repo)
├── backend/                          → Docker → Kubernetes (GHCR images)
├── web/                              → Vercel (Angular static site)
├── mobile/                           → GitHub Artifacts (APK / IPA)
├── infrastructure/                   → k8s manifests applied via kubectl
├── .github/
│   ├── workflows/
│   │   ├── backend-ci.yml            → Java CI: test → OWASP → SpotBugs → Docker → K8s
│   │   ├── web-ci.yml                → Angular CI: lint → test → build → Vercel deploy
│   │   ├── mobile-ci.yml             → Flutter CI: analyze → test → APK/IPA build
│   │   └── security-scan.yml         → CodeQL + Trivy + Gitleaks + Snyk + ZAP (scheduled)
│   └── dependabot.yml                → Weekly auto-updates: Maven, npm, pub, Docker, Actions
└── docs/
    └── owasp-suppressions.xml        → OWASP false-positive suppressions (justified)
```

### GitHub Actions — Pipeline Summary

| Workflow | Trigger | Path Filter | Key Jobs |
|----------|---------|-------------|----------|
| `backend-ci.yml` | push/PR to main/develop | `backend/**` | test → sonar → owasp → spotbugs → docker → deploy-k8s |
| `web-ci.yml` | push/PR to main/develop | `web/**` | test → security → build → vercel-deploy → e2e |
| `mobile-ci.yml` | push/PR to main/develop | `mobile/**` | test → dart-audit → build-android → build-ios |
| `security-scan.yml` | push/PR + cron (Mon 03:00) | all | codeql → trivy-fs → gitleaks → dependency-review → snyk → zap |

### Vercel Deployment (Angular Web App)

**Config**: `web/vercel.json`
**Pattern**: `--prebuilt` — CI builds the Angular app, tests it, then deploys the artifact (Vercel never rebuilds)

```
PR opened      → vercel build → vercel deploy --prebuilt     → Preview URL auto-commented on PR
Push to develop → vercel build --prod → vercel deploy --prebuilt     → Staging alias
Push to main   → vercel build --prod → vercel deploy --prebuilt --prod → Production
```

**Required GitHub Secrets for Vercel**:
```
VERCEL_TOKEN          — Personal Access Token from vercel.com/account/tokens
```
**Required after `vercel link --repo`** (from `web/.vercel/project.json`):
```
VERCEL_ORG_ID         — Add to GitHub Secrets
VERCEL_PROJECT_ID_WEB — Add to GitHub Secrets
```

**Vercel CLI Setup** (one-time, run locally in `web/` directory):
```bash
npm install -g vercel
vercel login
cd web
vercel link --repo      # Links the monorepo; creates .vercel/repo.json
vercel pull             # Pulls project settings and env vars
```

**vercel.json features**:
- Framework: `angular` (static SPA)
- SPA rewrites: all non-API routes → `index.html`
- Security headers: CSP, HSTS, X-Frame-Options, Referrer-Policy, Permissions-Policy
- Static asset caching: `max-age=31536000, immutable` for JS/CSS/assets

### Docker Image Registry

Backend images published to GitHub Container Registry (GHCR):
```
ghcr.io/{github-org}/{repo}/cba-backend:sha-{commit-sha}
ghcr.io/{github-org}/{repo}/cba-backend:main
ghcr.io/{github-org}/{repo}/cba-backend:develop
```
Images tagged with commit SHA — Kubernetes deployments reference SHA tags for deterministic rollouts.

### Kubernetes Deployment Flow

1. Docker image built and pushed to GHCR
2. Trivy scans image for CVEs (blocks on CRITICAL/HIGH)
3. `deployment.yaml` patched with new image SHA tag via `sed`
4. `kubectl apply -f infrastructure/k8s/` applies all manifests
5. `kubectl rollout status` waits up to 300s for pod readiness
6. GitHub environments (`staging`, `production`) provide deployment protection rules

---

## Automated Testing Strategy

### Coverage Requirements
- **Backend (Java)**: 70% line coverage minimum (JaCoCo + SonarCloud quality gate)
- **Web (Angular)**: 70% line coverage minimum (Karma + Istanbul, checked in CI)
- **Mobile (Flutter)**: Coverage tracked via Codecov (no hard minimum yet)

### Test Layers

| Layer | Tool | What it tests |
|-------|------|---------------|
| Backend unit tests | JUnit 5 + Mockito | Service logic, DTO mapping, validators |
| Backend integration | Testcontainers (real PostgreSQL) | Repositories, Flyway migrations, JPA queries |
| Backend API contract | SpringMockMvc | Controller layer, request/response shapes |
| Web unit tests | Karma + Jasmine | Components, services, pipes, guards |
| Web E2E | Playwright (against Vercel preview) | Full user flows on real deployed preview |
| Mobile unit tests | flutter_test | Providers, services, widget trees |
| SonarCloud gate | SonarCloud | Coverage, bugs, code smells, security hotspots |

### Security Testing Layers

| Tool | Type | When | Threshold |
|------|------|------|-----------|
| OWASP Dependency Check | CVE scan (Java deps) | Every backend push | Fail on CVSS ≥ 7.0 |
| npm audit | CVE scan (npm deps) | Every web push | Fail on high/critical |
| Snyk | CVE scan (Java + npm) | Every push | Fail on high severity |
| SpotBugs | Static analysis (Java) | Every backend push | Fail on any finding |
| GitHub CodeQL | SAST (Java + TypeScript) | Every push + PRs | security-extended queries |
| Trivy (filesystem) | CVE + IaC + secrets | Every push | CRITICAL/HIGH reported to Security tab |
| Trivy (Docker image) | Container CVE scan | On Docker build | Fail on CRITICAL/HIGH |
| Gitleaks | Secret detection | Every push + PRs | Fail on any hardcoded secret |
| Dependency Review | License + CVE (PRs) | PRs only | Fail on high CVE or GPL license |
| OWASP ZAP | Dynamic API scan | Weekly (scheduled) | Report only (no block) |
| Trivy (k8s configs) | IaC misconfiguration | Every push | CRITICAL/HIGH reported |

### Dependency Auto-Updates (Dependabot)

Dependabot runs every Monday, grouped by ecosystem:
- Maven: Spring Boot group, Security group, Testing group
- npm: Angular group, Angular Material group
- pub (Dart/Flutter): ungrouped
- Docker: all Dockerfiles
- GitHub Actions: all action pinning
- Major version bumps are ignored (require manual upgrade)

---

## Required Secrets & Variables Setup

### GitHub Repository Secrets (Settings → Secrets and variables → Actions → Secrets)

| Secret | Where to get it |
|--------|----------------|
| `VERCEL_TOKEN` | vercel.com → Account Settings → Tokens |
| `SONAR_TOKEN` | sonarcloud.io → My Account → Security |
| `SNYK_TOKEN` | app.snyk.io → Settings → API Token |
| `NVD_API_KEY` | nvd.nist.gov/developers/request-an-api-key |
| `KUBE_CONFIG_STAGING` | `cat ~/.kube/staging-config \| base64` |
| `KUBE_CONFIG_PROD` | `cat ~/.kube/prod-config \| base64` |
| `GITLEAKS_LICENSE` | gitleaks.io (optional, for commercial tier) |

### GitHub Repository Variables (Settings → Secrets and variables → Actions → Variables)

| Variable | Value |
|----------|-------|
| `SONAR_ORG` | Your SonarCloud organization slug |
| `API_BASE_URL_STAGING` | `https://api-staging.cba.com` |
| `KEYCLOAK_URL_STAGING` | `https://auth-staging.cba.com` |
| `API_STAGING_URL` | `https://api-staging.cba.com` (used by ZAP) |

---

## First-Time Project Setup Checklist

When the GitHub repo is first created, complete these steps before any pipeline runs:

```bash
# 1. Push this repo to GitHub
git init && git add . && git commit -m "chore: initial CBA platform scaffold"
git remote add origin https://github.com/{your-org}/cba-platform.git
git push -u origin main

# 2. Link the web project to Vercel
cd web
vercel login
vercel link --repo
vercel pull --yes --environment=production
# Copy VERCEL_ORG_ID and VERCEL_PROJECT_ID from web/.vercel/project.json
# → Add both as GitHub Secrets

# 3. Set up SonarCloud
# → Create project at sonarcloud.io, generate SONAR_TOKEN, set SONAR_ORG variable

# 4. Add all other secrets listed above

# 5. Enable GitHub Advanced Security (for CodeQL and Secret Scanning)
# → Settings → Security → Code security and analysis → Enable all

# 6. Create environments in GitHub
# → Settings → Environments → Create "staging" and "production"
# → Add required reviewers for "production" environment
```

---

## GitHub Token Management

**Repo**: https://github.com/RazorMVP/cba-platform
**Username**: RazorMVP

### Token Storage
The GitHub PAT is stored locally in `.claude/skills/cba/credentials.json`.
This file is gitignored — it is NEVER committed or pushed to GitHub.

### When the Token Expires
1. Generate a new token at: https://github.com/settings/tokens
   - Required scopes: `repo` (full), `workflow`, `write:packages`, `delete:packages`
2. Update the token in `.claude/skills/cba/credentials.json`
3. Re-apply to git remote:
   ```bash
   git remote set-url origin https://RazorMVP:{new-token}@github.com/RazorMVP/cba-platform.git
   ```

### Security Rule
The token lives only in:
- `.git/config` (local git config, never tracked)
- `.claude/skills/cba/credentials.json` (gitignored)

Never paste the token into any file that is tracked by git.
