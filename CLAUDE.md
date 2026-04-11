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

### Mifos API Live Documentation — Section Index

These are the canonical reference sections from the Mifos API live docs. Consult them when designing or extending any module:

| Section | URL | When to Use |
|---------|-----|-------------|
| Top / Introduction | https://demo.mifos.io/api-docs/apiLive.htm#top | Project overview, API conventions |
| Interact with API | https://demo.mifos.io/api-docs/apiLive.htm#interact | Testing via browser, REST plugins |
| General Options | https://demo.mifos.io/api-docs/apiLive.htm#genopts | Fields, pretty, template params |
| Creates & Updates | https://demo.mifos.io/api-docs/apiLive.htm#creates_and_updates | Date/number format conventions |
| Field Descriptions | https://demo.mifos.io/api-docs/apiLive.htm#field_descriptions | Field-level documentation |
| Authentication Overview | https://demo.mifos.io/api-docs/apiLive.htm#authentication_overview | Auth patterns |
| Basic Auth | https://demo.mifos.io/api-docs/apiLive.htm#authentication_basicauth | HTTP Basic implementation |
| OAuth2 Auth | https://demo.mifos.io/api-docs/apiLive.htm#authentication_oauth | OAuth2 ROPC implementation |
| Error Handling | https://demo.mifos.io/api-docs/apiLive.htm#errors | Error response format, HTTP codes |
| Batch API | https://demo.mifos.io/api-docs/apiLive.htm#batch_api | Bulk operations, dependent requests |
| Full API Matrix | https://demo.mifos.io/api-docs/apiLive.htm#fullapi_matrix | Complete endpoint inventory |
| Payment Logic | https://demo.mifos.io/api-docs/apiLive.htm#paymentapplicationlogic | Repayment allocation strategies |
| Self Service Overview | https://demo.mifos.io/api-docs/apiLive.htm#selfservice_overview | Customer-facing API scope |
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
- **Loan products** — full Mifos parity: shortName (UNIQUE, 4 chars), fund @ManyToOne, principal range + default, installmentAmountInMultiplesOf, full interest config (interestRateFrequencyType, interestType, amortizationType, interestCalculationPeriodType, daysInYearType, daysInMonthType), repayment schedule (numberOfRepayments, repaymentEvery, repaymentFrequencyType), grace periods, @Embedded `AllowAttributeOverrides` (8 boolean overrides), 8 GL account @ManyToOne linkages, @ManyToMany charges via `loan_product_charges` join table
- **Deposit products** — full Mifos parity: shortName (UNIQUE, 4 chars), accountType, minimumBalance, minRequiredOpeningBalance, interestCompounding, interestPostingPeriodType, daysInYearType, daysInMonthType, lockinPeriodFrequency, withdrawalFeeForTransfers, allowOverdraft + overdraftLimit + overdraftInterestRate + minOverdraftForInterestCalculation, accountingType (NONE/CASH), 8 GL account @ManyToOne linkages, @ManyToMany charges via `deposit_product_charges` join table
- Repayment types: `ANNUITY`, `FLAT`, `DECLINING_BALANCE`
- Interest compounding: `DAILY`, `MONTHLY`, `QUARTERLY`, `ANNUALLY`
- Full REST CRUD: `GET/POST/PUT/DELETE /api/v1/loan-products` and `/api/v1/deposit-products`
- `ProductService` validates ranges: `minPrincipal ≤ maxPrincipal`, `defaultInterestRate` within `[min, max]`, `minTermMonths ≤ maxTermMonths`
- ADMIN role required for writes; all authenticated roles can read
- Flyway `V20__product_mifos_parity.sql` — 30+ columns added to loan_products, 20+ to deposit_products; backfills `short_name` from name via `REGEXP_REPLACE`, then sets NOT NULL
- `AllowAttributeOverrides` — `@Embeddable` class stored as 8 individual boolean columns (prefix `allow_override_`), not JSON
- GL account linkages — response uses nested `GlAccountRef(id, glCode, name)` record to avoid N+1; null-safe via `GlAccountRef.of(GlAccount)`
- Charges — replace-all on update: `p.getCharges().clear(); p.getCharges().addAll(resolved)` (same pattern as permissions / provisioning criteria)

