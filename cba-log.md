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
| **Audit Module** | `audit/AuditLog`, `AuditLogRepository`, `AuditLogService`, `AuditController` | Append-only; `@Transactional(REQUIRES_NEW)`; 7-year retention; REST search at `/api/v1/audits` |
| **SMS Campaigns** | `social/SmsCampaign`, `SmsMessage`, repos, `SmsCampaignService`, `SmsCampaignController` | CRUD + activate command; message delivery tracking per recipient |
| **Report Mailing Jobs** | `social/ReportMailingJob`, `ReportMailingJobRepository`, `ReportMailingJobService`, `ReportMailingJobController` | CRUD + manual run; iCal RRULE recurrence; run history tracked |
| **Standing Instructions** | `social/StandingInstruction`, `StandingInstructionRepository`, `StandingInstructionService`, `StandingInstructionController` | Mifos-compatible model; FIXED/OUTSTANDING_BALANCE; disable/enable lifecycle |
| **Global Search** | `search/SearchResult`, `SearchService` (JdbcTemplate), `SearchController` | Cross-entity ILIKE search; resource-filtered; avoids domain coupling |
| **Two-Factor Auth** | `user/TwoFactorToken`, `TwoFactorTokenRepository`, `TwoFactorService`, `TwoFactorController` | 6-digit OTP; 10-min expiry; EMAIL/SMS delivery; single-use tokens |
| **Beneficiaries** | `customer/Beneficiary`, `BeneficiaryRepository`, `BeneficiaryService`, `BeneficiaryController` | Customer sub-resource; soft-delete; transfer limit cap; ownership validation |
| **Client Images** | `customer/ClientImage`, `ClientImageRepository`, `ClientImageService`, `ClientImageController` | One image per customer; PUT upsert; FILE_SYSTEM/S3/DATABASE storage types |
| **Credit Bureau** | `system/CreditBureauIntegration`, `CreditBureauProductMapping`, repos, `CreditBureauService`, `CreditBureauController` | Multi-bureau support; product mappings; activate/deactivate lifecycle |
| **Surveys** | `system/Survey`, `SurveyQuestion`, `SurveyResponse`, `SurveyScorecard`, `SurveyScorecardScore`, repos, `SurveyService`, `SurveyController` | PPI/welfare engine; 5-entity cascade; lookup by key; scorecard submission |
| **Accounting Rules** | `accounting/AccountingRule`, `AccountingRuleRepository`, `AccountingRuleService`, `AccountingRuleController` | GL debit/credit rule templates; multiple debit/credit flags |
| **Provisioning Criteria** | `accounting/ProvisioningCriteria`, `ProvisioningCriteriaDefinition`, repos, `ProvisioningCriteriaService`, `ProvisioningCriteriaController` | IFRS 9/Basel II age-band categories; provision %; replace-all update pattern |
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
| **Infrastructure — Docker Compose** | `infrastructure/docker-compose.yml` with postgres, keycloak, backend, web, mailhog | Phase 4 |
| **Infrastructure — Kubernetes** | `infrastructure/k8s/` namespace, deployments, services, ingress, HPA, sealed secrets | Phase 4 |
| **Infrastructure — Keycloak Realm** | `infrastructure/keycloak/cba-realm.json` with cba realm, 3 clients, FAPI 2.0, demo users | Phase 4 |
| **Web Frontend — Angular (flesh out)** | Feature components fully implemented (data bound to backend API, not just stubs) | Phase 2 |
| **Mobile Frontend — Flutter** | Customer mobile app (auth, dashboard, accounts, loans, payments, profile) | Phase 3 |

---

## Change History

### Session 10 — 2026-04-09

**Angular Web Portal scaffold: full feature module structure, routing, shared components, Nubeero design system, HTML prototypes co-located with Angular stubs, keycloak-angular v19 wiring, clean build.**

#### New Angular Files

