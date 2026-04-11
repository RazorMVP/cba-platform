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

## Card Management Service and Front End Processing Module

This section is the authoritative reference for the Card Management System (CMS) and Front End Processor (FEP) — a full production-grade card processing stack added to the CBA platform. Read this section fully before generating any code related to cards, ATM, POS, or ISO 8583.

---

### Architecture Overview

```
[Mobile / POS / ATM Terminal]
           ↓  ISO 8583-1987 over TCP (port 8583)
    [fep-service :8082]
     │  ISO 8583 TCP socket server (Netty + jPOS)
     │  Message parsing, routing, HSM adapter
     │  EMV ARQC validation, PIN block decryption
           ↓  REST (internal)
    [card-service :8081]
     │  Card lifecycle, auth rules, fraud engine
     │  Token vault (TSP), settlement, disputes
     │  Terminal simulator REST API
           ↓  REST (internal)
    [backend (monolith) :8080]
         Account balance queries
         Transaction posting on approval
         Loan credit limit queries (credit cards)
```

**Deployment model (Hybrid — Option C):**
- `fep-service` — standalone Spring Boot microservice; must be network-isolated; owns the TCP socket
- `card-service` — standalone Spring Boot microservice; owns all card domain data
- `backend` (existing monolith) — called by `card-service` via REST for balance/transaction operations

---

### New Services

| Service | Port (HTTP) | Port (TCP) | Responsibility |
|---------|-------------|------------|---------------|
| `card-service` | 8081 | — | Card lifecycle, auth rules, fraud engine, token vault, terminal simulator REST, disputes, settlement |
| `fep-service` | 8082 | 8583 | ISO 8583-1987 TCP socket server, message parsing, HSM adapter, auth routing, EMV cryptogram validation |

---

### card-service — Module Breakdown

| Package | Responsibility |
|---------|---------------|
| `com.cba.card` | Card entity, BIN ranges, card products, physical/virtual lifecycle, PIN management |
| `com.cba.card.limits` | Per-card limits: daily purchase, daily withdrawal, per-transaction, monthly caps |
| `com.cba.card.fraud` | Rule engine, risk scoring (0–100), configurable rule weights, STEP_UP threshold |
| `com.cba.card.token` | Token vault — DPAN → PAN mapping, token lifecycle (simulated EMVCo TSP) |
| `com.cba.card.settlement` | Dual-message batch (`0320`/`0322`/`0324`) + single-message real-time advice (`0120`) |
| `com.cba.card.dispute` | Dispute state machine: `RAISED → UNDER_REVIEW → RESOLVED_ISSUER / RESOLVED_ACQUIRER` |
| `com.cba.card.terminal` | Terminal simulator REST API — constructs ISO 8583 messages, fires to FEP TCP, returns response |

---

### fep-service — Module Breakdown

| Package | Responsibility |
|---------|---------------|
| `com.cba.fep.server` | Netty TCP socket server on port 8583, connection lifecycle, session management |
| `com.cba.fep.iso` | ISO 8583-1987 message packager/unpackager (jPOS `GenericPackager`), bitmap processing, all DE field definitions |
| `com.cba.fep.router` | Routes MTI `0100/0200/0400/0800` to correct handler; response correlation via STAN (DE11) |
| `com.cba.fep.auth` | Authorization handler — calls `card-service` auth endpoint, maps response code, builds `0110` reply |
| `com.cba.fep.hsm` | Thales payShield pluggable adapter interface; software stub implementation for dev; real hardware slot for prod |
| `com.cba.fep.emv` | DE 55 ICC data parser, ARQC cryptogram validation, ARPC generation |

---

### ISO 8583-1987 Message Types Implemented

| MTI | Direction | Purpose |
|-----|-----------|---------|
| `0100` | Terminal → FEP | Authorization Request (purchase, balance enquiry) |
| `0110` | FEP → Terminal | Authorization Response |
| `0120` | FEP → card-service | Financial Advice (single-message real-time settlement) |
| `0130` | card-service → FEP | Financial Advice Response |
| `0200` | Terminal → FEP | Financial Transaction Request (cash withdrawal) |
| `0210` | FEP → Terminal | Financial Transaction Response |
| `0220` | FEP → card-service | Financial Transaction Advice |
| `0320` | FEP → settlement | Batch Upload Request |
| `0322` | FEP → settlement | Batch Upload Advice |
| `0324` | FEP → settlement | Batch Close Request |
| `0400` | Terminal → FEP | Reversal Request |
| `0410` | FEP → Terminal | Reversal Response |
| `0420` | FEP internal | Reversal Advice |
| `0800` | Terminal ↔ FEP | Network Management (sign-on `0001`, sign-off `0002`, echo `0301`) |
| `0810` | FEP → Terminal | Network Management Response |

---

### Key ISO 8583 Data Elements (DE) Handled

| DE | Name | Notes |
|----|------|-------|
| DE 2 | Primary Account Number (PAN) | Stored encrypted; de-tokenized if DPAN detected |
| DE 3 | Processing Code | `000000`=purchase, `010000`=cash withdrawal, `310000`=balance enquiry |
| DE 4 | Transaction Amount | `NUMERIC(12)` in cents |
| DE 7 | Transmission Date & Time | `MMDDHHmmss` |
| DE 11 | STAN | Systems Trace Audit Number — unique per transaction, used for correlation |
| DE 12 | Local Transaction Time | `HHmmss` |
| DE 13 | Local Transaction Date | `MMDD` |
| DE 14 | Expiry Date | `YYMM` |
| DE 18 | Merchant Category Code (MCC) | Used by fraud rule engine |
| DE 22 | POS Entry Mode | `021`=mag stripe, `051`=EMV chip contact, `071`=contactless NFC |
| DE 23 | Card Sequence Number | For multi-card accounts |
| DE 35 | Track 2 Data | Mag stripe: PAN + expiry + service code |
| DE 37 | Retrieval Reference Number (RRN) | 12-char unique ref from acquirer |
| DE 38 | Authorization Code | 6-char code on approval; blank on decline |
| DE 39 | Response Code | `00`=approved, `05`=do not honor, `51`=insufficient funds, `54`=expired card, `57`=txn not permitted, `62`=restricted, `91`=issuer unavailable |
| DE 41 | Terminal ID | 8-char ATM/POS terminal identifier |
| DE 42 | Merchant ID | 15-char acquirer merchant ID |
| DE 43 | Merchant Name/Location | 40-char free text |
| DE 49 | Transaction Currency Code | ISO 4217 numeric (840=USD, 404=KES, 288=GHS) |
| DE 52 | PIN Block | Encrypted PIN block (ISO-0 format); sent to HSM adapter for PVV/IBM3624 verification |
| DE 54 | Additional Amounts | Available balance returned on approval |
| DE 55 | ICC Data (EMV) | TLV-encoded EMV tags including ARQC (tag `9F26`); FEP validates and generates ARPC |
| DE 90 | Original Data Elements | For reversals: original MTI + STAN + date + acquirer/forwarding ID |

---

### HSM Adapter — Thales payShield Command Set (Pluggable)

The HSM adapter is a Java interface (`HsmAdapter`) with two implementations:
- `SoftwareHsmAdapter` — dev/test; performs cryptographic ops in software (AES-256, TDES)
- `ThalesPayShieldAdapter` — production; sends TCP command frames to a real payShield 9000/10000

| Command | Purpose |
|---------|---------|
| `CW` | Generate/verify CVV and CVV2 using card key |
| `DC` | Verify PIN using Visa PVV method |
| `CA` | Verify PIN using IBM 3624 offset method |
| `BK` | Translate PIN from one ZPK to another (for cross-network key zones) |
| `NC` | Generate MAC using ISO 9797 Algorithm 3 (for message authentication) |
| `KQ` | Generate Zone PIN Key (ZPK) or Zone Master Key (ZMK) |
| `A2` | Generate PIN offset (for PIN mailer) |
| `EC` | Generate key check value (KCV) |

All HSM commands are request-response over a single TCP connection (keep-alive) with 2-byte length header framing.

---

### Card Types and Linked Entities

| Card Type | Links To | Authorization Check | Credit Line |
|-----------|----------|---------------------|-------------|
| **Debit** | `Account` (savings/checking) | Real-time balance check via monolith REST | — |
| **Prepaid** | `PrepaidWallet` (card-service) | Wallet balance check (internal to card-service) | — |
| **Credit** | `Loan` (revolving credit line in monolith) | Available credit = `creditLimit - outstandingBalance` | Revolving |

---

### Card Lifecycle State Machines

**Virtual Card:**
```
ISSUED → ACTIVE → BLOCKED ↔ ACTIVE → EXPIRED
                  ↓
               CANCELLED
```

**Physical Card:**
```
ORDERED → PRODUCED → DISPATCHED → ACTIVATION_PENDING → ACTIVE → BLOCKED ↔ ACTIVE → EXPIRED
                                                                    ↓
                                                                 CANCELLED
```

Physical card commands: `?command=activate | block | unblock | cancel | replace`

PIN lifecycle: `PIN_NOT_SET → PIN_SET`; PIN change always goes through HSM (`CA`/`DC` verify old PIN before `A2` sets new one).

---

### Fraud Engine — Rule-Based + Risk Scoring

Each rule has a configurable weight (0–100). Score thresholds are also configurable via Global Configuration.

| Rule ID | Rule Description | Default Weight |
|---------|-----------------|---------------|
| `VELOCITY_LIMIT` | More than N transactions in Y minutes on same card | 40 |
| `SINGLE_AMOUNT_LIMIT` | Single transaction amount exceeds card daily limit | 35 |
| `BLOCKED_COUNTRY` | Transaction originates from a blocked country code | 60 |
| `BLOCKED_MCC` | Merchant Category Code is on the blocked list | 45 |
| `DUPLICATE_TRANSACTION` | Same amount + merchant within 2 minutes | 50 |
| `CNP_DEBIT` | Card-not-present transaction on a debit card | 25 |
| `OUTSIDE_HOURS` | Transaction outside permitted hours for card product | 20 |
| `CARD_EXPIRED` | Card expiry date (DE14) has passed | 100 (hard block) |
| `CARD_BLOCKED` | Card status is BLOCKED or CANCELLED | 100 (hard block) |
| `PIN_RETRY_EXCEEDED` | PIN retry counter ≥ 3 | 100 (hard block) |