### 6. Open Banking Module (FAPI 2.0)
- UK Open Banking v3.1 compliant endpoints
- Base path: `/open-banking/v3.1/`
- AISP: accounts, balances, transactions
- PISP: domestic-payments initiation
- CBPII: funds-confirmation
- Each TPP access requires a `Consent` record with scopes and expiry
- Consent flow: `AWAITING_AUTHORISATION → AUTHORISED → REVOKED`
- **Implementation (Option A — API-only):** `ConsentController` exposes `POST /consents`, `GET /consents/{id}`, `PUT /consents/{id}/authorise`, `DELETE /consents/{id}`; no OAuth2 redirect in backend — frontend/Keycloak owns the redirect
- `ConsentService.validatePispConsent()` checks status=AUTHORISED, scope includes `payments`, and expiry not passed before any PISP call
- `CbpiiController` checks balance ≥ requested amount without moving funds; returns `fundsAvailable: true/false`

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
- Teller creation and management (`INACTIVE → ACTIVE → CLOSED`)
- Cashier management: assign staff to a teller desk with optional shift hours
- Full session lifecycle: open session with opening float → cash-in/cash-out transactions → close with settlement reconciliation
- Settlement: `closing_balance = opening_balance + Σ(CASH_IN) - Σ(CASH_OUT)`; `difference = actual_cash - closing_balance`
- DB constraint: `UNIQUE (cashier_id, session_date)` — one session per cashier per day
- Cash transactions optionally linked to a customer `Account`; account balance and immutable `Transaction` record updated atomically
- Endpoints: `POST /api/v1/tellers`, `POST /api/v1/tellers/{id}/activate`, `POST /api/v1/tellers/{id}/cashiers`, `POST /api/v1/tellers/{id}/cashiers/{cId}/sessions`, `POST /api/v1/tellers/{id}/sessions/{sId}/transactions`, `POST /api/v1/tellers/{id}/sessions/{sId}/settle`
- Flyway migration: `V5__teller_module.sql`

### 10. Group & Center Module (from Mifos — for microfinance use cases)
- Group creation, activation, staff assignment
- Collection sheet generation and processing
- Client association with groups
- GLIM (Group Loan Individual Monitoring) support
- Entities: `Center`, `Group`, `GroupMember`, `GlimAccount`, `CollectionSheet`, `CollectionSheetItem`
- Endpoints: `POST/GET /api/v1/groups`, `POST /api/v1/groups/{id}/activate`, `POST/DELETE /api/v1/groups/{groupId}/members/{customerId}`, `POST /api/v1/collectionsheets`, `GET /api/v1/groups/{groupId}/glimaccounts`

### 11. Office & Staff Module
- Branch office hierarchy using materialised path (`hierarchy` column: `".parentId.id."`)
- Staff management with office assignment and loan officer flag
- Entities: `Office`, `Staff`; package: `com.cba.office`
- Endpoints: `POST/GET/PUT /api/v1/offices`, `POST/GET/PUT/DELETE /api/v1/staff`

### 12. User Management Module
- Platform users synced with Keycloak Admin REST API (`keycloak-admin-client:23.0.7`)
- Creates user in Keycloak first (gets UUID back), then persists `PlatformUser` locally
- Roles assigned in Keycloak realm; mirrored in `user_roles` table
- Entity: `PlatformUser`; Config: `KeycloakAdminConfig` (Keycloak Admin Client Spring bean)
- Endpoints: `POST/GET /api/v1/users`, `GET/DELETE /api/v1/users/{id}`, `POST /api/v1/users/{id}/enable|disable`

### 13. Self Service Module
- Customer-facing endpoints — JWT `sub` mapped to `Customer.keycloakId` (V11 migration)
- Returns 404 (not 403) when resource belongs to another customer (prevents enumeration attacks)
- Package: `com.cba.selfservice`; Facade: `SelfServiceFacade`
- Endpoints (all CUSTOMER role): `GET /api/v1/self/userdetails`, `/self/accounts`, `/self/accounts/{id}/transactions`, `/self/loans`, `/self/loans/{id}`

### 14. GL / Accounting Module
- Double-entry journal with auto-posting + manual journal entries (Option C)
- `FinancialActivityAccount` maps abstract activities (ASSET_LOAN_PORTFOLIO, INCOME_INTEREST, etc.) to concrete GL codes
- `GlAccountingService.postDoubleEntry()` — invoked by domain services at transaction time
- `GlAccountingService.postManualEntries()` — requires balanced debits/credits; blocks DML SQL
- Package: `com.cba.accounting`
- Entities: `GlAccount`, `JournalEntry`, `FinancialActivityAccount`, `GlClosure`
- Endpoints: `GET /api/v1/glaccounts`, `POST/GET /api/v1/journalentries`, `POST /api/v1/journalentries/{id}/reverse`, `POST/GET /api/v1/glclosures`

### 15. Reports Module
- Dynamic SQL engine: report SQL with `${paramName}` placeholders stored in DB (V9 migration)
- `ReportService.runReport()` resolves params, validates SELECT-only, executes via `JdbcTemplate.queryForList()`
- Blocks DML keywords and injection characters (`'`, `;`, `--`) in parameter values
- 7 seed reports: ActiveLoans, LoansInArrears, SavingsBalance, TellerCashPosition, CustomerAcquisition, TrialBalance, LoanProductSummary
- Package: `com.cba.report`; Entities: `Report`, `ReportParameter`
- Endpoints: `GET /api/v1/reports`, `GET/DELETE /api/v1/reports/{id}`, `GET /api/v1/runreports/{reportName}?param=value`