| Category | Files | Notes |
|----------|-------|-------|
| **App config & routing** | `app.config.ts`, `app.routes.ts`, `app.ts` | `provideKeycloak` with `withAutoRefreshToken`; lazy-loaded feature routes for 7 nav groups |
| **Core — Auth** | `core/auth/auth.guard.ts`, `auth.interceptor.ts` | `createAuthGuard` (keycloak-angular v19); `authInterceptor` attaches Bearer token + `X-Tenant-ID` header |
| **Layout** | `layout/shell/shell.ts`, `layout/sidebar/sidebar.ts`, `layout/topbar/topbar.ts` | Nubeero dark shell; 7 nav groups with 50+ items matching Mifos/Fineract groupings |
| **Shared components** | `shared/components/kpi-card/`, `data-table/`, `status-badge/`, `page-header/` | Reusable banking UI components; all styled with Nubeero tokens |
| **Design tokens** | `assets/styles/_tokens.scss` | Full Nubeero SCSS token set (source of truth); `includePaths: ["src"]` in `angular.json` |
| **Feature stubs — Operations** | `features/operations/`: dashboard, customers, accounts, loans, payments, transactions | Lazy-loaded; route stubs with `screens/` prototype co-location |
| **Feature stubs — Products** | `features/products/`: loan-products, deposit-products, charges, floating-rates | |
| **Feature stubs — Groups** | `features/groups/`: groups, centers, collection-sheets | |
| **Feature stubs — Accounting** | `features/accounting/`: gl-accounts, journal-entries, gl-closures, accounting-rules, provisioning | |
| **Feature stubs — Reports** | `features/reports/`: reports, run-report, mailing-jobs | |
| **Feature stubs — System** | `features/system/`: codes, global-config, payment-types, funds, taxes, holidays, hooks, surveys | |
| **Feature stubs — Admin** | `features/admin/`: users, roles, tellers, offices, staff, audits | |
| **Feature stubs — Open Banking** | `features/open-banking/`: consents, accounts-info | |

#### HTML Prototypes (co-located in screens/)

| Prototype | Location | Based On |
|-----------|----------|----------|
| `dashboard.html` | `features/operations/screens/` | `.claude/skills/cba/designs/screens/backoffice/dashboard.html` |
| `customers.html` | `features/operations/screens/` | `.claude/skills/cba/designs/screens/backoffice/customers.html` |
| `loans.html` | `features/operations/screens/` | `.claude/skills/cba/designs/screens/backoffice/loans.html` |
| `*-prototype.html` ×14 | Each feature module `screens/` | Generated Nubeero-themed stubs |

#### Key Decisions & Fixes

| Issue | Fix |
|-------|-----|
| Angular 21 (not 17) installed — new file naming | Adopted: `.component` suffix dropped; files named `sidebar.ts`, `topbar.ts` etc. |
| Vitest replaces Karma in Angular 21 | `@angular/build:application` (Vite-based); test config updated |
| SCSS `@use` path resolution failure across deep hierarchies | Added `stylePreprocessorOptions: { includePaths: ["src"] }` to `angular.json`; all SCSS uses `@use 'assets/styles/tokens' as *;` |
| Wrong TypeScript import depths in generated stubs | Python script formula corrected: `'../' * depth` (depth = segments from `src/app/`) |
| keycloak-angular v19 breaking: `Keycloak` not exported | `import Keycloak from 'keycloak-js'` (default import); fixed in `auth.interceptor.ts` and `topbar.ts` |
| keycloak-angular v19 breaking: `createAuthGuard` signature | Takes `isAccessAllowed: async (_route, _state) => true` function, not `AuthGuardData` |

#### Compliance Checklist Update

| Item | Status |
|------|--------|
| Web Frontend — Angular scaffold (all feature stubs) | ✅ |
| Design tokens (Nubeero) applied | ✅ |
| HTML prototypes co-located with Angular stubs | ✅ |
| Clean `ng build` (no errors, warnings only in stubs) | ✅ |

#### Build Verification

- `node node_modules/@angular/cli/bin/ng.js build` → **no errors** (NG8113 unused import warnings in stubs only — expected)
- Total Angular feature components: **~80 stub components** across 8 nav groups
- HTML prototypes: **17 files** (3 full Nubeero + 14 generated stubs)

---

### Session 9 — 2026-04-09

**Center CRUD + documentation gap closure: `CenterController` added; Groups & Centers + CoB Scheduler sections in api-reference.html expanded from stubs to full endpoint tables; compliance checklist corrected.**

#### New Java Files

