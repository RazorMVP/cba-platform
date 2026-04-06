# CBA Platform — Change Log

> Tracks all implementation work, gaps, and decisions for the Core Banking Application.
> Updated as changes are made. Newest entries at the top of each section.

---

## Backend Audit — Current State vs CLAUDE.md Body of Knowledge

### ✅ Fully Built

| Module / Component | Files | Notes |
|--------------------|-------|-------|
| **Customer Module** | `customer/Customer.java`, `CustomerService`, `CustomerController`, `CustomerRepository`, `CustomerMapper`, `KycStatus`, DTOs | PII encrypted; KYC status flow: `PENDING_KYC → ACTIVE → SUSPENDED → CLOSED` |
| **Account Module** | `account/Account.java`, `AccountService`, `AccountController`, `AccountRepository`, `Transaction`, `TransactionRepository`, `AccountNumberGenerator`, `AccountType`, `AccountStatus`, DTOs | Double-entry transactions; `SELECT FOR UPDATE`; number format `{branch}-{type}-{seq}` |
| **Loan Module** | `loan/Loan.java`, `LoanService`, `LoanController`, `LoanRepository`, `LoanRepaymentSchedule`, `RepaymentScheduleEngine`, `LoanStatus`, DTOs | Annuity EMI `P×r×(1+r)^n/((1+r)^n-1)`; full status lifecycle |
| **Payment Module** | `payment/Payment.java`, `PaymentService`, `PaymentController`, `PaymentRepository`, `PaymentType`, `PaymentStatus`, DTOs | Double-entry; deadlock-safe UUID lock ordering; cross-currency support |
| **Product Module** | `product/LoanProduct.java`, `DepositProduct.java`, `LoanProductRepository`, `DepositProductRepository`, `RepaymentType`, `InterestCompounding`, `DepositAccountType`, `ProductService`, `ProductController`, DTOs | Full CRUD: `GET/POST/PUT/DELETE /api/v1/loan-products` and `/api/v1/deposit-products`; range validation; audit logging |
| **Teller / Cash Management** | `teller/Teller`, `Cashier`, `TellerSession`, `CashTransaction`, repositories, `TellerService`, `TellerController`, DTOs; `V5__teller_module.sql` | Full session lifecycle: create teller → activate → assign cashier → open session → cash-in/cash-out → settle; mirrors Mifos pattern |
| **Open Banking (FAPI 2.0)** | `openbanking/OpenBankingConsent`, `ConsentStatus`, `ConsentRepository`, `ConsentService`, `ConsentController`, `AccountInfoController`, `PispController`, `CbpiiController`, DTOs | Full stack: consent lifecycle + AISP (accounts/balances/transactions) + PISP (domestic-payments) + CBPII (funds-confirmation) |
| **Notification Module** | `notification/AccountEvent`, `LoanEvent`, `NotificationEventListener` | Spring `@EventListener` + `@Async`; hooks for account/loan events |
| **Audit Module** | `audit/AuditLog`, `AuditLogRepository`, `AuditLogService` | Append-only; `@Transactional(REQUIRES_NEW)`; 7-year retention policy |
| **Multi-Currency / Tenant** | `currency/ExchangeRate`, `ExchangeRateService`, `ExchangeRateController`, `ExchangeRateRepository`, DTOs; `tenant/Tenant`, `TenantService`, `TenantInterceptor`, `TenantRepository`; `common/tenant/TenantContext` | Tenant base currency; auto-inverse rates; cross-currency transfers; `X-Tenant-ID` header routing |
| **Common / Config** | `common/response/ApiResponse`, `CbaException`, `GlobalExceptionHandler`, `AuditableEntity`, `FieldEncryptor` (AES-256 `PBEWITHHMACSHA512ANDAES_256`), `EncryptedStringConverter`, `TenantContext`; `config/SecurityConfig`, `OpenApiConfig`, `AuditConfig`, `WebMvcConfig` | Keycloak JWT RBAC; CORS; CSP headers; OpenAPI 3.1 at `/swagger-ui.html` |
| **Flyway Migrations** | `V1__init_schema.sql`, `V2__demo_data.sql`, `V3__multi_currency.sql`, `V4__multi_currency_demo_data.sql` | UUID PKs; NUMERIC(19,4) money; optimistic locking; 3 tenant demo deployments (USD/KES/GHS) |
| **CI/CD Pipelines** | `.github/workflows/backend-ci.yml`, `web-ci.yml`, `mobile-ci.yml`, `security-scan.yml`, `dependabot.yml` | Java test → OWASP → SpotBugs → Docker → K8s; Vercel deploy for web; CodeQL + Trivy + Gitleaks |
| **Tests** | `RepaymentScheduleEngineTest` (6), `CustomerServiceTest` (3), `ExchangeRateServiceTest` (7), `PaymentServiceIT` (3), `CustomerRepositoryIT` | 16 unit tests + integration tests; Testcontainers PostgreSQL |