### 16. CoB Scheduler Module (Close of Business)
- Spring Batch jobs + Quartz triggers; both schemas managed by Flyway V10 (`initialize-schema: never`)
- Nightly schedule: standing-orders (23:55) → interest-accrual (23:57) → arrears (23:59)
- `QuartzJobBridge extends QuartzJobBean` bridges Quartz → Spring Batch; looks up bean by `jobBeanName` job data key
- Entity: `CobJobHistory`; Package: `com.cba.cob`
- Endpoints: `GET /api/v1/jobs`, `POST /api/v1/jobs/{jobName}/run`, `GET /api/v1/jobs/{jobName}/history`
- Valid job names: `standingOrderExecutionJob`, `interestAccrualJob`, `arrearsClassificationJob`

### 17. Batch API Module
- Executes multiple sub-requests in a single HTTP call (Mifos-compatible)
- JSON Path reference resolution: `"$.fieldName"` in body/URL references prior step response body
- `enclosingTransaction=true` — all steps share one DB transaction; any 4xx/5xx rolls back all
- Internal dispatch via `RestTemplate` self-calls (`http://localhost:{port}`); forwards `Authorization` + `X-Tenant-ID`
- Package: `com.cba.batch`
- Endpoint: `POST /api/v1/batches?enclosingTransaction=false`

### 18. Charges Module
- Charge definitions (master templates) + loan charges (applied instances), Mifos-compatible
- `ChargeTimeType` enum: `DISBURSEMENT`, `SPECIFIED_DUE_DATE`, `INSTALLMENT_FEE`, `OVERDUE_INSTALLMENT`
- `ChargeCalculationType` enum: `FLAT`, `PERCENT_OF_AMOUNT`, `PERCENT_OF_AMOUNT_AND_INTEREST`
- `LoanCharge` links a definition to a loan; tracks `amountPaid`, `waived`, `outstanding`
- Pay charge command updates `amountPaid` and creates an immutable `Transaction` record
- Cross-package lookup via `EntityManager.find(Loan.class, loanId)` (avoids importing LoanRepository)
- Flyway migration: `V12__charges_module.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/charges`, `GET/POST /api/v1/loans/{id}/charges`, `POST /api/v1/loans/{id}/charges/{cId}?command=pay`, `DELETE /api/v1/loans/{id}/charges/{cId}`

### 19. Fixed Deposit Module
- Full product + account lifecycle for term deposits
- `FixedDepositProduct`: `nominalAnnualInterestRate`, `minDepositAmount`/`maxDepositAmount`, `minDepositTerm`, `penaltyInterestRate`
- `FixedDepositAccount.Status` enum: `SUBMITTED`, `APPROVED`, `ACTIVE`, `PREMATURE_CLOSED`, `MATURED`, `CLOSED`, `REJECTED`
- Command pattern via `?command=approve|activate|reject|prematureClose|mature`
- Premature close applies penalty interest; mature calculates full interest
- Package: `com.cba.deposit`; Flyway: `V13__fixed_deposit_module.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/fixeddepositproducts`, `GET/POST /api/v1/fixeddepositaccounts`, `POST /api/v1/fixeddepositaccounts/{id}?command=...`

### 20. Recurring Deposit Module
- Monthly/periodic savings plan with mandatory installment amounts
- `RecurringDepositProduct`: `recurringDepositFrequency`, `recurringDepositFrequencyTypeId`, `minDepositAmount`
- `RecurringDepositAccount.Status` enum: matches Fixed Deposit status flow
- Validates installment amounts on activation; applies accrual on maturity
- Package: `com.cba.deposit`; Flyway: `V13__fixed_deposit_module.sql` (same migration)
- Endpoints: `GET/POST/PUT /api/v1/recurringdepositproducts`, `GET/POST /api/v1/recurringdepositaccounts`, `POST /api/v1/recurringdepositaccounts/{id}?command=...`

### 21. Share Products & Accounts Module
- Equity share issuance and redemption for cooperative/MFI institutions
- `ShareProduct`: `unitPrice`, `sharesIssued`, `minimumShares`, `maximumShares`, `shortName UNIQUE`
- `ShareAccount.Status` enum: `SUBMITTED`, `APPROVED`, `ACTIVE`, `CLOSED`, `REJECTED`
- `ShareAccountTransaction.TransactionType` enum: `PURCHASE`, `REDEEM`, `DIVIDEND`
- `@PrePersist` computes `totalAmount = unitPrice × numberOfShares` automatically
- Purchase updates `account.totalSharesHeld` and `product.sharesIssued`; redeem validates sufficient balance
- Package: `com.cba.share`; Flyway: `V14__share_module.sql`
- Endpoints: `GET/POST/PUT /api/v1/shareproducts`, `GET/POST /api/v1/shareaccounts`, `POST /api/v1/shareaccounts/{id}?command=...`, `GET/POST /api/v1/shareaccounts/{id}/transactions?type=purchase|redeem`