| File | Purpose |
|------|---------|
| `com.cba.group.CenterController` | `POST/GET/PUT/DELETE /api/v1/centers` + `?command=activate` |
| `com.cba.group.CenterService` | Business logic: create, list, get, update, activate, delete |
| `com.cba.group.dto.CenterRequest` | Request record: name, officeId, staffId, activationDate, meetingDayOfWeek |
| `com.cba.group.dto.CenterResponse` | Response record with `CenterResponse.from(Center)` factory |

#### Documentation Updates

| File | Change |
|------|--------|
| `docs/api-reference.html` | Groups section: stub → full endpoint tables for Centers (6) + Groups (6) + CollectionSheets/GLIM (3); CoB section added with endpoint table + implementation notes; Full API Matrix updated with Center rows; nav links moved out of Roadmap |
| `docs/cba-postman-collection-v2.json` | Folder 18 · Groups & Centers: +6 Center CRUD requests → **15 requests total** |
| `cba-log.md` | Architecture compliance checklist updated: Group & Center ✅, CoB ✅ |

#### Build Verification

- `./mvnw clean compile` → **EXIT:0**
- Total controllers: **62** (+1 CenterController)

---

### Session 8 — 2026-04-08

**Gap closure: 11 missing modules — SMS Campaigns, Report Mailing Jobs, Standing Instructions, Search, Two-Factor Auth, Beneficiaries, Client Images, Credit Bureau, Surveys, Accounting Rules, Provisioning Criteria + V19 migration**

#### Added — New Modules (Wave 2–4 Gap Closure)

| Module | Key Files | Endpoints |
|--------|-----------|-----------|
| **SMS Campaigns** | `com.cba.social`: `SmsCampaign`, `SmsMessage`, repos, `SmsCampaignService`, `SmsCampaignController`; `V17__hooks_holidays_campaigns.sql` (table pre-existing) | `GET/POST/PUT/DELETE /api/v1/smscampaigns`; `POST ...?command=activate`; `GET .../messages` |
| **Report Mailing Jobs** | `com.cba.social`: `ReportMailingJob`, `ReportMailingJobRepository`, `ReportMailingJobService`, `ReportMailingJobController` | `GET/POST/PUT/DELETE /api/v1/reportmailingjobs`; `POST ...?command=run` |
| **Standing Instructions** | `com.cba.social`: `StandingInstruction`, `StandingInstructionRepository`, `StandingInstructionService`, `StandingInstructionController` | `GET/POST/PUT/DELETE /api/v1/standinginstructions`; `POST ...?command=disable|enable` |
| **Global Search** | `com.cba.search`: `SearchResult` (record), `SearchService` (JdbcTemplate), `SearchController` | `GET /api/v1/search?query={q}[&resource=CLIENTS|LOANS|SAVINGS|GROUPS]` |
| **Two-Factor Auth** | `com.cba.user`: `TwoFactorToken`, `TwoFactorTokenRepository`, `TwoFactorService`, `TwoFactorController`; `V18__maker_checker_datatables.sql` (table pre-existing) | `POST /api/v1/twofactor/generate`; `POST /api/v1/twofactor/verify`; `GET /api/v1/users/{id}/twofactor` |
| **Beneficiaries** | `com.cba.customer`: `Beneficiary`, `BeneficiaryRepository`, `BeneficiaryService`, `BeneficiaryController` | `GET/POST/PUT/DELETE /api/v1/clients/{customerId}/beneficiaries` |
| **Client Images** | `com.cba.customer`: `ClientImage`, `ClientImageRepository`, `ClientImageService`, `ClientImageController` | `GET/PUT/DELETE /api/v1/clients/{customerId}/images` (PUT = upsert) |
| **Credit Bureau** | `com.cba.system`: `CreditBureauIntegration`, `CreditBureauProductMapping`, repos, `CreditBureauService`, `CreditBureauController` | `GET/POST/PUT/DELETE /api/v1/creditbureaus`; `POST ...?command=activate|deactivate`; `GET/POST/DELETE .../mappings` |
| **Surveys** | `com.cba.system`: `Survey`, `SurveyQuestion`, `SurveyResponse`, `SurveyScorecard`, `SurveyScorecardScore`, repos, `SurveyService`, `SurveyController` | `GET/POST/PUT/DELETE /api/v1/surveys`; `GET /api/v1/surveys/key/{key}`; `GET/POST .../scorecards` |
| **Accounting Rules** | `com.cba.accounting`: `AccountingRule`, `AccountingRuleRepository`, `AccountingRuleService`, `AccountingRuleController`; `V19__accounting_rules_provisioning.sql` | `GET/POST/PUT/DELETE /api/v1/accountingrules` |
| **Provisioning Criteria** | `com.cba.accounting`: `ProvisioningCriteria`, `ProvisioningCriteriaDefinition`, `ProvisioningCriteriaRepository`, `ProvisioningCriteriaService`, `ProvisioningCriteriaController` | `GET/POST/PUT/DELETE /api/v1/provisioningcriteria` |