**Score thresholds (configurable via `GlobalConfiguration`):**

| Score Range | Decision | ISO 8583 DE39 |
|-------------|----------|--------------|
| 0–29 | `APPROVE` | `00` |
| 30–69 | `STEP_UP` — require PIN even if contactless (force online PIN) | `0110` with PIN required flag |
| 70–100 | `DECLINE` | `05` do not honor / `62` restricted |

---

### Settlement Modes

**Single-Message (Real-Time Advice) — low-value / contactless:**
- Used when transaction amount ≤ configurable tap limit (default: currency equivalent of $25)
- Flow: `0200` Financial Request → immediate `0220` Financial Advice → funds debited in real-time
- No batch window; transaction is final on `0220` acceptance

**Dual-Message (Batch) — high-value / chip / mag stripe:**
- Authorisation: `0100` → `0110` (hold placed on funds — debit pending)
- Clearing: end-of-day `0320` Batch Upload → `0322` Batch Advice → `0324` Batch Close
- Settlement: `SettlementService` matches auth records, posts final GL debit/credit entries
- Unmatched authorizations expire after 7 days and are reversed automatically

---

### Tokenization (Simulated TSP — Internal Token Vault)

- `POST /api/v1/tokens` — generates a DPAN (Device PAN) mapped to a real PAN; returns `{ dpan, expiryDate, tokenRef }`
- DPAN format: same length as PAN (16 digits), different BIN range (token BIN: `9999xx`)
- Token vault: `token_vault` table in card-service DB — stores `dpan → pan` mapping encrypted at rest
- FEP de-tokenization: when DE2 PAN starts with token BIN prefix, FEP calls card-service `/tokens/detokenize` before auth lookup
- Token lifecycle: `ACTIVE → SUSPENDED → DELETED`; suspend on card block, delete on card cancel

---

### Terminal Simulator

**REST API** (embedded in card-service at `/api/v1/simulate/`):

| Endpoint | MTI Sent | Description |
|----------|----------|-------------|
| `POST /simulate/purchase` | `0100` | Card purchase at POS |
| `POST /simulate/withdrawal` | `0200` | ATM cash withdrawal |
| `POST /simulate/balance` | `0100` (DE3=`310000`) | Balance enquiry |
| `POST /simulate/reversal` | `0400` | Transaction reversal |
| `POST /simulate/network/signon` | `0800` (DE70=`0001`) | FEP sign-on |
| `POST /simulate/network/echo` | `0800` (DE70=`0301`) | Echo test |

Request body includes: `cardNumber`, `expiryDate`, `amount`, `currency`, `terminalId`, `merchantId`, `merchantName`, `entryMode` (`SWIPE`/`CHIP`/`CONTACTLESS`), `pinBlock` (optional).

Response returns full decoded ISO 8583 response: `responseCode`, `authCode`, `availableBalance`, `stan`, `rrn`, plus raw hex dump of the ISO 8583 message for debugging.

**Angular UI** (`TerminalSimulatorComponent`):
- Card picker (search by PAN last 4 or customer name)
- Transaction type selector (Purchase / Withdrawal / Balance / Reversal)
- Amount + currency input
- Entry mode toggle (Swipe / Chip / Contactless)
- "Send Transaction" button → calls `/simulate/*` → displays real response with colour-coded result (green=approved, red=declined)
- Raw ISO 8583 hex dump panel (collapsible) for technical inspection

---

### Disputes Module

**State machine:** `RAISED → UNDER_REVIEW → RESOLVED_ISSUER | RESOLVED_ACQUIRER | WITHDRAWN`

| Field | Type | Notes |
|-------|------|-------|
| `transactionRef` | String | Original RRN (DE37) of disputed transaction |
| `disputeReason` | Enum | `UNAUTHORIZED`, `GOODS_NOT_RECEIVED`, `DUPLICATE`, `AMOUNT_MISMATCH`, `OTHER` |
| `status` | Enum | State machine above |
| `raisedBy` | UUID | Customer ID |
| `resolvedBy` | UUID | Operations staff user ID |
| `resolutionNotes` | Text | Free text |
| `originalAmount` | NUMERIC(19,4) | Amount of disputed transaction |

Endpoints: `GET/POST /api/v1/cards/disputes`, `GET/PUT /api/v1/cards/disputes/{id}`

---

### Database Entities (card-service DB — separate schema `card_db`)

| Table | Key Columns |
|-------|-------------|
| `card_products` | id, name, card_type (DEBIT/PREPAID/CREDIT), bin_range_start, bin_range_end, default_daily_limit, features (JSONB) |
| `cards` | id, pan_encrypted, expiry_date, cvv_encrypted, card_sequence_no, card_type, status, virtual_flag, customer_id, linked_entity_id (account or loan UUID), product_id, pin_retry_count |
| `physical_card_orders` | id, card_id, status, production_request_date, dispatch_date, activation_code, card_bureau_ref |
| `card_limits` | id, card_id, daily_purchase_limit, daily_withdrawal_limit, per_txn_limit, monthly_limit, currency_code |
| `authorization_log` | id, card_id, stan, rrn, mti, processing_code, amount, currency_code, response_code, entry_mode, merchant_id, merchant_name, mcc, fraud_score, decision, created_at |
| `fraud_rules` | id, rule_id, weight, enabled, params (JSONB) — e.g. velocity_count, velocity_window_minutes |
| `fraud_score_log` | id, authorization_log_id, rule_id, score_contribution, triggered |
| `token_vault` | id, dpan_encrypted, pan_encrypted, token_ref, status, customer_id, card_id, created_at, expires_at |
| `settlement_batches` | id, batch_ref, status, settlement_date, total_amount, item_count, opened_at, closed_at |
| `settlement_items` | id, batch_id, authorization_log_id, amount, currency_code, status |
| `card_disputes` | id, card_id, transaction_ref, dispute_reason, status, raised_by, resolved_by, original_amount, resolution_notes |
| `prepaid_wallets` | id, card_id, customer_id, balance, currency_code, status |

All tables: UUID PKs, `version BIGINT` for optimistic locking, `created_at`/`updated_at` TIMESTAMPTZ.

---

### Angular Screens (new `CardsModule`)

| Component | Route | Auth Required | Status |
|-----------|-------|--------------|--------|
| `CardListComponent` | `/cards` | ADMIN/TELLER | 🔲 Planned |
| `CardDetailComponent` | `/cards/:id` | ADMIN/TELLER | 🔲 Planned |
| `CardProductsComponent` | `/cards/products` | ADMIN | 🔲 Planned |
| `FraudRulesComponent` | `/cards/fraud` | ADMIN | 🔲 Planned |
| `SettlementComponent` | `/cards/settlement` | ADMIN | 🔲 Planned |
| `DisputesComponent` | `/cards/disputes` | ADMIN/TELLER | 🔲 Planned |
| `TerminalSimulatorComponent` | `/cards/terminal` | ADMIN/TELLER | 🔲 Planned |
| `ApiKeysComponent` | `/cards/api-keys` | ADMIN | 🔲 Planned |
| `WebhooksComponent` | `/cards/webhooks` | ADMIN | 🔲 Planned |

---

### Monorepo Structure — New Additions

```
CoreBanking/
├── backend/                    ← existing monolith (REST client added for card-service)
├── card-service/               ← NEW standalone Spring Boot microservice
│   ├── pom.xml
│   ├── src/main/java/com/cba/card/
│   │   ├── CardApplication.java
│   │   ├── card/               ← Card entity, lifecycle, PIN
│   │   ├── limits/             ← CardLimit entity + service
│   │   ├── fraud/              ← FraudRule, FraudEngine, FraudScoreService
│   │   ├── token/              ← Token vault, DPAN, TSP simulation
│   │   ├── settlement/         ← SettlementBatch, SettlementService
│   │   ├── dispute/            ← Dispute entity + service
│   │   └── terminal/           ← TerminalSimulatorController + ISO8583Client
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/
│           ├── V1__card_schema.sql
│           └── V2__card_demo_data.sql
├── fep-service/                ← NEW standalone Spring Boot microservice
│   ├── pom.xml
│   ├── src/main/java/com/cba/fep/
│   │   ├── FepApplication.java
│   │   ├── server/             ← Netty TCP server (port 8583)
│   │   ├── iso/                ← jPOS packager, field definitions, bitmap
│   │   ├── router/             ← MTI-based message router
│   │   ├── auth/               ← Authorization handler
│   │   ├── hsm/                ← HsmAdapter interface + SoftwareHsmAdapter + ThalesPayShieldAdapter stub
│   │   └── emv/                ← DE55 parser, ARQC validator, ARPC generator
│   └── src/main/resources/
│       ├── application.yml
│       └── iso8583-1987-fields.xml   ← jPOS GenericPackager field config
├── web/                        ← existing Angular app (new CardsModule added)
│   └── src/app/features/cards/ ← NEW Angular feature module
└── infrastructure/
    └── docker-compose.yml      ← Updated: card-service + fep-service added
```

---