---

### ⚠️ Partially Built

_None — all Phase 1 backend modules are now complete._

---

### ❌ Not Yet Built

| Component | Required by CLAUDE.md | Priority |
|-----------|----------------------|----------|
| **Group & Center Module** | Group creation/activation, collection sheets, GLIM | Phase 1 backend gap (microfinance) |
| **Infrastructure — Docker Compose** | `infrastructure/docker-compose.yml` with postgres, keycloak, backend, web, mailhog | Phase 4 |
| **Infrastructure — Kubernetes** | `infrastructure/k8s/` namespace, deployments, services, ingress, HPA, sealed secrets | Phase 4 |
| **Infrastructure — Keycloak Realm** | `infrastructure/keycloak/cba-realm.json` with cba realm, 3 clients, FAPI 2.0, demo users | Phase 4 |
| **Web Frontend — Angular** | Full backoffice portal (dashboard, customers, accounts, loans, payments, reports) | Phase 2 |
| **Mobile Frontend — Flutter** | Customer mobile app (auth, dashboard, accounts, loans, payments, profile) | Phase 3 |

---

## Change History

### Session 4 — 2026-04-06

**Backend gap closure: Product Controller, Open Banking completion, Teller/Cash Management + Postman collection**

#### Added
- `backend/src/main/java/com/cba/product/ProductService.java` — CRUD service for loan and deposit products with range validation (`minPrincipal ≤ maxPrincipal`, default rate within range, term bounds)
- `backend/src/main/java/com/cba/product/ProductController.java` — `GET/POST/PUT/DELETE /api/v1/loan-products` and `/api/v1/deposit-products`; ADMIN writes, all roles read
- `backend/src/main/java/com/cba/product/dto/` — `LoanProductRequest`, `LoanProductResponse`, `DepositProductRequest`, `DepositProductResponse` records
- `backend/src/main/java/com/cba/openbanking/ConsentRepository.java` — `findByConsentId(String)` lookup
- `backend/src/main/java/com/cba/openbanking/ConsentService.java` — `createConsent`, `getConsent`, `authoriseConsent`, `revokeConsent`, `validatePispConsent`, `confirmFunds`; `consentId` generated as `ob-{18-char-hex}`
- `backend/src/main/java/com/cba/openbanking/ConsentController.java` — `POST /open-banking/v3.1/consents`, `GET /{id}`, `PUT /{id}/authorise`, `DELETE /{id}` (revoke)
- `backend/src/main/java/com/cba/openbanking/PispController.java` — `POST /open-banking/v3.1/pisp/domestic-payments`, `GET /domestic-payments/{id}`; delegates to `PaymentService.transfer()` after consent validation
- `backend/src/main/java/com/cba/openbanking/CbpiiController.java` — `POST /open-banking/v3.1/cbpii/funds-confirmations`; confirms sufficient balance without moving funds
- `backend/src/main/java/com/cba/openbanking/dto/` — `ConsentRequest`, `ConsentResponse`, `DomesticPaymentRequest`, `DomesticPaymentResponse`, `FundsConfirmationRequest`, `FundsConfirmationResponse`
- `backend/src/main/resources/db/migration/V5__teller_module.sql` — Creates `tellers`, `cashiers`, `teller_sessions` (UNIQUE on cashier+date), `cash_transactions` tables with full indexes
- `backend/src/main/java/com/cba/teller/` — Full teller module: `Teller`, `Cashier`, `TellerSession`, `CashTransaction` entities; `TellerStatus`, `SessionStatus`, `CashTransactionType` enums; 4 repositories; `TellerService`; `TellerController`; 9 DTO records
- `docs/cba-postman-collection.json` — 127KB Postman collection: 14 folders, 50+ requests; collection variables (`base_url`, `access_token`, `tenant_id`); Bearer token auth; 8 language code samples per key request (cURL, JavaScript/fetch, Python/requests, Java/OkHttp, C#/HttpClient, PHP/cURL, Go/net/http, Ruby/Net::HTTP)

#### Key design decisions
- **Open Banking Option A** — API-only consent flow; no OAuth2 redirect handling in backend (frontend/Keycloak owns the redirect); `PUT /consents/{id}/authorise` simulates customer approval step
- **Teller session uniqueness** — `UNIQUE (cashier_id, session_date)` enforced at DB level; one cashier can only open one session per day
- **Settlement formula** — `closing_balance = opening_balance + Σ(CASH_IN) - Σ(CASH_OUT)`; `difference = actual_cash - closing_balance` records variance for reconciliation
- **Cash transaction mirrors account ledger** — When `accountId` is provided, `TellerService` also updates `Account.balance` and writes an immutable `Transaction` record via `Transaction.of()` factory
- **PISP passes consent audit trail** — `createdBy` is set to `open-banking:{consentId}` so payment audit log identifies PISP-initiated transfers

#### Architecture compliance additions
| Requirement | Status |
|-------------|--------|
| Product REST CRUD | ✅ `ProductController` |
| Open Banking consent lifecycle | ✅ `ConsentService` + `ConsentController` |
| PISP domestic-payments | ✅ `PispController` |
| CBPII funds-confirmation | ✅ `CbpiiController` |
| Teller module | ✅ Full session lifecycle |
| Postman collection (8 languages) | ✅ `docs/cba-postman-collection.json` |

---

### Session 3 — 2026-04-06

**Multi-currency implementation across the entire backend**

#### Added
- `backend/src/main/resources/db/migration/V3__multi_currency.sql` — Adds `currency_code`, `country_code`, `locale_code` to tenants; creates `exchange_rates` table (UNIQUE per pair, NUMERIC(19,8) rate); adds cross-currency columns to payments; creates `supported_currencies` reference table (18 currencies)
- `backend/src/main/resources/db/migration/V4__multi_currency_demo_data.sql` — 3 tenant deployments (CBA United States/USD, CBA Kenya/KES, CBA Ghana/GHS); exchange rates (USD↔KES 135.50, USD↔GHS 15.80, KES↔GHS, USD↔EUR, USD↔GBP); KES products in Swahili; demo customers across all 3 tenants including Amina's USD foreign-currency account in KES tenant
- `backend/src/main/java/com/cba/tenant/Tenant.java` — JPA entity with currencyCode, countryCode, localeCode
- `backend/src/main/java/com/cba/tenant/TenantRepository.java`
- `backend/src/main/java/com/cba/tenant/TenantService.java` — `getBaseCurrency()` with `@Cacheable("tenants")`, USD fallback
- `backend/src/main/java/com/cba/tenant/TenantInterceptor.java` — Reads `X-Tenant-ID`, sets TenantContext, always clears in `afterCompletion`
- `backend/src/main/java/com/cba/common/tenant/TenantContext.java` — ThreadLocal tenant holder
- `backend/src/main/java/com/cba/config/WebMvcConfig.java` — Registers TenantInterceptor on `/api/**` and `/open-banking/**`
- `backend/src/main/java/com/cba/currency/ExchangeRate.java` — JPA entity
- `backend/src/main/java/com/cba/currency/ExchangeRateRepository.java`
- `backend/src/main/java/com/cba/currency/ExchangeRateService.java` — setRate (auto-inverse), getRate (identity for same-ccy), convert, getAllRates, deactivateRate
- `backend/src/main/java/com/cba/currency/ExchangeRateController.java` — `POST/GET/DELETE /api/v1/exchange-rates`
- `backend/src/main/java/com/cba/currency/dto/ConversionResult.java`, `ExchangeRateRequest.java`, `ExchangeRateResponse.java`
- `backend/.mvn/jvm.config` — `--add-opens` flags for Lombok javac access on Java 25
- `backend/src/test/java/com/cba/currency/ExchangeRateServiceTest.java` — 7 unit tests

#### Modified
- `backend/pom.xml` — Lombok 1.18.36 → **1.18.38** (Java 25 TypeTag fix); Spring Boot 3.4.4
- `backend/src/main/java/com/cba/account/AccountService.java` — Replaced hardcoded `"USD"` fallback with `tenantService.getBaseCurrency(TenantContext.getTenant())`; teller override preserved
- `backend/src/main/java/com/cba/config/SecurityConfig.java` — Removed deprecated `XssProtectionConfig` (Spring Security 6.1+); CSP handles XSS protection
- `backend/src/main/java/com/cba/payment/Payment.java` — Added cross-currency fields: `sourceCurrency`, `sourceAmount`, `destinationCurrency`, `destinationAmount`, `exchangeRateUsed`, `crossCurrency`
- `backend/src/main/java/com/cba/payment/PaymentService.java` — Detects cross-currency pairs; calls `ExchangeRateService.convert()`; debits source in its currency; credits destination in its currency; populates full audit trail on Payment record
- `backend/src/main/java/com/cba/payment/dto/PaymentResponse.java` — Added 5 cross-currency audit fields

#### Build fixes
- `TypeTag :: UNKNOWN` during clean compile → Lombok 1.18.38
- `XssProtectionConfig cannot be resolved` → Removed from SecurityConfig
- `CustomerMapperImpl cannot be resolved` (stale cache) → `./mvnw clean` forces MapStruct regeneration
- Clean compile now works: `./mvnw clean compile` succeeds; all 16 unit tests pass

#### Commits
- `b62dac2` — `feat(backend): multi-currency support across the entire system`
- `4233118` — `docs(claude): add multi-currency architecture section to CLAUDE.md`

---

### Session 2 — (prior session, reconstructed from compact)

**CI/CD pipeline and GitHub setup**

#### Added
- `.github/workflows/backend-ci.yml` — Java CI: test → SonarCloud → OWASP → SpotBugs → Docker (GHCR) → kubectl deploy
- `.github/workflows/web-ci.yml` — Angular CI: lint → test → build → Vercel deploy → Playwright E2E
- `.github/workflows/mobile-ci.yml` — Flutter CI: analyze → dart audit → APK/IPA build
- `.github/workflows/security-scan.yml` — CodeQL + Trivy (filesystem + Docker) + Gitleaks + Snyk + OWASP ZAP (weekly)
- `.github/dependabot.yml` — Weekly updates for Maven, npm, pub, Docker, GitHub Actions
- `web/vercel.json` — Angular SPA config; security headers (CSP, HSTS, X-Frame-Options); static asset caching
- `docs/owasp-suppressions.xml` — OWASP false-positive suppressions
- `sonar-project.properties` — SonarCloud project config

#### Commits
- Initial scaffold + CI/CD setup pushed to `github.com/RazorMVP/cba-platform`

---

### Session 1 — (prior session, reconstructed from compact)

**Phase 1 Backend — full Spring Boot 3 scaffold**

#### Added
- `backend/pom.xml` — Spring Boot 3.4.4, Lombok, MapStruct, springdoc, Testcontainers, JaCoCo, SpotBugs, Surefire (ByteBuddy experimental)
- `backend/src/main/resources/application.yml` — Profiles: dev (PostgreSQL), test (Testcontainers), prod (env vars + Redis + SMTP)
- `backend/src/main/resources/db/migration/V1__init_schema.sql` — Full schema: tenants, customers (PII encrypted), accounts, transactions, loans, loan_repayment_schedule, payments, standing_orders, open_banking_consents, audit_log, account_number_sequences, loan_products, deposit_products
- `backend/src/main/resources/db/migration/V2__demo_data.sql` — Default tenant, 3 deposit products, 3 loan products, 3 customers, 4 accounts, 1 active loan with schedule
- All 79 Java source files across: `common`, `config`, `customer`, `account`, `product`, `loan`, `payment`, `openbanking`, `notification`, `audit`
- `backend/src/test/` — RepaymentScheduleEngineTest, CustomerServiceTest, AbstractIntegrationTest, PaymentServiceIT, CustomerRepositoryIT
- `backend/.mvn/wrapper/` — Maven wrapper (no global mvn required)

---

## Pending Work (Next Sessions)

### High Priority — Backend Gaps
1. **Product Controller** — `GET/POST/PUT /api/v1/loan-products` and `/api/v1/deposit-products`
2. **Open Banking completion** — ConsentService, PISP domestic-payments, CBPII funds-confirmation, consent lifecycle endpoints
3. **Teller / Cash Management Module** — `Teller`, `Cashier` entities; endpoints `POST /api/v1/tellers`, cashier allocation, cash in/out, settlement
4. **Group & Center Module** — Group entity, activation workflow, collection sheets (microfinance use case)
5. **Close of Business (CoB) jobs** — `@Scheduled` interest accrual, arrears classification, standing order execution

### Phase 2 — Web Frontend (Angular 17)
- Scaffold with CLI (`--standalone`)
- Feature modules: dashboard, customers, accounts, loans, payments, reports
- Keycloak-Angular OIDC integration
- Nubeero design system (tokens from `designs/tokens.scss`, prototypes in `designs/screens/backoffice/`)

### Phase 3 — Mobile Frontend (Flutter 3)
- Feature-first structure: `features/{auth,dashboard,accounts,loans,payments,profile}/`
- flutter_appauth OIDC; Riverpod state; go_router navigation; biometric auth

### Phase 4 — Infrastructure
- `infrastructure/docker-compose.yml` — postgres, keycloak, backend, web, mailhog
- `infrastructure/k8s/` — namespace, deployments, services, ingress, HPA, ConfigMaps, Secrets
- `infrastructure/keycloak/cba-realm.json` — cba realm, 3 clients (backend/web/mobile), FAPI 2.0, demo users

---

## Architecture Compliance Checklist

| CLAUDE.md Requirement | Implemented | Notes |
|-----------------------|-------------|-------|
| UUID primary keys | ✅ | `gen_random_uuid()` on all tables |
| NUMERIC(19,4) for money | ✅ | All balance/amount columns |
| Optimistic locking (`version`) | ✅ | All mutable entities |
| Flyway owns schema (`ddl-auto: validate`) | ✅ | Never `create/update` in non-test |
| `@Transactional` on all service methods | ✅ | Including `readOnly = true` for reads |
| `@Transactional(REQUIRES_NEW)` for audit | ✅ | `AuditLogService` |
| `SELECT FOR UPDATE` for money transfers | ✅ | `AccountRepository.findByIdWithLock()` |
| Deadlock-safe UUID ordering | ✅ | `min/max(UUID)` before lock acquisition |
| PII field-level encryption (AES-256) | ✅ | `EncryptedStringConverter` + `PBEWITHHMACSHA512ANDAES_256` |
| Keycloak JWT + RBAC | ✅ | `realm_access.roles` claim; 4 roles |
| Standard response envelope | ✅ | `ApiResponse<T>` with data/meta/errors |
| OpenAPI 3.1 docs | ✅ | springdoc at `/swagger-ui.html` |
| Annuity EMI formula | ✅ | `RepaymentScheduleEngine` |
| Double-entry ledger | ✅ | Debit + Credit Transaction records per transfer |
| Audit trail on write operations | ✅ | `AuditLogService.log()` in all services |
| Tenant base currency (not hardcoded USD) | ✅ | `TenantContext` + `TenantService.getBaseCurrency()` |
| Auto-inverse exchange rates | ✅ | `ExchangeRateService.setRate()` |
| Cross-currency transfer with FX audit | ✅ | `PaymentService.transfer()` |
| Event-driven notifications | ✅ | `@EventListener` + `@Async` |
| Testcontainers (no mock DB) | ✅ | `AbstractIntegrationTest` |
| No PII in logs | ✅ | Encrypted PII never logged directly |
| ENCRYPTION_KEY from environment | ✅ | `FieldEncryptor` reads env var |
| Teller module | ✅ | `TellerController` + session lifecycle (`V5__teller_module.sql`) |
| Product REST endpoints | ✅ | `ProductController` — `GET/POST/PUT/DELETE /api/v1/loan-products` + deposit-products |
| Open Banking consent lifecycle | ✅ | `ConsentService` + `ConsentController` (AWAITING_AUTHORISATION → AUTHORISED → REVOKED) |
| PISP domestic-payments | ✅ | `PispController` — validates consent then delegates to `PaymentService` |
| CBPII funds-confirmation | ✅ | `CbpiiController` — balance check without fund movement |
| Postman collection (8 languages) | ✅ | `docs/cba-postman-collection.json` — 14 folders, 50+ requests |
| Group & Center module | ❌ | Not built |
| CoB batch processing | ❌ | Not built |
| Docker Compose | ❌ | Not built |
| Kubernetes manifests | ❌ | Not built |
| Keycloak realm JSON | ❌ | Not built |
| Angular web portal | ❌ | Not built |
| Flutter mobile app | ❌ | Not built |