#### Added — Flyway Migration

| File | Tables |
|------|--------|
| `V19__accounting_rules_provisioning.sql` | `accounting_rules`, `provisioning_criteria`, `provisioning_criteria_definitions` |

#### Build Verification

- `./mvnw clean compile` → **EXIT:0** (zero errors, only JVM compatibility warnings)
- Total Java files: **370** (309 from Session 7 + 61 from Session 8 — 54 new + 7 existing files counted again after edits)
- Total controllers: **61**

#### Documentation Updates (Session 8 docs commit)

| File | Change |
|------|--------|
| `docs/cba-postman-collection-v2.json` | +63 requests, +11 folders → **308 requests, 41 folders** |
| `docs/api-reference.html` | +11 new module sections + Full API Matrix rows for all new endpoints |
| `CLAUDE.md` | Modules 33–43 added with full documentation level |
| `cba-log.md` | This entry |

---

### Session 7 — 2026-04-08

**Full Mifos API parity Wave 2-4: 15 new modules — Charges, Fixed/Recurring Deposits, Shares, Loan Extensions, Floating Rates, Taxes, System Config, Notes, Documents, Hooks, Holidays, Maker-Checker, DataTables, Roles, Client Extensions, Audit Search**

#### Added — New Modules (Wave 2-4)