### Key Dependencies — card-service

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-data-jpa` | JPA / Hibernate |
| `spring-boot-starter-security` | JWT auth (shared Keycloak realm) |
| `spring-boot-starter-validation` | Bean validation |
| `flyway-core` | DB migrations |
| `postgresql` | JDBC driver |
| `lombok` | Boilerplate reduction |
| `jasypt-spring-boot-starter` | Field-level PAN/CVV encryption |
| `netty-all` | ISO 8583 TCP client (for terminal simulator calls to FEP) |

---

### Key Dependencies — fep-service

| Dependency | Purpose |
|------------|---------|
| `jpos` `2.1.x` | ISO 8583-1987 message packing/unpacking, `GenericPackager`, `ISOMsg` |
| `netty-all` `4.x` | High-performance TCP socket server (port 8583) |
| `spring-boot-starter-web` | Internal REST API (health, config reload) |
| `spring-boot-starter-data-jpa` | Auth log persistence |
| `lombok` | Boilerplate reduction |
| `bc-fips` or `bcprov-jdk18on` | Bouncy Castle — TDES PIN block decryption, AES card key ops |

---

### Open Banking Extension for Card Services

The card platform exposes two Open Banking layers. Read this section in full before generating any card-related Open Banking code.

---

#### Layer 1 — Existing Open Banking Module Extension (backend monolith)

Card accounts surface inside the existing `/open-banking/v3.1/` endpoints. The `backend` module adds a `CardServiceClient` REST client to fetch card data from `card-service` and maps it to UK Open Banking v3.1 response shapes.

| Existing Endpoint | Extension |
|-------------------|-----------|
| `GET /open-banking/v3.1/accounts` | Card accounts returned with `accountType: CARD`, `accountSubType: DEBIT_CARD / CREDIT_CARD / PREPAID_CARD` |
| `GET /open-banking/v3.1/accounts/{id}/balances` | `availableBalance` (debit/prepaid = wallet balance; credit = `creditLimit - outstanding`) |
| `GET /open-banking/v3.1/accounts/{id}/transactions` | Authorization history mapped to OB transaction objects |
| `POST /open-banking/v3.1/funds-confirmations` | Works against card available balance (CBPII) |

Consent scope additions: `CARD_READ`, `CARD_TRANSACTIONS_READ`, `CARD_BALANCES_READ` — added to `ConsentScope` enum in the existing Open Banking module.

---

#### Layer 2 — Dedicated Card API (card-service at `/card-api/v1/`)

A full BaaS-grade card API hosted in `card-service`. Third parties integrate via API keys (M2M) or FAPI 2.0 consent (customer-facing). All endpoints return the standard CBA response envelope `{ data, meta, errors }`.

##### Authentication — Dual-Mode

| Operation Type | Auth Method | Header |
|---------------|-------------|--------|
| Customer-facing (controls, own card data) | FAPI 2.0 consent (existing Keycloak flow) | `Authorization: Bearer {jwt}` |
| Platform-level M2M (issuance, webhooks, analytics, bulk) | API Key | `Authorization: ApiKey {key}` |

API keys are stored hashed (`PBKDF2WithHmacSHA256`) in the `api_keys` table. Key value shown only once on creation.

##### API Key Management (ADMIN only)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/card-api/v1/api-keys` | `POST` | Issue new API key — returns plaintext key once |
| `/card-api/v1/api-keys` | `GET` | List active keys (hashed — value not retrievable) |
| `/card-api/v1/api-keys/{id}` | `DELETE` | Revoke key immediately |

##### Card Issuance (API Key — BaaS)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/card-api/v1/cards` | `POST` | Issue virtual or physical card for a customer against an `accountId` or `loanId` |
| `/card-api/v1/cards` | `GET` | List cards; filter by `customerId`, `type`, `status` |
| `/card-api/v1/cards/{id}` | `GET` | Full card details |

##### Card Controls (FAPI 2.0 Consent — customer must authorise)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/card-api/v1/cards/{id}/controls` | `PUT` | Freeze/unfreeze; enable/disable contactless, CNP, international transactions |
| `/card-api/v1/cards/{id}/limits` | `PUT` | Update daily purchase, daily withdrawal, per-transaction, monthly limits |
| `/card-api/v1/cards/{id}/pin/change` | `POST` | PIN change — routed through HSM adapter (`CA`/`DC` verify old PIN, `A2` set new) |

##### Transaction & Authorization History

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/card-api/v1/cards/{id}/authorizations` | `GET` | API Key or consent | Full authorization log with fraud scores, entry mode, response codes |
| `/card-api/v1/cards/{id}/transactions` | `GET` | API Key or consent | Settled transactions only (cleared and posted) |

##### Spending Analytics (API Key)

| Endpoint | Description |
|----------|-------------|
| `GET /card-api/v1/cards/{id}/analytics/by-category` | Spend by MCC category (Dining, Travel, Retail, etc.) aggregated from `authorization_log` |
| `GET /card-api/v1/cards/{id}/analytics/by-merchant` | Top merchants by total amount and transaction frequency |
| `GET /card-api/v1/analytics/summary` | Monthly spend totals, approved vs. declined ratio, average transaction value |

All analytics endpoints accept `?from=YYYY-MM-DD&to=YYYY-MM-DD&currency=ISO4217` query params.
MCC → human-readable category mapping is a static lookup table seeded at startup.

##### Webhook Management (API Key)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/card-api/v1/webhooks` | `POST` | Register webhook: `{ callbackUrl, events[], secret }` |
| `/card-api/v1/webhooks` | `GET` | List registered webhooks |
| `/card-api/v1/webhooks/{id}` | `DELETE` | Deregister |
| `/card-api/v1/webhooks/{id}/deliveries` | `GET` | Delivery log for a webhook (last 100 attempts) |

**Delivery mechanism:**
- Spring `@Async` WebClient POST to `callbackUrl`
- Payload signed: `X-CBA-Signature: sha256={HMAC-SHA256(payload, secret)}`
- `X-CBA-Event` header contains event type (e.g. `AUTHORIZATION.APPROVED`)
- `X-CBA-Delivery` header contains a unique delivery UUID for idempotency
- Exponential backoff on failure: retry at 15s → 60s → 5m → 30m → 2h (5 attempts max)
- Final failure recorded as `FAILED` in `webhook_delivery_log`; no further retries

**Webhook Event Catalogue:**

| Category | Event | Trigger |
|----------|-------|---------|
| Authorization | `AUTHORIZATION.APPROVED` | Auth request approved by fraud engine + balance check |
| Authorization | `AUTHORIZATION.DECLINED` | Auth declined (insufficient funds, fraud, blocked card) |
| Authorization | `AUTHORIZATION.REVERSED` | `0400` reversal processed |
| Card Lifecycle | `CARD.ISSUED` | New card created (virtual or physical order placed) |
| Card Lifecycle | `CARD.ACTIVATED` | Physical card activated or virtual card moved to ACTIVE |
| Card Lifecycle | `CARD.BLOCKED` | Card status set to BLOCKED |
| Card Lifecycle | `CARD.UNBLOCKED` | Card unblocked, returned to ACTIVE |
| Card Lifecycle | `CARD.EXPIRED` | Card reached expiry date (nightly CoB job) |
| Card Lifecycle | `CARD.PIN_CHANGED` | PIN successfully changed via HSM |
| Card Lifecycle | `CARD.LIMIT_CHANGED` | Any card limit updated |
| Fraud | `FRAUD.RULE_TRIGGERED` | Any fraud rule fired (score > 0), with rule ID and weight |
| Fraud | `FRAUD.CARD_STEP_UP` | Score in 30–69 range — contactless step-up to online PIN |
| Fraud | `FRAUD.CARD_DECLINED_HIGH_RISK` | Score ≥ 70 — transaction declined by fraud engine |
| Dispute | `DISPUTE.RAISED` | Customer raised a new dispute |
| Dispute | `DISPUTE.RESOLVED` | Dispute resolved (RESOLVED_ISSUER or RESOLVED_ACQUIRER) |

---

#### New Database Tables (card-service — additions for Open Banking)

| Table | Key Columns |
|-------|-------------|
| `api_keys` | id, name, key_hash, created_by (user UUID), active, scopes (JSONB), last_used_at, created_at |
| `webhooks` | id, name, callback_url, events (JSONB string array), secret_hash, active, created_by, created_at |
| `webhook_delivery_log` | id, webhook_id, event_type, delivery_uuid, payload (JSONB), http_status, status (`PENDING`/`DELIVERED`/`FAILED`), attempt_count, last_attempt_at, next_retry_at |

---

#### Package Structure (card-service additions)

| Package | Contents |
|---------|----------|
| `com.cba.card.openbanking` | `CardApiController` — all `/card-api/v1/` endpoints |
| `com.cba.card.openbanking.apikey` | `ApiKey` entity, `ApiKeyService`, key hashing, request filter |
| `com.cba.card.openbanking.webhook` | `Webhook` entity, `WebhookService`, `WebhookDeliveryService` (async), delivery log |
| `com.cba.card.openbanking.analytics` | `SpendingAnalyticsService` — MCC aggregation, merchant roll-up, monthly summary |

In `backend` (existing monolith):
| Package | Contents |
|---------|----------|
| `com.cba.openbanking.card` | `CardAccountAdapter` — calls `CardServiceClient`, maps to OB account/balance/transaction DTOs |
| `com.cba.openbanking` (existing) | `ConsentScope` enum extended with `CARD_READ`, `CARD_TRANSACTIONS_READ`, `CARD_BALANCES_READ` |

---

### Multi-Scheme Support (Visa, Mastercard, Verve, Afrigo, UnionPay + Future Schemes)

This section defines the three additional modules required to make the card platform fully scheme-ready. Without these, the platform operates as a closed-loop proprietary card system only. With these, a bank can integrate with any ISO 8583-based card scheme — present and future.

American Express is explicitly **out of scope** — it does not use ISO 8583 and requires a proprietary protocol adapter not compatible with this architecture.

---

#### Gap Analysis — Current Architecture vs. Scheme Requirements