### 22. Loan Guarantors & Collateral Module
- `Guarantor`: `GuarantorType` enum `EXISTING_CUSTOMER`/`EXTERNAL`; external stores personal details inline
- `Collateral`: `value NUMERIC(19,4)`, `collateralTypeCodeValueId UUID` references code_values; `description`
- Package: `com.cba.loan`; Flyway: `V15__loan_extensions.sql`
- Endpoints: `GET/POST/DELETE /api/v1/loans/{id}/guarantors`, `GET/POST/PUT/DELETE /api/v1/loans/{id}/collaterals`

### 23. Loan Reschedule / Re-aging / Re-amortization Module
- `LoanRescheduleRequest.Status` PENDING/APPROVED/REJECTED; fields for `newInterestRate`, `graceOnPrincipal`, `graceOnInterest`, `extraTerms`, `recalculateInterest`
- `LoanReagingRequest` (Fineract 1.14.0 feature): `FrequencyType` DAYS/WEEKS/MONTHS; moves overdue installments to future dates; `preview` flag for dry-run
- `LoanReamortizationRequest` (Fineract 1.14.0 feature): triggers full schedule recalculation after partial forgiveness/modification
- All handled by `LoanExtensionService`; package: `com.cba.loan`; Flyway: `V15__loan_extensions.sql`
- Endpoints: `GET/POST /api/v1/loanreschedule`, `POST /api/v1/loanreschedule/{id}?command=approve|reject`, `GET/POST /api/v1/loans/{id}/reaging`, `GET/POST /api/v1/loans/{id}/reamortization`

### 24. Floating Rates Module
- Rate curves with dated periods; `baseLendingRate boolean` marks the reference rate
- `FloatingRate`: `isActive`, `isBaseLendingRate`, list of `FloatingRatePeriod` (`fromDate`, `interestRate`, `isDifferentialToBaseLendingRate`)
- Update replaces all periods atomically (clears `ratePeriods` list + re-adds from request)
- Package: `com.cba.system`; Flyway: `V16__system_modules.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/floatingrates`

### 25. Taxes Module
- `TaxComponent`: `percentage NUMERIC(19,6)`, `creditAccountId`/`debitAccountId` reference GL accounts, `startDate`
- `TaxGroup`: `@OneToMany` via `@JoinTable(name="tax_group_mappings")` to tax components with effective dates
- Package: `com.cba.system`; combined `TaxController` at `/api/v1/taxes/`
- Endpoints: `GET/POST/PUT /api/v1/taxes/components`, `GET/POST/PUT /api/v1/taxes/groups`

### 26. System Configuration Module
- **Codes & Code Values**: extensible enum tables; `systemDefined boolean` prevents deletion of built-in codes; `CodeValue` has `position` for UI ordering
- **Global Configuration**: `GlobalConfiguration` key-value store with `stringValue`, `numericValue`, `booleanValue`, `enabled`; seeded via Flyway
- **Funds**: `name UNIQUE`, `externalId`; referenced by loan products for fund tracking
- **Payment Types**: `cashPayment boolean`, `systemDefined boolean`; position for UI ordering; system-defined types cannot be deleted
- **Account Number Formats**: `AccountType` enum LOAN/SAVINGS/CLIENT/SHARE; `PrefixType` enum; controls auto-generated account number format
- Package: `com.cba.system`; Flyway: `V16__system_modules.sql`
- Endpoints: `GET/POST/DELETE /api/v1/codes`, `GET/POST/PUT/DELETE /api/v1/codes/{id}/codevalues`, `GET/PUT /api/v1/configurations`, `GET/POST/PUT /api/v1/funds`, `GET/POST/PUT/DELETE /api/v1/paymenttypes`, `GET/POST/PUT/DELETE /api/v1/accountnumberformats`

### 27. Notes & Documents Module
- Polymorphic: `entityType VARCHAR(50)` + `entityId UUID` — one table serves all entity types (clients, loans, accounts, etc.)
- `Document` stores file metadata only (`fileName`, `fileSize`, `contentType`, `storagePath`) — actual binary handled by external storage
- Package: `com.cba.social`; Flyway: `V17__social_modules.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/{entityType}/{entityId}/notes`, `GET/POST/DELETE /api/v1/{entityType}/{entityId}/documents`

### 28. Hooks & Holidays Module
- `Hook`: `HookType` WEB/SMS; `events List<String>` stored as `@JdbcTypeCode(SqlTypes.JSON)` JSONB column
- `Holiday`: `RepaymentSchedulingType` enum SAME_DAY/NEXT_WORKING_DAY/PREVIOUS_WORKING_DAY/NEXT_REPAYMENT_MEETING_DATE; `Status` PENDING/ACTIVE
- Package: `com.cba.social`; handled by `HookService` (manages both hooks and holidays)
- Endpoints: `GET/POST/PUT/DELETE /api/v1/hooks`, `GET/POST/DELETE /api/v1/holidays`, `POST /api/v1/holidays/{id}?command=activate`