| Module | Key Files | Endpoints |
|--------|-----------|-----------|
| **Charges** | `com.cba.charge`: `Charge`, `LoanCharge`, repositories, `ChargeService`, `ChargeController`; `V12__charges_module.sql` | `GET/POST/PUT/DELETE /api/v1/charges`; `GET/POST/DELETE /api/v1/loans/{id}/charges`; `POST ...?command=pay` |
| **Fixed Deposits** | `com.cba.deposit`: `FixedDepositProduct`, `FixedDepositAccount`, repos, `FixedDepositService`, `FixedDepositProductController`, `FixedDepositAccountController`; `V13__fixed_deposit_module.sql` | `GET/POST/PUT/DELETE /api/v1/fixeddepositproducts`; `GET/POST /api/v1/fixeddepositaccounts`; command pattern: approve/activate/reject/prematureClose/mature |
| **Recurring Deposits** | `com.cba.deposit`: `RecurringDepositProduct`, `RecurringDepositAccount`, repos, `RecurringDepositService`, controllers | `GET/POST/PUT /api/v1/recurringdepositproducts`; `GET/POST /api/v1/recurringdepositaccounts`; command pattern |
| **Share Products & Accounts** | `com.cba.share`: `ShareProduct`, `ShareAccount`, `ShareAccountTransaction`, repos, `ShareService`, `ShareProductController`, `ShareAccountController`; `V14__share_module.sql` | `GET/POST/PUT /api/v1/shareproducts`; `GET/POST /api/v1/shareaccounts`; `GET/POST .../transactions?type=purchase|redeem` |
| **Loan Guarantors** | `com.cba.loan`: `Guarantor`, `GuarantorRepository`, `LoanExtensionService`, `GuarantorController`; `V15__loan_extensions.sql` | `GET/POST/DELETE /api/v1/loans/{id}/guarantors` |
| **Loan Collateral** | `com.cba.loan`: `Collateral`, `CollateralRepository`, `CollateralController` | `GET/POST/PUT/DELETE /api/v1/loans/{id}/collaterals` |
| **Loan Reschedule** | `com.cba.loan`: `LoanRescheduleRequest`, `LoanRescheduleRepository`, `LoanRescheduleController` | `GET/POST /api/v1/loanreschedule`; `POST /{id}?command=approve|reject` |
| **Loan Re-aging** (Fineract 1.14) | `com.cba.loan`: `LoanReagingRequest`, `LoanReagingRepository`, `LoanReagingController` | `GET/POST /api/v1/loans/{id}/reaging` |
| **Loan Re-amortization** (Fineract 1.14) | `com.cba.loan`: `LoanReamortizationRequest`, `LoanReamortizationRepository`, `LoanReamortizationController` | `GET/POST /api/v1/loans/{id}/reamortization` |
| **Floating Rates** | `com.cba.system`: `FloatingRate`, `FloatingRatePeriod`, repos, `FloatingRateService`, `FloatingRateController`; `V16__system_modules.sql` | `GET/POST/PUT/DELETE /api/v1/floatingrates` |
| **Taxes** | `com.cba.system`: `TaxComponent`, `TaxGroup`, repos, `TaxService`, `TaxController` | `GET/POST/PUT /api/v1/taxes/components`; `GET/POST/PUT /api/v1/taxes/groups` |
| **System Config** | `com.cba.system`: `Code`, `CodeValue`, `GlobalConfiguration`, `Fund`, `SystemPaymentType`, `AccountNumberFormat`, repos, `SystemConfigService`; controllers: `CodesController`, `GlobalConfigController`, `FundsController`, `PaymentTypesController`, `AccountNumberFormatController` | Full CRUD on `/api/v1/codes`, `/api/v1/configurations`, `/api/v1/funds`, `/api/v1/paymenttypes`, `/api/v1/accountnumberformats` |
| **Notes & Documents** | `com.cba.social`: `Note`, `Document` (polymorphic entityType+entityId), `NoteService`, `DocumentService`, `NoteController`, `DocumentController`; `V17__social_modules.sql` | `GET/POST/PUT/DELETE /api/v1/{entityType}/{entityId}/notes`; `GET/POST/DELETE .../documents` |
| **Hooks & Holidays** | `com.cba.social`: `Hook` (events as JSONB), `Holiday`, `HookService`, `HookController`, `HolidayController` | `GET/POST/PUT/DELETE /api/v1/hooks`; `GET/POST/DELETE /api/v1/holidays`; `POST ...?command=activate` |
| **Maker-Checker** | `com.cba.social`: `MakerChecker` (commandAsJson TEXT), `MakerCheckerService`, `MakerCheckerController` | `GET/POST/DELETE /api/v1/makercheckers`; `POST /{id}?command=approve|reject` |
| **DataTables** | `com.cba.social`: `DataTable`, `DataTableColumn`, `DataTableRepository`, `DataTableService`, `DataTableController` | `GET/POST /api/v1/datatables`; `DELETE /api/v1/datatables/{name}` |
| **Roles & Permissions** | `com.cba.role`: `Role`, `Permission`, repos, `RoleService`, `RoleController` | `GET/POST/PUT /api/v1/roles`; `GET/PUT /api/v1/roles/{id}/permissions`; `GET /api/v1/roles/permissions` |
| **Client Identifiers** | `com.cba.customer`: `ClientIdentifier`, `ClientIdentifierRepository`, `ClientExtensionService`, `ClientIdentifierController`; `V18__client_extensions.sql` | `GET/POST/DELETE /api/v1/clients/{id}/identifiers` |
| **Client Addresses** | `com.cba.customer`: `ClientAddress`, `ClientAddressRepository`, `ClientAddressController` | `GET/POST/PUT/DELETE /api/v1/clients/{id}/addresses` |
| **Audit Search** | `com.cba.audit`: `AuditController` — added `GET /api/v1/audits/{id}` and `GET /api/v1/audits/search?entityType=&changedBy=&from=&to=` | Extended from list-only to full search |

#### Added — Flyway Migrations