| Requirement | Visa | Mastercard | Verve | Afrigo | UnionPay | Our Status |
|-------------|------|------------|-------|--------|----------|------------|
| ISO 8583-1987 core | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — fep-service jPOS GenericPackager, all MTIs (Sessions 27–28) |
| EMV chip + contactless | ✅ | ✅ | ✅ | ✅ | ✅ QPBOC + SM4 | ✅ Covered — ArqcValidator + ArpcGenerator + DE55 parser; SM4 for domestic UnionPay (Session 33) |
| HSM PIN verification | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — HsmAdapter interface + SoftwareHsmAdapter (dev) + ThalesPayShieldAdapter stub (Session 27) |
| Card lifecycle management | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — CardService state machine; physical card ORDERED→PRODUCED→DISPATCHED→ACTIVE (Session 28 + Step 7) |
| Fraud engine | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — FraudEngine 10 rules, per-currency thresholds, configurable weights (Session 28 + 32) |
| BIN management + routing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — com.cba.card.bin: BinRange, BinService range-scan, 6/8-digit BIN, BinController, demo data (Session 29) |
| Scheme adapter (private DEs) | DE 60–63, 126 | DE 48 PDS, 111–127 | DE 62–63 | Minimal | DE 60–63 CUP | ✅ Covered — com.cba.fep.scheme: 5 adapters + 5 per-scheme jPOS packager XMLs (Session 27) |
| Scheme settlement file format | BASE II | IPM / GCMS | NIBSS e-settlement | PAPSS | CUPS / CNAPS | ⚠️ Partial — internal settlement batch lifecycle built (SettlementBatch/SettlementService); scheme clearinghouse file formats (BASE II, IPM, NIBSS, PAPSS, CUPS) not built |
| Interchange management | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — com.cba.card.interchange: rate tables per scheme, InterchangeQualificationEngine, settlement netting (Session 30) |
| 3D Secure / ACS (CNP) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — com.cba.card.threeds: ACS, frictionless/challenge flow, CAVV, OTP, per-currency frictionless limits (Session 31) |
| Card personalization bureau | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ Covered — com.cba.card.bureau: CDP generation, bureau job lifecycle, ORDERED→PRODUCED→DISPATCHED (Session 34 / Step 7) |
| Full chargeback workflow | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ Basic only — basic dispute state machine (RAISED→RESOLVED) built; scheme-specific reason codes, retrieval requests, representment, time-bound escalation pending (Step 8) |

---

#### Module A — BIN Management Module

**Purpose:** Every card transaction must be routed to the correct scheme based on the card's BIN (Bank Identification Number — first 6 to 8 digits of PAN). Without this module the FEP cannot identify which scheme adapter to invoke.

**Package:** `com.cba.card.bin`

**Key entities:**

| Table | Key Columns |
|-------|-------------|
| `bin_ranges` | id, bin_start (VARCHAR 8), bin_end (VARCHAR 8), scheme (`VISA`/`MASTERCARD`/`VERVE`/`AFRIGO`/`UNION_PAY`), product_type, card_type, country_code (ISO 3166), currency_code (ISO 4217), active |

**Rules:**
- Supports both 6-digit (legacy) and 8-digit BINs (EMV 2019 mandate — all schemes migrating to 8 digits)
- BIN lookup uses range scan: `WHERE bin_start <= :pan8 AND bin_end >= :pan8 AND active = true`
- BIN registration per scheme mirrors what banks receive from their scheme membership agreements
- `BinService.lookupScheme(pan)` — called by FEP immediately after ISO 8583 message is parsed; result determines which `SchemeAdapter` is selected

**Endpoints** (ADMIN only):
- `GET/POST /api/v1/bins` — list and register BIN ranges
- `GET/PUT/DELETE /api/v1/bins/{id}` — manage individual ranges
- `GET /api/v1/bins/lookup?pan={first8}` — test BIN lookup (dev/ops tool)

**Flyway:** `V3__bin_management.sql` in card-service

---

#### Module B — Scheme Adapter Framework

**Purpose:** A pluggable adapter pattern in the FEP that handles all scheme-specific message variations. Each scheme has private data elements, proprietary message structures, and unique settlement file formats. The adapter isolates all scheme-specific logic so the core FEP remains scheme-agnostic. Adding a new scheme requires only a new adapter implementation — no FEP core changes.

**Package:** `com.cba.fep.scheme`

**Interface:**

```java
public interface SchemeAdapter {
    SchemeType getScheme();
    ISOPackager getPackager();                          // scheme-specific jPOS packager
    void validateRequest(ISOMsg msg) throws SchemeValidationException;
    void enrichRequest(ISOMsg msg, CardContext ctx);    // add scheme private DEs
    void enrichResponse(ISOMsg request, ISOMsg response, AuthResult result);
    SettlementRecord buildSettlementRecord(AuthorizationLog log); // scheme settlement format
    String getNetworkId();                              // scheme network identifier
}
```

**Implementations:**

| Adapter Class | Scheme | Private DEs Handled | Settlement Format |
|---------------|--------|--------------------|--------------------|
| `VisaSchemeAdapter` | Visa | DE 60–63 (Visa-specific subelements), DE 126 | BASE II record format |
| `MastercardSchemeAdapter` | Mastercard | DE 48 PDS (Private Data Subelements), DE 111–127 | IPM (ISO 8583 + private DEs) |
| `VerveSchemeAdapter` | Verve (Interswitch) | DE 62–63 (Interswitch subelements) | NIBSS e-settlement format |
| `AfrigoSchemeAdapter` | Afrigo (PAPSS) | Minimal — largely standard ISO 8583 | PAPSS clearing format |
| `UnionPaySchemeAdapter` | China UnionPay | DE 60–63 (CUP subelements); QPBOC contactless profile | CUPS / CNAPS format |

**jPOS Packager Configs** (one XML per scheme in `fep-service/src/main/resources/`):
- `iso8583-visa.xml` — Visa field definitions including private DEs 60–63
- `iso8583-mastercard.xml` — MC field definitions including DE 48 PDS structure
- `iso8583-verve.xml` — Verve/Interswitch field definitions
- `iso8583-afrigo.xml` — Afrigo/PAPSS field definitions (closest to base standard)
- `iso8583-unionpay.xml` — CUP field definitions including QPBOC-specific tags

**SchemeAdapterFactory:**
- Called after BIN lookup: `factory.getAdapter(schemeType)` → returns correct `SchemeAdapter`
- Adapters registered as Spring beans; new schemes added by implementing `SchemeAdapter` and registering a BIN range

**Future-proofing:** Any new scheme (RuPay, Mada, GhIPSS, JCB, Interac) requires only:
1. Implement `SchemeAdapter`
2. Add jPOS packager XML for that scheme's private DEs
3. Register BIN ranges in BIN Management Module
4. Add interchange rates in Interchange Management Module
→ Zero changes to FEP core

**QPBOC (UnionPay contactless) handling:**
- UnionPay uses QPBOC (Quick Pass Based on CUP) — a CUP variant of EMV contactless
- QPBOC uses the same DE 55 structure but with CUP-specific EMV tags (tag `9F7C`, `9F77`, etc.)
- `UnionPaySchemeAdapter` includes a QPBOC tag parser alongside standard EMV tag processing

---

#### Module C — Interchange Management Module

**Purpose:** Every transaction processed through a card scheme incurs interchange fees and scheme assessment fees. These must be calculated per transaction, applied during settlement, and netted against gross amounts. Without this module, settlement figures will be incorrect.

**Package:** `com.cba.card.interchange`

**Key entities:**

| Table | Key Columns |
|-------|-------------|
| `interchange_rates` | id, scheme, card_type, mcc_category, transaction_type (`PURCHASE`/`CASH`/`REFUND`), channel (`CARD_PRESENT`/`CNP`), rate_percent `NUMERIC(6,4)`, fixed_fee `NUMERIC(10,4)`, currency_code, effective_from, effective_to |
| `scheme_fees` | id, scheme, fee_type (`ASSESSMENT`/`NETWORK`/`CROSS_BORDER`/`INTERNATIONAL_SERVICE`), rate_percent `NUMERIC(6,4)`, fixed_fee `NUMERIC(10,4)`, effective_from |
| `interchange_log` | id, authorization_log_id, scheme, interchange_amount, scheme_fee_amount, net_settlement_amount, rate_applied, calculated_at |

**InterchangeQualificationEngine:**
- Determines which interchange rate applies based on: scheme + card_type + MCC + transaction_type + channel + country
- Downgrade logic: if transaction does not meet preferred-rate criteria (e.g. chip not used), falls back to a higher interchange rate
- `calculateInterchange(AuthorizationLog)` → `InterchangeResult(interchangeAmount, schemeFees, netAmount)`

**Settlement netting formula:**
```
Net Settlement = Gross Transaction Amount
              − Interchange Fee (to issuer or acquirer depending on transaction direction)
              − Scheme Assessment Fee
              − Cross-Border Fee (if international)
```

**Endpoints** (ADMIN only):
- `GET/POST /api/v1/interchange/rates` — manage interchange rate tables per scheme
- `GET/POST /api/v1/interchange/fees` — manage scheme assessment fees
- `GET /api/v1/interchange/calculate?authId={id}` — calculate interchange for a given auth (dev/ops tool)

**Flyway:** `V4__interchange_management.sql` in card-service

---

#### Module D — 3D Secure 2.x / Access Control Server (ACS)

**Purpose:** All five schemes mandate 3D Secure (3DS2) for card-not-present (CNP/e-commerce) transactions. Without an ACS, the bank cannot authenticate CNP transactions — these would either be declined or processed without authentication liability shift.

**Package:** `com.cba.card.threeds`

**Flow:**
```
E-commerce merchant → 3DS Directory Server (Visa/MC/scheme hosted)
                              ↓  Authentication Request (AReq)
                    CBA Access Control Server (ACS)
                              ↓  Cardholder authentication
                              ↓  Authentication Response (ARes) + CAVV
                    3DS Directory Server → Merchant
                              ↓  Authorization with CAVV in DE 55
                    FEP → card-service (standard auth flow)
```

**Key entities:**

| Table | Key Columns |
|-------|-------------|
| `threeds_sessions` | id, card_id, merchant_id, amount, currency, acs_trans_id, ds_trans_id, status, authentication_value (CAVV), eci_indicator, created_at |

**ACS responsibilities:**
- Receive `AReq` from Directory Server over HTTPS
- Verify cardholder identity (OTP via SMS/email, biometric challenge, or frictionless if risk score low)
- Generate CAVV (Cardholder Authentication Verification Value) using card key via HSM
- Return `ARes` to Directory Server

**Endpoints:**
- `POST /3ds/acs/areq` — receive authentication request from Directory Server
- `GET /3ds/acs/challenge/{acsTransId}` — cardholder challenge page
- `POST /3ds/acs/challenge/{acsTransId}/verify` — submit OTP/biometric response

**Scheme registration** (per scheme, one-time setup):
- Visa — register ACS with Visa Directory Server (Visa 3D Secure)
- Mastercard — register with Mastercard SecureCode Directory Server
- Verve — register with Verve 3D Secure Directory Server
- Afrigo — register with PAPSS authentication infrastructure
- UnionPay — register with UnionPay SecurePlus Directory Server