### 29. Maker-Checker Module
- Stores full `commandAsJson TEXT` for re-execution on approval
- `Status` PENDING/APPROVED/REJECTED; `madeByUserId`/`checkedByUserId` track the two-person workflow
- `checkerUserId` passed as optional request param on approve/reject command
- Package: `com.cba.social`
- Endpoints: `GET/POST/DELETE /api/v1/makercheckers`, `POST /api/v1/makercheckers/{id}?command=approve|reject`

### 30. DataTables Module
- Dynamic schema extension using `registeredTableName`/`applicationTableName`; Mifos-compatible naming
- `DataTableColumn`: `columnName`, `columnType`, `columnLength`, `nullable`, `unique`, `codeId UUID`
- `allowMultipleRows boolean` controls one-to-one vs one-to-many extension table pattern
- Package: `com.cba.social`
- Endpoints: `GET/POST /api/v1/datatables`, `DELETE /api/v1/datatables/{registeredTableName}`

### 31. Client Identifiers & Addresses Module
- `ClientIdentifier`: `documentTypeCodeValueId UUID` (references codes), `documentKey`, `expiryDate`, `active`
- `ClientAddress`: `AddressType` HOME/WORK/MAILING; full address fields with `countryCode`
- Cross-package customer lookup via `EntityManager.find(Customer.class, customerId)`
- Package: `com.cba.customer`; Flyway: `V18__client_extensions.sql`
- Endpoints: `GET/POST/DELETE /api/v1/clients/{id}/identifiers`, `GET/POST/PUT/DELETE /api/v1/clients/{id}/addresses`

### 32. Roles & Permissions Module
- `Role`: `@ManyToMany permissions` via `@JoinTable(name="role_permissions")`; `disabled boolean`
- `Permission`: `grouping`, `code UNIQUE`, `entityName`, `actionName`, `canMakerChecker`
- `updatePermissions()` clears and re-adds the full permission set (replace-all pattern)
- Package: `com.cba.role`
- Endpoints: `GET/POST/PUT /api/v1/roles`, `GET/PUT /api/v1/roles/{id}/permissions`, `GET /api/v1/roles/permissions`

### 33. SMS Campaigns Module
- Bulk SMS campaign management (Mifos-compatible). `CampaignType` enum: `INDIVIDUAL`, `ALL`, `QUERY`; `TriggerType`: `DIRECT`, `SCHEDULED`, `TRIGGERED`
- `SmsCampaign.Status` flow: `PENDING → WAITING_FOR_ACTIVATION → ACTIVE → CLOSED → DELETED` (soft-delete)
- `SmsMessage` tracks per-recipient delivery: `DeliveryStatus` enum `PENDING | SENT | FAILED | INVALID`; linked to campaign and customer
- `recurrence` stored as iCal RRULE string (e.g. `FREQ=WEEKLY;BYDAY=MO`)
- Package: `com.cba.social`; Flyway: `V17__hooks_holidays_campaigns.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/smscampaigns`, `POST /api/v1/smscampaigns/{id}?command=activate`, `GET /api/v1/smscampaigns/{id}/messages`

### 34. Report Mailing Jobs Module
- Scheduled report delivery via email. References stored reports by `reportName`; params as JSONB `Map<String,String>`
- `OutputType` enum: `CSV`, `PDF`, `XLS`. `recurrence` as iCal RRULE. `emailRecipients` comma-separated
- Run history tracked on entity: `runCount`, `previousRunStartTime`, `previousRunEndTime`, `previousRunStatus`
- `runNow(id)` increments `runCount` and sets `previousRunStartTime`; actual email sending wired separately
- Package: `com.cba.social`; Flyway: `V17__hooks_holidays_campaigns.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/reportmailingjobs`, `POST /api/v1/reportmailingjobs/{id}?command=run`

### 35. Standing Instructions Module
- Periodic account-to-account transfer instructions (Mifos `standinginstructions` model, distinct from payment standing orders)
- `InstructionType`: `FIXED` (fixed amount) | `OUTSTANDING_BALANCE` (transfer full balance)
- `Priority`: `HIGH | MEDIUM | LOW | URGENT`; `Status`: `ACTIVE → DISABLED → DELETED`
- `RecurrenceType`: `PERIODIC_RECURRENCE | AS_PER_DUES`; frequency/interval control execution schedule
- Amount stored as `NUMERIC(19,4)` → `BigDecimal`; validity date range enforced at execution time
- Package: `com.cba.social`; Flyway: `V17__hooks_holidays_campaigns.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/standinginstructions`, `POST /api/v1/standinginstructions/{id}?command=disable|enable`