| Migration | Contents |
|-----------|----------|
| `V12__charges_module.sql` | `charges`, `loan_charges` tables |
| `V13__fixed_deposit_module.sql` | `fixed_deposit_products`, `fixed_deposit_accounts`, `recurring_deposit_products`, `recurring_deposit_accounts` |
| `V14__share_module.sql` | `share_products`, `share_accounts`, `share_account_transactions` |
| `V15__loan_extensions.sql` | `guarantors`, `collaterals`, `loan_reschedule_requests`, `loan_reaging_requests`, `loan_reamortization_requests` |
| `V16__system_modules.sql` | `floating_rates`, `floating_rate_periods`, `tax_components`, `tax_groups`, `tax_group_mappings`, `codes`, `code_values`, `global_configurations`, `funds`, `payment_types`, `account_number_formats` |
| `V17__social_modules.sql` | `notes`, `documents`, `hooks`, `holidays`, `maker_checkers`, `datatables`, `datatable_columns`, `roles`, `permissions`, `role_permissions` |
| `V18__client_extensions.sql` | `client_identifiers`, `client_addresses` |

#### Updated — Documentation

| File | Change |
|------|--------|
| `docs/api-reference.html` | Sidebar + 18 new module sections; Full API Matrix expanded from ~50 to ~160+ endpoints; "Planned" → "Live" for Self Service, Groups, Accounting |
| `docs/cba-postman-collection-v2.json` | 18 new folders (12–29), total: 30 folders / 245 requests |
| `CLAUDE.md` | Banking Module Catalogue sections 18–32 documenting all Wave 2-4 modules |

#### Key architectural decisions

| Decision | Rationale |
|----------|-----------|
| No `@Builder` on JPA entities | Lombok `@Builder` ignores field initializer expressions; `@Getter @Setter @NoArgsConstructor` with `@PrePersist` handles defaults safely |
| `EntityManager.find()` for cross-package lookups | Avoids importing repositories from other bounded contexts; charge service needs Loan, client extensions need Customer |
| Polymorphic Notes/Documents (entityType+entityId) | Single table serves all entity types (clients, loans, accounts, groups); mirrors Mifos pattern |
| JSONB for Hook events (`@JdbcTypeCode(SqlTypes.JSON)`) | Variable-length list of event strings; avoids a join table for a simple list |
| Re-aging/Re-amortization as separate entities | Fineract 1.14.0 feature; request records provide full audit trail and preview capability before committing |
| `commandAsJson TEXT` on MakerChecker | Stores the complete original request JSON; enables exact replay on checker approval without re-serializing |
| `allowMultipleRows` on DataTable | Controls one-to-one (e.g., extended profile) vs one-to-many (e.g., multiple guarantors) extension table semantics |

---

### Session 6 — 2026-04-07

**Full Mifos API parity: 8 new modules + Layer A gap closure (loan repayments, standing orders, payment reversal, batch API, exchange rate GET)**

#### Added — New Modules (Layer B)

| Module | Key Files | Endpoints |
|--------|-----------|-----------|
| **Office & Staff** | `com.cba.office`: `Office`, `Staff`, `OfficeRepository`, `StaffRepository`, `OfficeService`, `OfficeController`, DTOs | `POST/GET/PUT /api/v1/offices`, `POST/GET/PUT/DELETE /api/v1/staff` |
| **User Management** | `com.cba.user`: `PlatformUser`, `PlatformUserRepository`, `UserService`, `UserController`, DTOs; `config/KeycloakAdminConfig` | `POST/GET /api/v1/users`, `POST /api/v1/users/{id}/enable\|disable`, `DELETE /api/v1/users/{id}` |
| **Self Service** | `com.cba.selfservice`: `SelfServiceController`, `SelfServiceFacade`; `V11__self_service_keycloak_link.sql` (`keycloak_id` column on `customers`) | `GET /api/v1/self/userdetails\|accounts\|loans` + account transactions + loan detail |
| **Groups & Centers** | `com.cba.group`: `Center`, `Group`, `GroupMember`, `GlimAccount`, `CollectionSheet`, `CollectionSheetItem`, repositories, `GroupService`, `GroupController`, DTOs | Groups CRUD + activate + members; `POST /api/v1/collectionsheets`; GLIM listing |
| **GL / Accounting** | `com.cba.accounting`: `GlAccount`, `JournalEntry`, `FinancialActivityAccount`, `GlClosure`, repositories, `GlAccountingService`, `GlAccountingController`, `ManualJournalRequest` | `/api/v1/glaccounts`, `/api/v1/journalentries` (post/list/reverse), `/api/v1/glclosures` |
| **Reports** | `com.cba.report`: `Report`, `ReportParameter`, `ReportRepository`, `ReportService`, `ReportController` | `GET /api/v1/reports`, `GET/DELETE /api/v1/reports/{id}`, `GET /api/v1/runreports/{name}?params` |
| **CoB Scheduler** | `com.cba.cob`: `InterestAccrualJob`, `ArrearsClassificationJob`, `StandingOrderExecutionJob`, `QuartzJobBridge`, `CobSchedulerConfig`, `CobJobHistory`, `CobJobHistoryRepository`, `CobController` | `GET /api/v1/jobs`, `POST /api/v1/jobs/{name}/run`, `GET /api/v1/jobs/{name}/history` |
| **Batch API** | `com.cba.batch`: `BatchController`, `BatchApiService`, DTOs `BatchRequest`/`BatchResponse` | `POST /api/v1/batches?enclosingTransaction=false` |