---

#### Module E — Card Personalization Bureau Integration

**Purpose:** Physical card issuance for any scheme requires chip personalization — loading the EMV application, keys, and cardholder data onto the chip. This is done by a certified card bureau (Thales, HID Global, Idemia). Without this integration, `ORDERED` cards can never reach `PRODUCED` status.

**Package:** `com.cba.card.bureau`

**Key entities:**

| Table | Key Columns |
|-------|-------------|
| `bureau_jobs` | id, batch_ref, bureau_name, card_count, status (`PENDING`/`SENT`/`CONFIRMED`/`FAILED`), submitted_at, confirmed_at |
| `bureau_job_items` | id, job_id, card_id, personalization_data_hash, chip_serial_no, status |

**CDP (Card Data Preparation) file — per card, per scheme:**
- EMV application data (AID, application label, priority)
- Issuer Master Key derivative (calculated by HSM — never leaves the HSM in plaintext)
- Cardholder data (name, PAN, expiry — in scheme-mandated format)
- PIN offset (if pre-set PIN selected)
- Track 1/2 data for magnetic stripe encoding
- Each scheme has its own CDP file specification (Visa VIS CDP, MC M/Chip CDP, Verve CDP)

**Bureau API:** HTTP/SFTP batch file transmission to bureau; confirmation callback updates card status to `PRODUCED`

---

#### Module F — Scheme-Compliant Chargeback Module (Upgrade)

**Purpose:** Upgrade our basic dispute module to meet the chargeback standards of all five schemes. Each scheme has defined reason codes, strict processing timeframes, and mandatory workflow stages.

**Replaces:** Basic dispute module (`RAISED → UNDER_REVIEW → RESOLVED`)

**New state machine:**
```
RAISED → RETRIEVAL_REQUESTED → CHARGEBACK_INITIATED
       → REPRESENTMENT → PRE_ARBITRATION → RESOLVED
```

**Reason code frameworks (stored as configurable lookup table):**

| Scheme | Reason Code Examples |
|--------|---------------------|
| Visa | 10.1 (EMV liability shift), 10.4 (other fraud), 11.2 (declined authorization), 12.6 (duplicate) |
| Mastercard | 4853 (goods/services not provided), 4837 (no cardholder authorization), 4863 (cardholder does not recognize) |
| Verve | Verve Dispute Resolution Framework reason codes (mirrors Mastercard structure) |
| Afrigo | PAPSS dispute reason codes |
| UnionPay | CUP dispute reason codes |

**Timeframe enforcement:** `@Scheduled` job checks open chargebacks daily; auto-escalates or auto-resolves based on scheme deadlines (10 days, 45 days, etc.)

**Table additions:** `chargeback_reason_codes` (scheme, code, description, max_days_to_respond), `retrieval_requests`, `representments`

---

#### Updated Angular Screens (CardsModule — 12 screens total)

| Component | Route | Auth | Status |
|-----------|-------|------|--------|
| `CardListComponent` | `/cards` | ADMIN/TELLER | 🔲 Planned |
| `CardDetailComponent` | `/cards/:id` | ADMIN/TELLER | 🔲 Planned |
| `CardProductsComponent` | `/cards/products` | ADMIN | 🔲 Planned |
| `FraudRulesComponent` | `/cards/fraud` | ADMIN | 🔲 Planned |
| `SettlementComponent` | `/cards/settlement` | ADMIN | 🔲 Planned |
| `DisputesComponent` | `/cards/disputes` | ADMIN/TELLER | 🔲 Planned |
| `TerminalSimulatorComponent` | `/cards/terminal` | ADMIN/TELLER | 🔲 Planned |
| `ApiKeysComponent` | `/cards/api-keys` | ADMIN | 🔲 Planned |
| `WebhooksComponent` | `/cards/webhooks` | ADMIN | 🔲 Planned |
| `BinManagementComponent` | `/cards/bins` | ADMIN | 🔲 Planned |
| `SchemeConfigComponent` | `/cards/schemes` | ADMIN | 🔲 Planned |
| `InterchangeComponent` | `/cards/interchange` | ADMIN | 🔲 Planned |

---

### Build Order

1. ✅ **fep-service** — ISO 8583 TCP server, jPOS base packager, message router, HSM adapter, EMV handler _(commit `eb398cc`)_
2. ✅ **fep-service — Scheme Adapter Framework** — `SchemeAdapter` interface, all 5 adapters (Visa/MC/Verve/Afrigo/UnionPay), per-scheme jPOS packager XMLs, `SchemeAdapterFactory` _(commit `eb398cc`)_
3. ✅ **card-service — core modules** — card, limits, fraud, token, settlement, dispute, terminal simulator REST _(Session 28)_
4. ✅ **card-service — BIN Management Module** — BIN range table, 6/8-digit lookup, scheme routing; `GET /{bin}/scheme` M2M endpoint; `BinRangeRequest` DTO _(Session 29)_
5. ✅ **card-service — Interchange Management Module** — rate tables per scheme, qualification engine, settlement netting _(Session 30)_
6. ✅ **card-service — 3D Secure ACS** — `threeds` package, ACS endpoints, CAVV generation, OTP challenge, SecurityConfig `@Order(0)` chain _(Session 31)_
7. ✅ **card-service — Card Personalization Bureau** — CDP file generation, bureau job lifecycle; `ORDERED → PRODUCED → DISPATCHED` state progression _(Session 34)_
8. ✅ **card-service — Scheme-Compliant Chargeback** — full state machine, reason code framework, timeframe enforcement _(Session 36)_
9. **card-service — Open Banking layer** — Card API (`/card-api/v1/`), API key auth, webhook delivery, spending analytics
10. **backend (monolith)** — `CardServiceClient` REST client; AISP/CBPII extension for card accounts; `ConsentScope` additions
11. **Angular `CardsModule`** — 12 screens
12. **Docker Compose** — `card-service` + `fep-service` service definitions
13. **Infrastructure / K8s** — new deployment + service manifests

---

### card-service — Scheme-Compliant Chargeback Notes (Session 36)

**Build status**: `cd card-service && ./mvnw clean compile → BUILD SUCCESS (0 errors)`

**What was built:** Upgraded the basic `RAISED → UNDER_REVIEW → RESOLVED` dispute module to a full scheme-compliant chargeback workflow with 5-scheme reason code catalogue, sub-resource records for retrieval requests and representments, and a nightly timeframe enforcer.

**Verified package structure (additions to `com.cba.card.dispute`):**

```
card-service/src/main/java/com/cba/card/dispute/
├── DisputeStatus.java            — REWRITTEN: 7 states (RAISED/RETRIEVAL_REQUESTED/
│                                    CHARGEBACK_INITIATED/REPRESENTMENT/PRE_ARBITRATION/
│                                    RESOLVED/WITHDRAWN)
├── CardDispute.java              — EXTENDED: schemeReasonCode FK, currencyCode, 3 deadline
│                                    fields (chargebackDeadline/responseDeadline/
│                                    preArbitrationDeadline), resolutionFavor
├── ChargebackReasonCode.java     — NEW entity: scheme+code UNIQUE; 3 timeframe ints
├── ChargebackReasonCodeRepository.java — findBySchemeOrderByCode, findBySchemeAndCode
├── RetrievalRequest.java         — NEW entity: dispute FK, deadline, PENDING/FULFILLED/EXPIRED
├── RetrievalRequestRepository.java — findByStatusAndDeadlineBefore (timeframe enforcer)
├── Representment.java            — NEW entity: dispute FK, deadline, PENDING/ACCEPTED/
│                                    REJECTED/ESCALATED
├── RepresentmentRepository.java  — findByStatusAndDeadlineBefore (timeframe enforcer)
├── ChargebackTimeframeEnforcer.java — NEW @Scheduled(cron "0 0 2 * * *"):
│                                    (1) expire overdue retrieval requests
│                                    (2) auto-accept lapsed representments → RESOLVED ACQUIRER
├── DisputeService.java           — REWRITTEN: 7 named methods (raiseDispute,
│                                    requestRetrieval, initiateChargeback,
│                                    recordRepresentment, escalateToPreArbitration,
│                                    resolve, withdraw)
└── DisputeController.java        — REWRITTEN: 10 endpoints (see below)
```

**Flyway migration:** `V6__chargeback_module.sql`
- `chargeback_reason_codes` table + UNIQUE(scheme, code)
- `retrieval_requests` table + 3 indexes
- `representments` table + 3 indexes
- `ALTER TABLE card_disputes ADD COLUMN` (6 new columns)
- Seeds 17 reason codes across 5 schemes