### 36. Global Search Module
- Cross-entity search across clients, loans, savings accounts, and groups
- Uses `JdbcTemplate` ILIKE queries — deliberately avoids importing domain repositories to prevent circular coupling
- `resource` query param filters to one entity type: `CLIENTS | LOANS | SAVINGS | GROUPS`; omit for all
- Invalid resource values throw `CbaException.badRequest("INVALID_RESOURCE", ...)`
- Returns `SearchResult` record: `entityId`, `entityType`, `entityName`, `entityAccountNo`, `entityStatus`, `entityExternalId`
- Package: `com.cba.search`
- Endpoints: `GET /api/v1/search?query={q}[&resource=CLIENTS|LOANS|SAVINGS|GROUPS]`

### 37. Two-Factor Authentication Module
- OTP token generation and verification for platform users. 6-digit code with 10-minute expiry
- `DeliveryMethod`: `EMAIL | SMS`. Token stored in `two_factor_auth_tokens` table; single-use (marked `verified=true` on success)
- `generateToken()` uses `SecureRandom` for cryptographically secure codes
- `verifyToken()` rejects expired or already-verified tokens with distinct error codes
- Package: `com.cba.user`; Flyway: `V18__maker_checker_datatables.sql`
- Endpoints: `POST /api/v1/twofactor/generate`, `POST /api/v1/twofactor/verify`, `GET /api/v1/users/{userId}/twofactor`

### 38. Beneficiaries Module
- Third-party transfer beneficiaries; sub-resource of customers at `/api/v1/clients/{customerId}/beneficiaries`
- No `version` column in DB; soft-delete via `active=false` (list endpoint returns only `active=true`)
- `getBeneficiary()` validates ownership — returns 404 (not 403) when customerId doesn't match, preventing enumeration
- `transferLimit NUMERIC(19,4)` → `BigDecimal` — optional cap on single transfer to this beneficiary
- Package: `com.cba.customer`; Flyway: `V18__maker_checker_datatables.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/clients/{customerId}/beneficiaries`, `GET /api/v1/clients/{customerId}/beneficiaries/{id}`

### 39. Client Images Module
- Profile image management; one image per customer (`UNIQUE` constraint on `customer_id`)
- `StorageType` enum: `FILE_SYSTEM | S3 | DATABASE`. Stores metadata only — binary handled by external storage
- `saveImage()` performs an upsert: finds existing record or creates new; single `PUT` endpoint handles both cases
- No `version` column in DB table
- Package: `com.cba.customer`; Flyway: `V18__maker_checker_datatables.sql`
- Endpoints: `GET /api/v1/clients/{customerId}/images`, `PUT /api/v1/clients/{customerId}/images`, `DELETE /api/v1/clients/{customerId}/images`

### 40. Credit Bureau Module
- Credit bureau integration config and loan-product mappings. Supports multiple bureau adapters (TransUnion, Metropol, etc.)
- `CreditBureauIntegration`: `implClass` field stores the fully-qualified adapter class name; `country` ISO code
- `CreditBureauProductMapping`: UNIQUE constraint on `(loan_product_id, credit_bureau_id)`; `creditCheckMandatory boolean`
- Activate/deactivate without deleting; `findByCreditBureauId()` for listing mappings per bureau
- No `version` on either entity; cross-package loan product reference stored as UUID (no JPA join)
- Package: `com.cba.system`; Flyway: `V18__maker_checker_datatables.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/creditbureaus`, `POST /api/v1/creditbureaus/{id}?command=activate|deactivate`, `GET/POST/DELETE /api/v1/creditbureaus/{id}/mappings`

### 41. Surveys Module
- PPI / welfare survey engine (Mifos-compatible). 5-entity cascade chain: `Survey → SurveyQuestion → SurveyResponse`, `Survey → SurveyScorecard → SurveyScorecardScore`
- `Survey.key` is UNIQUE — used for programmatic lookup (`/api/v1/surveys/key/{key}`)
- Questions ordered by `sequenceNo ASC`; responses ordered similarly. All `CascadeType.ALL + orphanRemoval=true`
- Scorecards are immutable once submitted; `SurveyScorecardScore` stores `questionId` + `responseId` as UUIDs
- No `version` on any survey entity; no `updatedAt` on `SurveyQuestion` or `SurveyResponse`
- Package: `com.cba.system`; Flyway: `V18__maker_checker_datatables.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/surveys`, `GET /api/v1/surveys/key/{key}`, `GET/POST /api/v1/surveys/{id}/scorecards`

### 42. Accounting Rules Module
- Configurable debit/credit GL account mappings for journal entry types. Links a rule name to debit and credit `gl_accounts` UUIDs
- `allowMultipleDebits` / `allowMultipleCredits` flags control whether the posting engine can fan out to multiple accounts
- Package: `com.cba.accounting`; Flyway: `V19__accounting_rules_provisioning.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/accountingrules`