#### Added — Layer A Gap Closure

| Gap | Files Changed |
|-----|---------------|
| Loan repayments (`POST /{id}/repayments`) | `LoanService.makeRepayment()`, `LoanController`, `LoanRepaymentRequest/Response` |
| Loan write-off (`POST /{id}/write-off`) | `LoanService.writeOffLoan()`, `Loan.writtenOffOn/writeOffReason`, `WriteOffRequest` |
| Standing orders | `StandingOrder`, `StandingOrderRepository`, `PaymentService` (3 methods), `PaymentController`, `StandingOrderRequest/Response` |
| Payment reversal (`POST /{id}/reverse`) | `Payment.reversalOf/Reason/At` fields, `PaymentService.reversePayment()`, `PaymentController`, `ReversePaymentRequest` |
| Exchange rate GET by pair | `ExchangeRateService.getRateResponse()`, `ExchangeRateController GET /{from}/{to}` |

#### Added — Flyway Migrations

| Migration | Contents |
|-----------|----------|
| `V6__offices_staff_users.sql` | `offices`, `staff`, `platform_users`, `user_roles`, `self_service_user_clients` |
| `V7__groups_centers.sql` | `centers`, `groups`, `group_members`, `glim_accounts`, `collection_sheets`, `collection_sheet_items` |
| `V8__gl_accounting.sql` | `gl_accounts`, `financial_activity_accounts`, `journal_entries`, `gl_closures`; 15 GL accounts + 7 activity mappings |
| `V9__reports.sql` | `reports`, `report_parameters`; 7 seed reports |
| `V10__batch_layer_a_fixes.sql` | `standing_orders`, ALTER `payments`/`loans`; Spring Batch + Quartz schemas; `cob_job_history` |
| `V11__self_service_keycloak_link.sql` | `keycloak_id VARCHAR(100)` column + unique index on `customers` |

#### Updated

- `LoanRepository` — added `findByStatusIn(List<LoanStatus>)`
- `AccountRepository` — added `findByStatus(AccountStatus, Pageable)`
- `CustomerRepository` — added `findByKeycloakId(String)`
- `Customer.java` — added `keycloakId` field
- `application.yml` — added Spring Batch (`initialize-schema: never`), Quartz (`job-store-type: jdbc`), Keycloak admin config
- `CLAUDE.md` — documented all 8 new modules (sections 10–17)

#### Key architectural decisions

| Decision | Rationale |
|----------|-----------|
| `RestTemplate` self-calls for Batch API | `MockHttpServletRequest` is test-scoped; self-calls are the correct production pattern |
| Flyway-managed Batch + Quartz schemas | `spring.batch.jdbc.initialize-schema: never` — Flyway owns the schema invariant |
| JWT 404 not 403 on ownership mismatch | Prevents resource enumeration; returning 403 confirms existence |
| `QuartzJobBridge` bean lookup by name | Allows Quartz triggers to launch any Spring Batch job without compile-time coupling |
| Keycloak Admin Client dual-write | User created in Keycloak first (source of truth for auth), then mirrored locally for office/staff links |
| Report SQL `SELECT`-only guard | Blocks DML keywords + injection chars in param values; prevents destructive report execution |