**Endpoint inventory (dispute module — full replacement):**

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/v1/cards/disputes` | ADMIN/TELLER | List disputes; filter by `?status=` |
| `GET` | `/api/v1/cards/disputes/{id}` | ADMIN/TELLER | Single dispute detail |
| `GET` | `/api/v1/cards/disputes/{id}/retrieval-requests` | ADMIN/TELLER | List retrieval requests for dispute |
| `GET` | `/api/v1/cards/disputes/{id}/representments` | ADMIN/TELLER | List representments for dispute |
| `GET` | `/api/v1/cards/disputes/reason-codes` | ADMIN/TELLER | Scheme reason code catalogue; `?scheme=VISA` |
| `POST` | `/api/v1/cards/disputes` | ADMIN/TELLER/CUSTOMER | Raise dispute |
| `POST` | `/api/v1/cards/disputes/{id}/retrieval` | ADMIN/TELLER | Request documentation from acquirer |
| `POST` | `/api/v1/cards/disputes/{id}/chargeback` | ADMIN/TELLER | Initiate formal chargeback (body: `reasonCodeId`) |
| `POST` | `/api/v1/cards/disputes/{id}/representment` | ADMIN/TELLER | Record acquirer representment (body: `acquirerReason`) |
| `POST` | `/api/v1/cards/disputes/{id}/pre-arbitration` | ADMIN | Escalate to scheme pre-arbitration |
| `POST` | `/api/v1/cards/disputes/{id}/resolve` | ADMIN/TELLER | Final resolve (body: `resolvedBy`, `resolutionFavor`, `notes`) |
| `POST` | `/api/v1/cards/disputes/{id}/withdraw` | ADMIN/TELLER/CUSTOMER | Withdraw dispute |

**Timeframe enforcement logic:**

| Condition | Action |
|-----------|--------|
| `RetrievalRequest.status=PENDING` and `deadline < today` | Mark EXPIRED; warn in logs; human must initiate chargeback |
| `Representment.status=PENDING` and `deadline < today` | Mark ACCEPTED; dispute → RESOLVED favor=ACQUIRER (issuer missed deadline) |

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| `initiate_chargeback` valid from two states | `RAISED` (skip retrieval) AND `RETRIEVAL_REQUESTED` (after docs requested) — both are valid entry points to `CHARGEBACK_INITIATED` |
| No auto-escalation on expired retrievals | Unlike representments, expired retrieval requests don't auto-escalate — reason code is not yet known so deadlines can't be calculated. Human decision required. |
| `resolutionFavor` is "ISSUER" or "ACQUIRER" (not enum) | Stored as VARCHAR(10); validated in service with `equalsIgnoreCase`; normalized to uppercase on save |
| `listRetrievalRequests` / `listRepresentments` use in-memory filter | Sub-resources are few per dispute; no dedicated `findByDisputeId` query needed — `findAll()` + stream filter is acceptable |
| `ChargebackReasonCode` is reference data only | No admin endpoint to create/update reason codes at runtime — changes require a new Flyway migration. This mirrors how scheme rule books work in production. |

---

### card-service — Multi-Currency Rules (Session 32)

The card platform is **fully multi-currency**. Every monetary threshold and comparison must be per-currency. The following rules apply to all future card-service development:

#### Currency Representation
- All amounts in the system use **ISO 4217 minor units** (e.g. cents for USD/KES/GHS, kobo for NGN)
- Currency is identified by **ISO 4217 numeric code** (e.g. `"840"`=USD, `"404"`=KES, `"288"`=GHS, `"566"`=NGN) — the same code that appears in DE49 of ISO 8583 messages
- Never use alphabetic codes (`USD`, `KES`) as map keys in config or DB — use numeric codes for consistency with the wire format

#### Per-Currency Config Pattern
Use a YAML map keyed by ISO numeric code with a `"default"` fallback:
```yaml
card.threeds.frictionless-limits:
  "840": 5000      # USD: $50.00
  "404": 700000    # KES: 7,000 KES
  "288": 50000     # GHS: 500 GHS
  "default": 5000  # fallback for unlisted currencies