### 43. Provisioning Criteria Module
- Loan loss provisioning definitions (IFRS 9 / Basel II). Each criteria has named age-band categories with provision percentages
- Standard categories: `STANDARD` (0–30d, 1%), `WATCH` (31–90d, 5%), `SUB_STANDARD` (91–180d, 25%), `DOUBTFUL` (181–360d, 50%), `LOSS` (361+d, 100%)
- `ProvisioningCriteriaDefinition`: `provisionPercentage NUMERIC(5,2)` → `BigDecimal`; `liabilityAccountId` + `expenseAccountId` as UUID FK refs to `gl_accounts`
- `updateCriteria()` clears and re-adds all definitions atomically (replace-all pattern, same as `updatePermissions()`)
- No `version` on `ProvisioningCriteriaDefinition`; parent `ProvisioningCriteria` has `@Version`
- Package: `com.cba.accounting`; Flyway: `V19__accounting_rules_provisioning.sql`
- Endpoints: `GET/POST/PUT/DELETE /api/v1/provisioningcriteria`

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
| Design Component | Angular Component | Module | Status |
|-----------------|-------------------|--------|--------|
| Sidebar nav | `SidebarComponent` | `LayoutModule` | ✅ Built |
| Topbar | `TopbarComponent` | `LayoutModule` | ✅ Built |
| KPI card | `KpiCardComponent` | `SharedModule` | ✅ Built |
| Data table | `DataTableComponent` | `SharedModule` | ✅ Built |
| Status badge | `StatusBadgeComponent` | `SharedModule` | ✅ Built — inputs: `[label]` (string) + `[variant]` (success/warning/error/info/neutral/primary) — **never use `[status]`** |
| Dashboard | `DashboardComponent` | `OperationsModule` | ✅ Built — KPIs, transaction table, portfolio bars, KYC queue |
| Customers list | `CustomersListComponent` | `OperationsModule` | ✅ Built — debounced search, KYC filter tabs, pagination |
| Customer detail | `CustomerDetailComponent` | `OperationsModule` | ✅ Built — 5 tabs, KYC state machine |
| Accounts list | `AccountsListComponent` | `OperationsModule` | ✅ Built — type filter, pagination |
| Account detail | `AccountDetailComponent` | `OperationsModule` | ✅ Built — header card, overview/transactions tabs, freeze/unfreeze/close/deposit/withdraw modals |
| Payments list | `PaymentsListComponent` | `OperationsModule` | ✅ Built — account context picker, paginated payment history, 3-step transfer wizard modal, standing order modal |
| Payment detail | `PaymentDetailComponent` | `OperationsModule` | ✅ Built — status band with FX details, transfer route card, payment details card, reverse modal |
| Teller list | `TellerListComponent` | `OperationsModule` | ✅ Built — search + status filter, create teller modal |
| Teller detail | `TellerDetailComponent` | `OperationsModule` | ✅ Built — overview/cashiers/sessions tabs, session expand/collapse, cash-in/out/settle modals, lifecycle buttons |
| Loans list | `LoansListComponent` | `OperationsModule` | ✅ Built — pipeline view, sliding detail panel |
| Loan detail | `LoanDetailComponent` | `OperationsModule` | ✅ Built — 5 tabs, approve/disburse/repayment/reject |
| Loan products list | `LoanProductsListComponent` | `ProductsModule` | ✅ Built — search, active filter, pagination |
| Loan product detail | `LoanProductDetailComponent` | `ProductsModule` | ✅ Built — view/edit toggle, 5 section tabs, GL linkages, charges |
| Deposit products list | `DepositProductsListComponent` | `ProductsModule` | ✅ Built — search, type filter, pagination |
| Deposit product detail | `DepositProductDetailComponent` | `ProductsModule` | ✅ Built — view/edit toggle, 5 section tabs, overdraft config, GL linkages |
| Fixed deposit products list | `FixedDepositsListComponent` | `ProductsModule` | ✅ Built — search, active filter, pagination; term range column |
| Fixed deposit product detail | `FixedDepositDetailComponent` | `ProductsModule` | ✅ Built — view/edit toggle, 4 section tabs (core/rates/term/penalty) |
| Recurring deposit products list | `RecurringDepositsListComponent` | `ProductsModule` | ✅ Built — search, active filter, pagination; deposit frequency column |
| Recurring deposit product detail | `RecurringDepositDetailComponent` | `ProductsModule` | ✅ Built — view/edit toggle, 5 section tabs (core/rates/frequency/term/penalty) |
| Share products list | `SharesListComponent` | `ProductsModule` | ✅ Built — search, active filter, pagination; unit price + shares issued columns |
| Share product detail | `ShareDetailComponent` | `ProductsModule` | ✅ Built — view/edit toggle, 3 section tabs (core/shares/lockin); dividend policy toggle |
| GL accounts | `GlAccountsComponent` | `AccountingModule` | ✅ Built — type filter tabs, search, enable/disable, create/edit modal |
| Journal entries | `JournalEntriesComponent` | `AccountingModule` | ✅ Built — T-ledger grouped view, date filter, manual entry modal with balance validation, reversal |
| Provisioning criteria | `ProvisioningComponent` | `AccountingModule` | ✅ Built — IFRS 9 age bands, create/edit/delete, GL account dropdowns by type |
| Reports list | `ReportsListComponent` | `ReportsModule` | ✅ Built — search/category filter, dynamic param form, schema-on-read results table, CSV export, create/delete |
| CoB Scheduler | `CobSchedulerComponent` | `ReportsModule` | ✅ Built — job cards with stats, Run Now trigger, inline history panel, duration helper |
| Report Mailing Jobs | `ReportMailingComponent` | `ReportsModule` | ✅ Built — mailing job CRUD, RRULE schedule presets, output type chips, send-now trigger |
| Users | `UsersComponent` | `AdminModule` | ✅ Built — search filter, create modal (firstname/lastname/username/email/password/office/roles), enable/disable toggle, delete confirm |
| Roles | `RolesComponent` | `AdminModule` | ✅ Built — list with permission count badge, create/edit modal, permissions matrix modal grouped by grouping with select-all-in-group |
| Offices | `OfficesComponent` | `AdminModule` | ✅ Built — search, create/edit modal with parent office dropdown, hierarchy column |
| Hooks | `HooksComponent` | `AdminModule` | ✅ Built — WEB/SMS type chips, create/edit modal with event selection chips |
| Maker-Checker | `MakerCheckerComponent` | `AdminModule` | ✅ Built — status filter tabs (All/PENDING/APPROVED/REJECTED), metadata table, approve/reject for PENDING entries |
| TPP Management | `OpenBankingComponent` | `AdminModule` | ✅ Built — TPP registry: clientId, country, scope chips, cert expiry; register/activate/revoke |
| Groups list | `GroupsListComponent` | `GroupsModule` | ✅ Built — status filter + search, create modal, routerLink to detail |
| Group detail | `GroupDetailComponent` | `GroupsModule` | ✅ Built — 3 tabs: Members (add/remove), Collection Sheet (generate + table), GLIM Accounts (accordion) |
| Centers list | `CentersListComponent` | `GroupsModule` | ✅ Built — status filter + search, create modal, routerLink to detail |
| Center detail | `CenterDetailComponent` | `GroupsModule` | ✅ Built — 2 tabs: Groups (links), All Members |
| Consents list | `OpenBankingListComponent` | `OpenBankingModule` | ✅ Built — type filter tabs (All/AISP/PISP/CBPII) + status dropdown, type badges, scope chips |
| Consent detail | `ConsentDetailComponent` | `OpenBankingModule` | ✅ Built — status badge, conditional Authorise/Revoke button, two-column detail grid, confirm modal |
| Codes & Values | `CodesComponent` | `SystemModule` | ✅ Built — inline accordion expand, load-on-expand, inline add/edit value form, create code modal |
| Global Config | `GlobalConfigComponent` | `SystemModule` | ✅ Built — searchable table, inline row edit (type-aware: string/number/boolean), enabled toggle |
| Floating Rates | `FloatingRatesComponent` | `SystemModule` | ✅ Built — accordion with rate periods, create/edit modal with dynamic period rows, delete confirm |
| Taxes | `TaxesComponent` | `SystemModule` | ✅ Built — two tabs: Tax Components (CRUD) + Tax Groups (component bundles with effective dates) |