---

### Session 5 — 2026-04-07

**API documentation overhaul: Mifos-style HTML reference, dual Postman collections, GitHub Pages deployment**

#### Added
- `docs/api-reference.html` — 1,120-line self-contained HTML API reference modelled on Mifos `apiLive.htm`. Covers: Introduction, Authentication (overview + Basic + OAuth2/Keycloak), General Options, Request Conventions (dates/numbers/UUIDs), Field Descriptions, Error Handling (all error codes), Batch API (with dependent request patterns), Payment Application Logic (EMI formula, repayment allocation, cross-currency flow), all 10 backend modules (Customers → Audit), Self Service / Groups / GL sections (marked Planned), and a Full API Matrix (55+ implemented endpoints). Dark sidebar navigation with scroll-spy; collapsible endpoint details blocks; responsive layout using Nubeero design tokens.
- `docs/cba-postman-collection-v2.json` — Enriched 391KB Postman collection (v2). 11 folders restructured in Mifos API divisions: 00·Authentication → 01·Customers → 02·Accounts → 03·Loans → 04·Payments → 05·Products → 06·Teller → 07·Open Banking → 08·Exchange Rates → 09·Batch API → 10·Audit → 11·System. Every folder has a full description explaining module purpose, status flows, key rules, and Mifos reference context. Pre-request test script on token endpoint auto-sets `access_token` collection variable. 8 language code samples per request (cURL, JavaScript/fetch, Python/requests, Java/OkHttp, C#/HttpClient, PHP/cURL, Go/net/http, Ruby/Net::HTTP).
- `docs/cba-postman-collection-coming-soon.json` — 63KB separate Postman collection for planned/unimplemented endpoints. 8 modules: Self Service, Group & Center Management, GL/Accounting, User Management, Office Management, Notifications, Close of Business Scheduler, Reports, Savings Extended. All requests prefixed `[COMING SOON]` and marked NOT YET IMPLEMENTED. Modelled directly on Mifos API patterns.
- `.github/workflows/pages.yml` — GitHub Pages deployment workflow. Triggers on push to `main` when `docs/**` changes. Builds `_site/` with `api-reference.html` as `index.html`, both Postman collections, and a `collections.html` download index. Deploys to GitHub Pages using `actions/deploy-pages@v4`.

#### Updated
- `CLAUDE.md` — Added **Mifos API Live Documentation — Section Index** table with all 13 section URLs and their purpose. Added **API Documentation & Postman Collections (Session 5)** table in Reference Files section with GitHub Pages URL.

#### Key decisions
- **Two separate collections** — implemented endpoints (v2) and planned stubs (coming-soon) kept separate so integrators import only what is live
- **GitHub Pages from `/docs` folder** — `api-reference.html` is the `index.html` so `https://razormvp.github.io/cba-platform/` serves the full reference directly
- **Pages workflow path filter** — only triggers when `docs/**` changes, not on every backend push, to avoid unnecessary deploys
- **No external dependencies in HTML** — fully self-contained; works offline without any CDN

#### Architecture compliance additions
| Requirement | Status |
|-------------|--------|
| Mifos-style HTML API reference | ✅ `docs/api-reference.html` |
| Enriched Postman collection (Mifos divisions) | ✅ `docs/cba-postman-collection-v2.json` |
| Coming Soon Postman collection | ✅ `docs/cba-postman-collection-coming-soon.json` |
| GitHub Pages deployment | ✅ `.github/workflows/pages.yml` |
| Mifos reference URLs in CLAUDE.md | ✅ 13 section URLs added |

---

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
| Group & Center module | ✅ | `com.cba.group` — Center + Group CRUD, collection sheets, GLIM; `CenterController` + `GroupController` |
| CoB batch processing | ✅ | `com.cba.cob` — Spring Batch + Quartz; 3 nightly jobs; `CobController` at `/api/v1/jobs` |
| Docker Compose | ❌ | Not built |
| Kubernetes manifests | ❌ | Not built |
| Keycloak realm JSON | ❌ | Not built |
| Angular web portal | ❌ | Not built |
| Flutter mobile app | ❌ | Not built |