```
Inject as `@Value("#{${card.threeds.frictionless-limits}}") Map<String, Long>` and resolve with `map.getOrDefault(currencyCode, map.getOrDefault("default", fallback))`.

#### Fraud Rule Per-Currency Thresholds
Store currency-specific thresholds in the rule's `params` JSONB column under a `"thresholds"` map key:
```json
{"thresholds":{"840":100000,"404":13000000,"288":500000},"default_threshold_minor_units":100000}
```
The `FraudEngine.resolveSingleAmountThreshold()` method handles this lookup. Add new currencies by updating the DB row — no code change needed.

#### Hard Rules — Never Do These
| Don't | Do instead |
|-------|-----------|
| Hardcode `"840"` as a currency default | Use configurable `@Value` with `"default"` key |
| Compare `amountCents` to a single threshold | Look up threshold by `currencyCode` from a map |
| Default `currencyCode` to `null` or `"USD"` when missing | Throw `IllegalArgumentException` — fail loudly |
| Use alphabetic codes (`USD`) as YAML/DB map keys | Use ISO numeric codes (`"840"`) |

---

### card-service — 3DS ACS Implementation Notes (Session 31)

**Build status**: `./mvnw clean compile → BUILD SUCCESS (0 errors)`

**Verified package structure:**

```
card-service/src/main/java/com/cba/card/threeds/
├── ThreeDsStatus.java           — enum: INITIATED, CHALLENGE_REQUIRED, AUTHENTICATED, FAILED, REJECTED
├── ThreeDsSession.java          — JPA entity; @Version; @PreUpdate; stores CAVV in authentication_value
├── ThreeDsOtpToken.java         — JPA entity; otp_hash only (HMAC-SHA256, never plaintext)
├── ThreeDsSessionRepository.java — findByAcsTransId, findByCardId, findByStatus
├── ThreeDsOtpTokenRepository.java — findTopBySessionIdAndVerifiedFalseOrderByCreatedAtDesc
├── CavvGenerator.java           — software CAVV via javax.crypto.Mac (HmacSHA256); hmacHex() for OTP hashing
├── AReqMessage.java             — EMVCo 3DS 2.3 AReq DTO record; scaledAmount() helper
├── AResMessage.java             — ARes record; factory methods frictionless/challenge/declined/attempted
├── ChallengeSubmitRequest.java  — cardholder OTP DTO (@NotBlank @Size(min=4,max=8))
├── ChallengeVerifyResponse.java — outcome record; authenticated/failed/locked factory methods
├── ThreeDsService.java          — orchestration; frictionless decision; OTP gen (SecureRandom); CAVV gen; verify
└── ThreeDsController.java       — POST /3ds/acs/areq, GET /3ds/acs/challenge/{id} (HTML), POST /3ds/acs/challenge/{id}/verify
```

**Resources added:**
```
card-service/src/main/resources/
├── application.yml               — card.threeds.* config block (cavv-master-key, frictionless-limit, otp-expiry, max-attempts, acs-base-url)
└── db/migration/V4__threeds_module.sql — threeds_sessions + threeds_otp_tokens tables
```

**Endpoints (3DS ACS — no JWT):**

| Method | Path | Caller | Description |
|--------|------|--------|-------------|
| `POST` | `/3ds/acs/areq` | Directory Server | Receive AReq; return ARes (Y/N/C) |
| `GET` | `/3ds/acs/challenge/{acsTransId}` | Cardholder browser | Challenge HTML page |
| `POST` | `/3ds/acs/challenge/{acsTransId}/verify` | Browser JS / 3DS SDK | Submit OTP; returns JSON |

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| `@Order(0)` chain must be declared first | `threeDsChain` at Order 0 matches `/3ds/acs/**` and permits all — must be before the JWT chain at Order 2 or the JWT filter intercepts first |
| `GET /3ds/acs/challenge/{id}` returns `TEXT_HTML_VALUE` | Produces HTML because the cardholder's browser is redirected here; `@RestController` would normally return JSON — override with `produces = MediaType.TEXT_HTML_VALUE` |
| `%%` in Java text blocks for `String.formatted()` | Text blocks with `%s` for `String.formatted()` need literal `%%` wherever the HTML contains a single `%` — affects CSS percentages in the challenge page |
| OTP at DEBUG log level only | `log.debug("3DS OTP: {}", otp)` — never at INFO in production; application.yml sets `com.cba.card: INFO` so DEBUG is off by default |
| CAVV uses `frictionless ECI "05"` not "06" | EMVCo 3DS 2.x: ECI "05" applies to both frictionless and challenge-verified authentications; "06" = attempted (3DS tried, not verified); "07" = no 3DS |
| `panHmacKey` used for both PAN hash and OTP hash | Consistent key hierarchy — same key used in `CardService` for PAN hashing; avoids a second key in config |

---

### card-service — Bureau Module Implementation Notes (Session 33)

**Build status**: `cd card-service && ./mvnw clean compile → BUILD SUCCESS (0 errors)`

**Package**: `com.cba.card.bureau`

**Flyway**: `V5__bureau_module.sql` — `bureau_jobs` + `bureau_job_items` tables

**Verified package structure:**
```
card-service/src/main/java/com/cba/card/bureau/
├── BureauJobStatus.java          — enum: PENDING | SENT | CONFIRMED | FAILED
├── BureauJobItemStatus.java      — enum: PENDING | PERSONALIZED | FAILED
├── BureauJob.java                — JPA entity; batch_ref UNIQUE; @OneToMany items
├── BureauJobItem.java            — JPA entity; personalization_data_hash SHA-256 hex
├── BureauJobRepository.java
├── BureauJobItemRepository.java  — findByJobIdAndStatus for dispatch step
├── CdpRecord.java                — record: all CDP fields; panEncryptedForBureau never in REST response
├── CdpGenerator.java             — scheme-aware AID/AIP/service-code/IAC resolution; SHA-256 hash
├── BureauConfirmRequest.java     — bureau callback DTO; partial confirmation support
├── BureauService.java            — 4-step lifecycle: createJob/submitJob/confirmJob/dispatchJob
└── BureauController.java         — POST /api/v1/bureau/jobs + lifecycle commands
```

**Endpoint inventory:**

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/v1/bureau/jobs` | ADMIN | Create batch from all ORDERED cards |
| `GET` | `/api/v1/bureau/jobs` | ADMIN | List all jobs (newest first) |
| `GET` | `/api/v1/bureau/jobs/{id}` | ADMIN | Job detail + items |
| `POST` | `/api/v1/bureau/jobs/{id}/submit` | ADMIN | Generate CDP, mark SENT |
| `POST` | `/api/v1/bureau/jobs/{id}/confirm` | ADMIN | Bureau callback → PRODUCED |
| `POST` | `/api/v1/bureau/jobs/{id}/dispatch` | ADMIN | Mark DISPATCHED |
| `POST` | `/api/v1/bureau/jobs/{id}/fail` | ADMIN | Mark FAILED + reason |
| `GET` | `/api/v1/bureau/jobs/{jobId}/cdp/{cardId}` | ADMIN | CDP preview (no PAN in response) |

**Card status progression driven by bureau lifecycle:**

| Bureau event | Card status | PhysicalCardOrder status |
|---|---|---|
| `createJob` | ORDERED (unchanged) | ORDERED |
| `submitJob` | ORDERED (unchanged) | productionRequestDate set |
| `confirmJob` | **PRODUCED** | **PRODUCED** + bureauRef |
| `dispatchJob` | **DISPATCHED** | **DISPATCHED** + dispatchDate |
| Cardholder activates | ACTIVATION_PENDING → ACTIVE | — |

**EMV AID lookup by scheme:**
| Scheme | Card Type | AID |
|--------|-----------|-----|
| Visa | Debit/Credit | `A0000000031010` |
| Mastercard | Credit | `A0000000041010` |
| Mastercard | Debit | `A0000000043060` (Maestro) |
| Verve | Any | `A000000333010101` |
| Afrigo | Any | `A000000337010008` |
| UnionPay | Any | `A000000333010102` |

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| `panEncryptedForBureau` must never appear in REST responses | `CdpPreviewResponse` record in controller strips it — `CdpRecord` carries it for bureau file generation only |
| SHA-256 hash computed on `CdpRecord` with empty hash field | Two-step construction: build record with `hash=""`, compute hash, rebuild record with computed hash |
| `PhysicalCardOrderRepository.findByStatus()` was missing | Added to repository — needed by `BureauService.createJob()` to collect ORDERED orders |
| `ApiResponse.ok()` not `ApiResponse.of()` | The `ApiResponse` envelope uses `ok()` as the factory method |
| Bureau name from config | `${card.bureau.name:CBA_BUREAU}` in `application.yml` — override per deployment |

---

### fep-service — QPBOC SM4 Adapter Notes (Session 32)

**Build status**: `./mvnw clean compile → BUILD SUCCESS (0 errors)`

**What was the ⚠️:** `ArqcValidator` only implemented 3DES CBC-MAC. Domestic China UnionPay (QPBOC) cards use SM4, so all valid domestic CUP ARQCs were returning `false`. The gap analysis row correctly flagged this.

**Files changed:**

| File | Change |
|------|--------|
| `fep-service/…/emv/CryptogramAlgorithm.java` | NEW — enum `TDES` \| `SM4` |
| `fep-service/…/scheme/SchemeAdapter.java` | Added `default getCryptogramAlgorithm()` → `TDES` |
| `fep-service/…/scheme/UnionPaySchemeAdapter.java` | Override → `SM4` |
| `fep-service/…/emv/ArqcValidator.java` | Full rewrite: overloaded `validate()`, SM4 path, CID offline detection |
| `fep-service/…/router/AuthorizationHandler.java` | Line 101: passes `adapter.getCryptogramAlgorithm()` to validator |

**CID offline detection (tag `9F27`) logic:**

| CID bits 7-6 | AC type | Action |
|---|---|---|
| `0x80` | ARQC | Proceed with online ARQC validation |
| `0x40` | TC  | Offline approved — skip validation, return `true` |
| `0x00` | AAC | Offline declined — skip validation, return `false` |

**SM4 vs TDES key derivation:**
- Same derivation constants (`0xF0`/`0x0F`) as EMV Book 2
- TDES: two separate 8-byte single-block encryptions → 16-byte SK
- SM4: one 16-byte block encryption (16-byte IMK key, 16-byte input = left‖right derivation halves) → 16-byte SK

**SM4 fallback for international UnionPay:**
- If SM4 ARQC fails, validator silently retries with TDES
- International UnionPay cards outside mainland China still use 3DES; this prevents false declines in dev/test environments

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| SM4Engine block size = 16 bytes | Unlike 3DES (8-byte block), SM4 works on 128-bit blocks. `CBCBlockCipherMac` with SM4Engine handles this automatically — no padding change needed |
| `BCCBlockCipherMac` MAC size parameter | Constructor takes bits not bytes: `new CBCBlockCipherMac(engine, 64)` = 8-byte MAC regardless of block cipher |
| SM4 key derivation single-pass | Since SM4 has 16-byte blocks, both left and right derivation halves fit in one encryption call (vs two 3DES calls) |
| `SchemeAdapter.getCryptogramAlgorithm()` is a `default` method | All other adapter implementations (Visa, MC, Verve, Afrigo, Unknown) automatically return TDES without any code change |

---

### fep-service — Implementation Notes (Session 27)

**Build status**: `./mvnw clean compile → BUILD SUCCESS (0 errors)` — commit `eb398cc`

**Verified package structure:**

```
fep-service/src/main/java/com/cba/fep/
├── FepApplication.java
├── server/
│   ├── FepTcpServer.java           — @PostConstruct/@PreDestroy Netty lifecycle; port from ${fep.tcp.port:8583}
│   ├── FepServerInitializer.java   — ChannelInitializer; 2-byte LengthFieldBasedFrameDecoder(65535,0,2,0,2)
│   └── FepMessageHandler.java      — @ChannelHandler.Sharable; RC=96 fallback on unhandled exception
├── iso/
│   ├── IsoField.java               — compile-time DE constants (MTI=0 … DE128)
│   └── IsoMessageFactory.java      — EnumMap<SchemeType, GenericPackager> loaded from classpath XMLs
├── router/
│   ├── MessageRouter.java          — Java 21 switch on MTI string; RC=30 for unknown MTI
│   ├── AuthorizationHandler.java   — 0100/0120 handler with full PIN+EMV+detokenize flow
│   ├── FinancialHandler.java       — 0200/0220 handler; DE54 balance format: "40"+ccy+"C"+amount(12)
│   ├── ReversalHandler.java        — 0400/0420; DE90 original data elements
│   └── NetworkHandler.java         — 0800 sign-on/sign-off/echo; DE70 network codes
├── scheme/
│   ├── SchemeType.java             — enum: VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY, UNKNOWN
│   ├── SchemeAdapter.java          — interface: applyPackager, extractPrivateData, embedArpc, finalizeResponse
│   ├── AbstractSchemeAdapter.java  — explicit constructor (NOT @RequiredArgsConstructor); Logger via LoggerFactory.getLogger(getClass())
│   ├── SchemeAdapterFactory.java   — List<SchemeAdapter> Spring injection; ConcurrentHashMap BIN cache; public refreshBinCache()
│   ├── VisaSchemeAdapter.java      — DE60-63 private DEs; STIP stand-in via result.standIn() (not isStandIn())
│   ├── MastercardSchemeAdapter.java— DE48 PDS parser TAG(4)+LEN(3)+VALUE; DE111-125 MIP; result.mipReference()
│   ├── VerveSchemeAdapter.java     — DE62-63 Interswitch subelements
│   ├── AfrigoSchemeAdapter.java    — PAPSS minimal private DEs
│   ├── UnionPaySchemeAdapter.java  — QPBOC tags 9F7C/9F77/9F78/9F79; dual-currency DE49≠DE50 detection
│   └── UnknownSchemeAdapter.java   — fallback; extracts no private data
├── hsm/
│   ├── HsmAdapter.java             — interface: verifyPin, verifyCvv, generateArpc, translatePinBlock, generateMac, generateKcv
│   ├── SoftwareHsmAdapter.java     — @ConditionalOnProperty(fep.hsm.provider=SOFTWARE, matchIfMissing=true); Bouncy Castle TDES; dev only
│   └── ThalesPayShieldAdapter.java — @ConditionalOnProperty(fep.hsm.provider=THALES); TCP stubs; 2-byte length framing
├── emv/
│   ├── EmvDataParser.java          — BER-TLV parser; multi-byte tags; long-form lengths; constructed tag recursion
│   ├── ArqcValidator.java          — EMV Book 2 session key derivation; CBC-MAC; ISO/IEC 7816-4 padding (0x80 then 0x00s)
│   └── ArpcGenerator.java          — Method 1: 3DES-MAC(SK, ARQC XOR ARC)
└── auth/
    ├── AuthorizationRequest.java   — @Builder @With record; canonical constructor defaults scheme to UNKNOWN
    ├── AuthorizationResult.java    — record; factory methods approve/decline/systemError; plain-name accessors (approved(), responseCode())
    └── CardServiceClient.java      — RestTemplate; all calls catch RestClientException → systemError() fallback
```

**Resources:**
```
fep-service/src/main/resources/
├── application.yml
├── iso8583-1987-fields.xml     — base packager (pre-BIN-lookup unpack)
├── iso8583-visa.xml            — Visa private DEs 60-63, 126
├── iso8583-mastercard.xml      — MC DE48 PDS structure, DE111-127
├── iso8583-verve.xml           — Verve/Interswitch DE62-63
├── iso8583-afrigo.xml          — Afrigo/PAPSS (minimal private DEs)
└── iso8583-unionpay.xml        — CUP DE60-63, DE36/40/46-47/50; QPBOC tags in DE55 comment
```

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| Lombok not processing in fep-service | `maven-compiler-plugin` must declare `annotationProcessorPaths` with Lombok 1.18.38 explicitly — Spring Boot parent default does NOT reliably activate it |
| `@RequiredArgsConstructor` on abstract class | Does not work reliably; use explicit constructor + `LoggerFactory.getLogger(getClass())` instead of `@Slf4j` |
| Java record accessors | Records generate plain-name accessors: `approved()` not `isApproved()`, `responseCode()` not `getResponseCode()`. Applies to `AuthorizationResult` and all other records in fep-service |
| `refreshBinCache()` visibility | Must be `public` — the `BinCacheRefreshScheduler` is in a different package (`com.cba.fep.config`) |
| Maven wrapper | fep-service has no `./mvnw` by default; copy from backend: `cp backend/mvnw fep-service/mvnw && chmod +x && cp -r backend/.mvn fep-service/.mvn` |
| TCP framing | ISO 8583 uses 2-byte big-endian length prefix (excludes the 2-byte header itself). Netty: `LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2)` + `LengthFieldPrepender(2)` |
| `@ChannelHandler.Sharable` | Required on `FepMessageHandler` — Netty enforces this at runtime when a single handler instance is added to multiple pipelines |

---

### card-service — Interchange Management Module Notes (Session 30)

**Build status**: `cd card-service && ./mvnw clean compile → BUILD SUCCESS (0 errors)`

**Package**: `com.cba.card.interchange`

**Endpoint inventory:**

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/v1/interchange/rates` | ADMIN | List all active interchange rate tiers |
| `GET` | `/api/v1/interchange/rates/{id}` | ADMIN | Single rate by UUID |
| `POST` | `/api/v1/interchange/rates` | ADMIN | Create rate (validated `InterchangeRateRequest` DTO) |
| `PUT` | `/api/v1/interchange/rates/{id}` | ADMIN | Update rate |
| `DELETE` | `/api/v1/interchange/rates/{id}` | ADMIN | Soft-delete |
| `GET` | `/api/v1/interchange/fees` | ADMIN | List all active scheme fees |
| `GET` | `/api/v1/interchange/fees/{id}` | ADMIN | Single fee by UUID |
| `POST` | `/api/v1/interchange/fees` | ADMIN | Create scheme fee (validated `SchemeFeeRequest` DTO) |
| `PUT` | `/api/v1/interchange/fees/{id}` | ADMIN | Update fee |
| `DELETE` | `/api/v1/interchange/fees/{id}` | ADMIN | Soft-delete |
| `GET` | `/api/v1/interchange/calculate?authId=` | ADMIN | Calculate interchange for a specific auth (persists to log) |
| `GET` | `/api/v1/interchange/log/{authId}` | ADMIN | Retrieve most recent interchange log for an auth |

**Qualification engine flow:**
1. Resolve `CardType` from `cards` table via `cardId`
2. Map `entryMode` → `ChannelType` (CHIP/CONTACTLESS/SWIPE → CARD_PRESENT; default → CNP)
3. Map `processingCode` DE3 prefix → `TransactionType` (01x → CASH; 20x → REFUND; default → PURCHASE)
4. Query `interchange_rates` ordered by MCC specificity (non-null before null); first row wins
5. Sum all active `scheme_fees` for the scheme (ASSESSMENT + NETWORK + any CROSS_BORDER seeded)
6. Net = Gross − Interchange − SchemeFees; persist to `interchange_log`

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| JPQL enum parameters | Pass typed `CardType`, `TransactionType`, `ChannelType` enums directly to repository — NOT strings. Strings require native SQL or a String-typed entity field |
| `CASE WHEN` in JPQL ORDER BY | Hibernate 6.x (Spring Boot 3.x) supports this; earlier Hibernate 5.x does not. Verify Hibernate version before adding similar expressions |
| Engine is `@Component` not `@Service` | Intentional: the calling `@Service` (`InterchangeService` or `SettlementService`) owns the `@Transactional` scope. Engine inherits the transaction. |
| Migration numbering | `V3__interchange_management.sql` — V3 because `V3__bin_management.sql` was never created (bin_ranges already in V1). CLAUDE.md spec said V4 — spec was aspirational. |

---

### card-service — BIN Management Module Notes (Session 29)

**Build status**: `cd card-service && ./mvnw clean compile → BUILD SUCCESS (0 errors)`

**Endpoint inventory (BIN module):**

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/api/v1/bins` | ADMIN | List all BIN ranges (admin UI) |
| `GET` | `/api/v1/bins/all` | none | Full BIN→scheme export for FEP cache (raw `Map<String,String>`) |
| `GET` | `/api/v1/bins/lookup?pan=` | none | Dev/ops diagnostic — scheme by PAN prefix (ApiResponse wrapper) |
| `GET` | `/api/v1/bins/{bin}/scheme` | none | FEP M2M fallback — scheme by BIN string (raw `{"scheme":"VISA"}`) |
| `GET` | `/api/v1/bins/{id}` | ADMIN | Single BIN range by UUID |
| `POST` | `/api/v1/bins` | ADMIN | Create BIN range (validated `BinRangeRequest` DTO) |
| `PUT` | `/api/v1/bins/{id}` | ADMIN | Update BIN range (validated `BinRangeRequest` DTO) |
| `DELETE` | `/api/v1/bins/{id}` | ADMIN | Soft-delete BIN range (`active=false`) |

**Critical gotchas:**

| Issue | Fix |
|-------|-----|
| `GET /api/v1/bins/{bin}/scheme` vs `CardServiceClient.lookupBinScheme()` | fep-service calls this path and reads `response.get("scheme")` — the response must be a raw Map, NOT wrapped in ApiResponse; controller method returns `ResponseEntity<Map<String,String>>` |
| `GET /api/v1/bins/lookup` vs `/{bin}/scheme` | Different shapes for different consumers: `lookup?pan=` wraps in ApiResponse for human use; `/{bin}/scheme` is bare JSON for machine use |
| `bin_ranges` in V1, not V3 | CLAUDE.md spec said `V3__bin_management.sql` but the table was already in `V1__card_schema.sql` from initial scaffold — no separate migration needed |
| `@Cacheable("binLookup")` cache | Spring's simple cache manager handles this automatically when no explicit `CacheManager` bean is declared — no additional config needed |

---

### card-service — Implementation Notes (Session 28)

**Build status**: `./mvnw clean compile → BUILD SUCCESS (0 errors)`

**Verified package structure:**

```
card-service/src/main/java/com/cba/card/
├── CardApplication.java           — @SpringBootApplication + @EnableCaching + @EnableAsync + @EnableScheduling
├── card/
│   ├── Card.java                  — JPA entity: pan_encrypted, pan_hash (HMAC-SHA256), pan_prefix/suffix, CardType, CardStatus
│   ├── CardProduct.java           — card product template: CardType, BIN range, default daily limit, features JSONB
│   ├── CardService.java           — issueCard, executeCommand (block/unblock/cancel/activate/replace), expireCards (CoB)
│   └── CardController.java        — GET/POST /api/v1/cards, GET/POST /api/v1/cards/{id}?command=...
├── limits/
│   ├── CardLimit.java             — UNIQUE on card_id; daily_purchase/withdrawal, per_txn, monthly limits
│   └── CardLimitService.java      — update limits; validate ≥0; enforce per_txn ≤ daily
├── fraud/
│   ├── FraudEngine.java           — rules evaluated in priority order; hard-block rules short-circuit
│   ├── FraudRuleEntity.java       — rule_id UNIQUE, weight 0-100, params JSONB
│   └── FraudController.java       — GET/PUT /api/v1/cards/fraud/rules
├── token/
│   ├── TokenVault.java            — dpan_encrypted + dpan_hash (HMAC-SHA256); DPAN BIN prefix "9999"
│   └── TokenService.java          — generateToken, detokenize, suspendToken, deleteToken
├── auth/
│   ├── CardAuthorizationService.java — full 0100/0120 flow: card lookup → fraud → balance → approve/decline
│   └── CardAuthorizationController.java — POST /api/v1/cards/authorize (called by fep-service)
├── settlement/
│   ├── SettlementBatch.java       — batch_ref UNIQUE; OPEN → CLOSED → SETTLED | FAILED
│   ├── SettlementItem.java        — @ManyToOne batch; PENDING → SETTLED | FAILED
│   ├── SettlementService.java     — openOrGetTodaysBatch, addToCurrentBatch, closeBatch; @Scheduled(cron "0 58 23 * * *") expiry
│   └── SettlementController.java  — GET/POST /api/v1/cards/settlement/batches, POST /batches/{id}/close
├── dispute/
│   ├── CardDispute.java           — card_id, transaction_ref (RRN), DisputeReason, DisputeStatus
│   ├── DisputeService.java        — raiseDispute, updateDispute (review/resolve_issuer/resolve_acquirer/withdraw)
│   └── DisputeController.java     — GET/POST /api/v1/cards/disputes, PUT /api/v1/cards/disputes/{id}?command=...
├── terminal/
│   ├── FepIso8583Client.java      — Netty TCP client; same 2-byte length-prefix framing as FEP server; one-connection-per-request
│   ├── Iso8583Builder.java        — minimal ISO 8583 builder: LLVAR, fixed-length, 8-byte primary bitmap, STAN counter
│   ├── SimulateRequest.java       — covers all MTI types; optional fields null-safe
│   ├── SimulateResponse.java      — responseCode + description, authCode, availableBalance, STAN, RRN, hex dumps
│   ├── TerminalSimulatorService.java — builds 0100/0200/0400/0800; best-effort response decoder (no jPOS)
│   └── TerminalSimulatorController.java — POST /api/v1/simulate/{purchase,withdrawal,balance,reversal,network/signon,network/echo}
├── bin/
│   ├── BinRange.java              — bin_start/bin_end VARCHAR(8); 6 and 8-digit BIN support
│   └── BinService.java            — lookupScheme(pan) via range scan; caches results
├── wallet/
│   └── PrepaidWallet.java         — balance NUMERIC(19,4); UNIQUE on card_id
└── common/ config/
    ├── ApiResponse.java            — { data, meta, errors } standard envelope
    ├── CbaException.java           — notFound/badRequest/conflict factory methods
    └── RestClientConfig.java       — RestTemplate bean for monolith backend calls
```

**Resources:**
```
card-service/src/main/resources/
├── application.yml                 — port 8081, card_db PostgreSQL, Jasypt AES-256, Keycloak JWT, FEP TCP config
└── db/migration/
    ├── V1__card_schema.sql         — all tables: cards, card_products, physical_card_orders, card_limits,
    │                                 prepaid_wallets, bin_ranges, authorization_log, fraud_rules, fraud_score_log,
    │                                 token_vault, settlement_batches, settlement_items, card_disputes,
    │                                 api_keys, webhooks, webhook_delivery_log
    └── V2__card_demo_data.sql      — demo card products, BIN ranges, fraud rules, sample cards
```

**Critical gotchas for future sessions:**

| Issue | Fix |
|-------|-----|
| `int fieldLen` uninitialized in switch | Arrow-block switch cases don't guarantee assignment for javac; initialize `int fieldLen = 0;` before the switch |
| `CardController.listProducts()` type mismatch | Pre-existing bug: was returning `cardService.findByCustomer(null)` (List<Card>) as `List<CardProduct>`; fixed to `List.of()` |
| No jPOS in card-service | card-service's `Iso8583Builder` is a minimal custom builder (no jPOS); jPOS lives only in fep-service |
| `FepIso8583Client` one-connection-per-request | Appropriate for simulator; NioEventLoopGroup shut down after each call; acceptable overhead for dev tooling |
| Maven wrapper | Copy from backend (same as fep-service): `cp backend/mvnw card-service/mvnw && chmod +x && cp -r backend/.mvn card-service/.mvn` |

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