### Angular View/Edit Toggle Pattern
All detail pages (loan product, deposit product, customer, loan) share this pattern:
- `editMode: boolean` flag switches between view and edit template
- `form: CreateRequest` — a **separate object** from `product`; never two-way bound to displayed data
- `enterEditMode(blank=false)` deep-copies product → form OR populates blank defaults for `isNew`
- `cancelEdit()` discards the form object; displayed `product` is untouched
- `save()` calls create or update; only writes to `product` on success
- `isNew` detected from route param `'new'`; on save navigates to `../p.id`
- Deactivate uses a confirm modal (same pattern across all detail pages)

### Angular Search Pattern
All list pages use client-side debounced search:
```typescript
private readonly search$ = new Subject<string>();
// In ngOnInit:
this.search$.pipe(debounceTime(250), distinctUntilChanged(), takeUntil(this.destroy$))
  .subscribe(q => this.applyFilter(q));
```
Products lists are not server-paginated (small datasets); Customers list uses `switchMap` for server-side search.

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

### API Documentation & Postman Collections (Session 5)

| File | Contents |
|------|----------|
| `docs/api-reference.html` | Full API reference (Mifos apiLive.htm-style) — standalone HTML, also served via GitHub Pages |
| `docs/cba-postman-collection-v2.json` | Enriched Postman collection — all implemented endpoints, Mifos-style divisions, 8-language code samples |
| `docs/cba-postman-collection-coming-soon.json` | Coming Soon Postman collection — planned/unimplemented endpoints based on Mifos patterns |

**GitHub Pages URL**: `https://razormvp.github.io/cba-platform/` (deployed automatically on push to `main` when `docs/**` changes)

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
