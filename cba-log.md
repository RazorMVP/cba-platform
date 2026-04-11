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
| **Product Module** | `product/LoanProduct.java`, `DepositProduct.java`, `AllowAttributeOverrides.java`, `ChargeDefinitionRepository.java`, `ProductService`, `ProductController`, DTOs (rewritten); `V20__product_mifos_parity.sql`; `loan_product_charges`, `deposit_product_charges` join tables | Full Mifos parity: 30+ new columns on loan_products, 20+ on deposit_products; @Embedded AllowAttributeOverrides (8 booleans); @ManyToOne GL account linkages (8 per product); @ManyToMany charges join tables; nested GlAccountRef response; range validation; ADMIN writes |
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
| **Web Frontend — Angular (Operations + Products)** | Dashboard, Customers, Accounts, Loans, Loan Detail, Customer Detail, Loan Products, Deposit Products — all wired to backend API with view/edit CRUD | Phase 2 (partial) |
| **Mobile Frontend — Flutter** | Customer mobile app (auth, dashboard, accounts, loans, payments, profile) | Phase 3 |

---

## Change History

### Session 22 — 2026-04-11

**Comprehensive UI audit — resolved global CSS custom property gap that made all feature CTAs and cards invisible in the Vercel deployment.**

#### New/Updated Files
| File | Change |
|------|--------|
| `web/src/styles.scss` | FIXED — added `:root {}` CSS custom property bridge: `--color-primary`, `--color-text`, `--color-muted`, `--bg-card`, `--color-error` |
| `web/src/app/features/system/floating-rates.html` | FIXED — removed superfluous `?.` optional chaining on `ratePeriods` (always-defined array); fixes Angular template type warning |

#### Root Cause Analysis
All 43 feature-level SCSS files reference CSS custom properties (`var(--bg-card)`, `var(--color-primary)`, etc.) but the tokens file only defined SCSS compile-time variables (`$color-bg-card`). No `:root {}` block translated these to runtime CSS custom properties, so:
- `.btn-primary { background: var(--color-primary) }` → resolved to nothing → invisible buttons (incl. "New Transfer" CTA)
- `.card { background: var(--bg-card) }` → resolved to nothing → transparent cards
- All `color: var(--color-text)` text → unresolvable on dark content areas

The layout components (shell, sidebar, topbar) were unaffected because they use SCSS variables directly.

#### Fix Applied
Single `:root {}` block in `styles.scss` bridging SCSS → CSS custom properties using interpolation (`--color-primary: #{$color-primary-800}`). This resolves all 5 undefined properties globally across all 43 affected components in one edit.

#### Build Verification
- `ng build --configuration=production` — zero errors, 0 new warnings (pre-existing budget warnings unchanged)
- Floating-rates template warning resolved (optional chaining removed from required array)

#### Compliance Checklist Update
- Design token bridge: ✅ Fixed — all CSS custom properties now resolve correctly in production

---

### Session 21 — 2026-04-11

**All 16 stub Angular components fully implemented — admin, groups, open-banking, and system modules now have complete API-wired UIs.**

#### New/Updated Files
| File | Change |
|------|--------|
| `web/src/app/features/admin/admin.service.ts` | NEW — service covering Users, Roles, Offices, Hooks, MakerChecker, TPP |
| `web/src/app/features/admin/users.ts/.html/.scss` | BUILT — create modal, enable/disable toggle, delete confirm |
| `web/src/app/features/admin/roles.ts/.html/.scss` | BUILT — permissions matrix modal grouped by category, select-all-in-group |
| `web/src/app/features/admin/offices.ts/.html/.scss` | BUILT — hierarchy display, parent office dropdown |
| `web/src/app/features/admin/hooks.ts/.html/.scss` | BUILT — WEB/SMS type chips, event selection chips |
| `web/src/app/features/admin/maker-checker.ts/.html/.scss` | BUILT — status tabs, approve/reject PENDING entries |
| `web/src/app/features/admin/open-banking.ts/.html/.scss` | BUILT — TPP registry (platform-level PSD2 view) |
| `web/src/app/features/groups/groups.service.ts` | NEW — Groups, Centers, CollectionSheet, GLIM interfaces + API calls |
| `web/src/app/features/groups/groups-list.ts/.html/.scss` | BUILT — status filter tabs, create modal |
| `web/src/app/features/groups/group-detail/group-detail.ts/.html/.scss` | BUILT — 3 tabs: Members, Collection Sheet, GLIM Accounts |
| `web/src/app/features/groups/centers-list.ts/.html/.scss` | BUILT — same pattern as groups-list |
| `web/src/app/features/groups/center-detail/center-detail.ts/.html/.scss` | BUILT — 2 tabs: Groups, All Members |
| `web/src/app/features/open-banking/open-banking.service.ts` | NEW — Consent service (list, get, authorise, revoke) |
| `web/src/app/features/open-banking/open-banking-list.ts/.html/.scss` | BUILT — type filter tabs + status dropdown, scope chips |
| `web/src/app/features/open-banking/consent-detail/consent-detail.ts/.html/.scss` | BUILT — full authorisation flow (Authorise/Revoke), two-column detail grid |
| `web/src/app/features/system/system.service.ts` | NEW — Codes, GlobalConfig, FloatingRates, Taxes interfaces + API calls |
| `web/src/app/features/system/codes.ts/.html/.scss` | BUILT — inline accordion, load-on-expand, inline add/edit form |
| `web/src/app/features/system/global-config.ts/.html/.scss` | BUILT — inline row edit, type-aware inputs (string/number/boolean), enabled toggle |
| `web/src/app/features/system/floating-rates.ts/.html/.scss` | BUILT — accordion with rate periods, dynamic period rows in modal |
| `web/src/app/features/system/taxes.ts/.html/.scss` | BUILT — two-tab: Tax Components CRUD + Tax Groups with component bundles |
| `CLAUDE.md` | UPDATED — component map: all 16 stubs → ✅ Built |

#### Key Patterns / Decisions
- Admin `open-banking` implemented as a **TPP Management** view (platform-level PSD2 registry), distinct from the operational consents list — maps to FCA/PSD2 TPP registration workflows
- Codes implemented with **inline accordion** (no route change) — consistent with Mifos convention, keeps admin in context for small value sets
- Consent detail exposes full **authorisation flow**: Authorise (green) or Revoke (red) conditionally shown based on consent status
- Maker-checker shows **metadata only** (entity type, action, made-by, timestamp) — no payload content displayed per requirement; approve/reject only for PENDING status
- GlobalConfig uses **type-aware inline editing**: `valueType()` helper dispatches to text/number/boolean-select input without separate edit forms
- Taxes uses a **two-tab pattern** (Components / Groups) in one component — avoids route complexity for co-located admin concerns
- FloatingRates and Tax Groups both use a **dynamic rows pattern** — add/remove rows before submitting parent+children as a single request

#### Build Verification
- `ng build --configuration=production` — zero errors, all warnings pre-existing
- Bug fixed: all new templates incorrectly bound `[status]="..."` to `StatusBadgeComponent`, which only accepts `[label]` and `[variant]` — fixed across all 11 affected components before commit
- `statusVariant()` helpers added to `groups-list`, `centers-list`, `group-detail`, `center-detail` to supply variant values
- TypeScript: no unused-import errors (FloatingRatePeriod removed after IDE diagnostic)
- All components follow `inject()` pattern (no constructor injection)
- All use `@if`/`@for` control flow (not `*ngIf`/`*ngFor`)
- SCSS: all files use `@use 'assets/styles/tokens' as *`

#### Compliance Checklist Update
- Angular component map: 0 stubs remaining — all 48 components ✅ Built

---

### Session 20 — 2026-04-11

**Full design system exported to Figma — all 38 built components as 1440×900 frames across 10 pages.**

#### New/Updated Files
| File | Change |
|------|--------|
| Figma: `RqbeDCCJiD36eettFSsKZn` | NEW — CoreBanking-Nubeero Figma file fully populated via MCP |

#### Key Patterns / Decisions
- Stitch MCP unavailable (OAuth token expired); Figma used as design archive destination instead
- File built from scratch: single Design Tokens page → 10 section pages + 38 frames
- Design variable collection `Nubeero / CBA` created with 36 color, spacing, and radius tokens
- All frames are 1440×900, share the same sidebar (260px) + topbar (64px) shell — mirrors Angular layout contract
- Shared Components page isolates reusable atoms: Sidebar, Topbar, KPI Card, 7 Status Badge variants, Table Header + Row

#### Figma Page Inventory
| Page | Frames |
|------|--------|
| 🔧 Shared Components | Sidebar, Topbar, KPI Card, 7× Status Badges, Table Header + Row |
| 📊 Dashboard | Full KPIs, transaction table, loan portfolio bars, KYC queue |
| 👥 Customers | List (10-row, KYC filters) · Detail (5-tab, KYC timeline) |
| 💳 Accounts | List (type filters) · Detail (overview + transactions) |
| 💸 Payments | List (summary cards + history) · Detail (status band, route card, audit trail) |
| 🏦 Tellers | List · Detail (cashiers + session management + settlement) |
| 🏷️ Loans | List (pipeline + sliding panel) · Detail (5-tab, repayment schedule) |
| 📦 Products | 9 frames — all 5 product types (list + detail each) |
| 📈 Accounting | GL Accounts · Journal Entries (T-ledger) · Provisioning Criteria (IFRS 9) |
| 📋 Reports | Reports + Run Panel · CoB Scheduler · Report Mailing Jobs |

#### Build Verification
- All 38 frames confirmed via `use_figma` inspection (frame count per page validated)
- Nubeero token palette applied: `#040609` shell, `#0a1628` sidebar, `#1e2833` CTAs, `Instrument Sans` typography

#### Compliance Checklist Update
- Design archive: ✅ Complete (all 32 built Angular components have Figma prototypes)

---

### Session 19 — 2026-04-10

**Vercel deployment pipeline unblocked + demo auth bypass mode.**

#### New/Updated Files
| File | Change |
|------|--------|
| `.github/workflows/web-ci.yml` | Promoted Vercel env vars to job level; `.vercel/` added to web/.gitignore |
| `web/angular.json` | Added missing `fileReplacements` to production config — was baking in `localhost:8180` instead of prod Keycloak URL |
| `web/src/environments/environment.ts` | Added `authBypass: false` |
| `web/src/environments/environment.prod.ts` | Added `authBypass` (reads `NG_APP_AUTH_BYPASS` env var); Keycloak/API URLs read from `NG_APP_*` env vars |
| `web/src/app/core/auth/demo-keycloak.ts` | NEW — mock Keycloak for bypass mode (authenticated=true, Demo Admin user) |
| `web/src/app/app.config.ts` | Conditionally provides real Keycloak or mock based on `authBypass` flag |
| `web/src/app/core/auth/auth.guard.ts` | Returns `true` immediately in bypass mode |
| `web/src/app/core/auth/auth.interceptor.ts` | Skips Bearer header in bypass mode |
| Multiple `.ts`/`.html` files | Fixed all ESLint warnings: removed empty `error:()=>{}` callbacks, typed `any`, migrated `*ngIf`/`*ngFor` → `@if`/`@for` |

#### Key Patterns / Decisions
- `NG_APP_AUTH_BYPASS=true` is set as a Vercel env var; esbuild bakes it into the bundle at build time (not runtime)
- `fileReplacements` was the root cause of localhost:8180 showing in prod — it was never set in `angular.json`
- Bypass mode provides `DEMO_KEYCLOAK` mock directly to the DI container so all three inject sites (interceptor, guard, topbar) work without a real Keycloak

#### Build Verification
- `ng build --configuration=production` → 0 errors
- `ng lint` → All files pass linting
- CI pipeline: Lint ✅ Security ✅ Production Build ✅ (Vercel deploy green)

### Session 18 — 2026-04-10

**Angular Reports UI — full ReportsListComponent + CobSchedulerComponent + ReportMailingComponent with ReportService.**

**Files changed (10):**
- `web/src/app/features/reports/report.service.ts` — NEW: `Report`, `ReportParameter`, `CobJob`, `CobJobHistory`, `ReportMailingJob` interfaces; all backend methods (listReports, runReport, listJobs, runJob, getJobHistory, listMailingJobs, createMailingJob, updateMailingJob, deleteMailingJob, runMailingJob)
- `web/src/app/features/reports/reports-list.ts` — REWRITTEN: dynamic param form from `ReportParameter` array; `runReport()` builds `Record<string,string>` params; schema-on-read results table (columns from first row keys); CSV export; create report modal with SQL textarea; delete confirmation
- `web/src/app/features/reports/reports-list.html` — REWRITTEN: report table with category chips + param count + core badge; Run button; run modal with dynamic param grid + results table + Export CSV; create + delete modals
- `web/src/app/features/reports/reports-list.scss` — REWRITTEN: purple report icon, category/param chips, results table with sticky headers, scrollable results area
- `web/src/app/features/reports/cob-scheduler.ts` — REWRITTEN: per-job `runningJobs` Set; `setTimeout` re-fetch after trigger; inline history panel on select; `duration()` helper (ms/s/m)
- `web/src/app/features/reports/cob-scheduler.html` — REWRITTEN: job card grid (3 stats: last run / status / next run); inline history table on card click; Run Now button with spinner; status dots
- `web/src/app/features/reports/cob-scheduler.scss` — REWRITTEN: job-card grid, running spinner animation on icon, status dots per status, inline history panel with blue accent
- `web/src/app/features/reports/report-mailing.ts` — REWRITTEN: RRULE preset dropdown (Daily/Weekly/Monthly/Custom); per-job run state Set; create/edit/delete modals; `rruleLabel()` helper
- `web/src/app/features/reports/report-mailing.html` — REWRITTEN: mailing jobs table with report/schedule/output type chips; send button; create/edit modal with schedule presets
- `web/src/app/features/reports/report-mailing.scss` — REWRITTEN: per-output-type chips (CSV=green/PDF=red/XLS=dark-green), email icon, rrule chip

**Key patterns:**
- Schema-on-read results table: `resultCols = Object.keys(rows[0])` — column headers unknown at design time
- Dynamic param form: `paramEntries` built from `report.reportParameters` array — each entry renders correct input type (date/number/text)
- CSV export via Blob + URL.createObjectURL — no server round-trip
- CoB history inline inside job card (expand on click) vs separate route — fits 3-job CoB pattern

**Build:** Clean (`Output location: dist/cba-web`, zero TS errors)
**CLAUDE.md:** Updated Angular Component Map — added ReportsListComponent, CobSchedulerComponent, ReportMailingComponent; stub count ~51 → ~48

---

### Session 17 — 2026-04-10

**Angular Accounting / GL UI — full GlAccountsComponent + JournalEntriesComponent + ProvisioningComponent with AccountingService.**

**Files changed (10):**
- `web/src/app/features/accounting/accounting.service.ts` — NEW: `GlAccount`, `JournalEntry`, `ManualJournalRequest`, `GlClosure`, `ProvisioningCriteria`, `ProvisioningDefinition` interfaces; all CRUD + command methods for GL accounts, journal entries, GL closures, provisioning criteria
- `web/src/app/features/accounting/gl-accounts.ts` — REWRITTEN: client-side type-filter tabs (ALL/ASSET/LIABILITY/EQUITY/INCOME/EXPENSE); search by GL code/name; show-disabled toggle; create/edit modal; enable/disable lifecycle toggle per row
- `web/src/app/features/accounting/gl-accounts.html` — REWRITTEN: type-tab filter bar; data table with account-type icon cells; manual-entries lock icon; action buttons; create/edit modal
- `web/src/app/features/accounting/gl-accounts.scss` — REWRITTEN: type-tabs component, per-type icon colours (asset=green/liability=amber/equity=blue/expense=grey), row-disabled opacity
- `web/src/app/features/accounting/journal-entries.ts` — REWRITTEN: `JournalEntryGroup` client-side grouping by `transactionId`; date range + type + GL code filters; reverse modal; create manual entry modal with dynamic debit/credit lines; `isBalanced` guard (debits === credits > 0)
- `web/src/app/features/accounting/journal-entries.html` — REWRITTEN: date-range filter bar; grouped ledger display (T-ledger format per transaction); reverse button for USER/non-reversed entries; manual entry modal with live balance bar, add/remove debit & credit lines
- `web/src/app/features/accounting/journal-entries.scss` — REWRITTEN: `entry-group` cards with ledger table; `amount--debit` (red) / `amount--credit` (green); `balance-bar` with ok/err states; scrollable modal with dynamic line sections
- `web/src/app/features/accounting/provisioning.ts` — REWRITTEN: 5 default IFRS 9 age bands pre-seeded; create/edit/delete modals; GL account dropdowns filtered by type (LIABILITY for liability, EXPENSE for expense); `glAccountLabel()` helper
- `web/src/app/features/accounting/provisioning.html` — REWRITTEN: criteria cards with bands table; category badges coloured by severity; create/edit modal with scrollable age-band editor; delete confirmation modal
- `web/src/app/features/accounting/provisioning.scss` — REWRITTEN: `criteria-card`, per-category badge colours (standard=green/watch=amber/sub_standard=orange/doubtful=red/loss=deep-red), `defs-table` inline editor

**Key patterns:**
- `JournalEntryGroup` computed client-side from flat entry list — groups by `transactionId`, sums debit/credit totals
- `isBalanced` getter gates the Post Entry button: `debitTotal > 0 && debitTotal === creditTotal`
- GL account dropdowns in provisioning filtered by accountType — liability accounts for liability column, expense for expense
- Default IFRS 9 bands pre-seeded for new criteria (standard 1% → loss 100%)

**Build:** Clean (`Output location: dist/cba-web`, zero TS errors)
**CLAUDE.md:** Updated Angular Component Map — added `GlAccountsComponent`, `JournalEntriesComponent`, `ProvisioningComponent`; stub count ~54 → ~51

---

### Session 16 — 2026-04-10

**Angular Teller Sessions UI — full TellerListComponent + TellerDetailComponent with TellerService: teller lifecycle, cashier assignment, session open/close, cash-in/cash-out, and settlement reconciliation.**

**Files changed (7):**
- `web/src/app/features/operations/teller/teller.service.ts` — NEW: `Teller`, `Cashier`, `TellerSession`, `CashTransaction` interfaces; full lifecycle methods (create, activate, close, assignCashier, openSession, closeSession, recordTransaction)
- `web/src/app/features/operations/teller/teller-list.ts` — REWRITTEN: loads all tellers; client-side filter by name/branchCode/status; create teller modal with startDate defaulting to today
- `web/src/app/features/operations/teller/teller-list.html` — REWRITTEN: page header, search+status filter bar, data table with chevron rows, create teller modal
- `web/src/app/features/operations/teller/teller-list.scss` — REWRITTEN: compact Nubeero list styles; teller-icon, modal styles
- `web/src/app/features/operations/teller/teller-detail/teller-detail.ts` — REWRITTEN: `ActiveTab = 'overview' | 'cashiers' | 'sessions'`; `ModalType` union (6 modals); cashiers loaded eagerly; `selectSession()` expand/collapse with lazy txn load; `get openSession()` finder; `get sessionRunningBalance()` computed from transactions; `openSettleModal()` pre-fills actualCash; `submitOpenSession()` auto-expands session + switches tab
- `web/src/app/features/operations/teller/teller-detail/teller-detail.html` — REWRITTEN: teller header with lifecycle buttons; green open-session-banner with running balance; tab bar with count badges; sessions as expandable `session-card` divs with left-border; 6 modals (activate, close-teller, assign-cashier, open-session, cash-txn, settle)
- `web/src/app/features/operations/teller/teller-detail/teller-detail.scss` — REWRITTEN: `session-card--open` (green border); `settlement-row--surplus/deficit`; `txn-badge--in/out`; `amount--in/out` colour classes

**Key patterns:**
- `selectedSession` expand/collapse with lazy `sessionTxns` load (avoids deep routing for single-day workflow)
- Settlement live difference preview: `difference = settleActualCash - sessionRunningBalance`; coloured surplus/deficit
- Cashiers loaded eagerly on init (needed before Open Session modal opens)
- `UNIQUE (cashier_id, session_date)` constraint; error message explains "session may already be open today"

**Build:** Clean (`Output location: dist/cba-web`, zero TS errors)
**CLAUDE.md:** Updated Angular Component Map — added `TellerListComponent` + `TellerDetailComponent`; stub count ~56 → ~54

---

### Session 15 — 2026-04-10

**Angular Payments UI — full PaymentsListComponent + PaymentDetailComponent with PaymentService, 3-step transfer wizard, standing order modal, FX cross-currency display, and payment reversal.**

#### New/Updated Files
| File | Change |
|------|--------|
| `web/src/app/features/operations/payments/payment.service.ts` | New — `Payment`, `TransferRequest`, `StandingOrder`, `StandingOrderRequest` interfaces; `get`, `getAccountPayments`, `transfer`, `reverse`, `listStandingOrders`, `createStandingOrder`, `cancelStandingOrder` methods |
| `web/src/app/features/operations/payments/payments-list.ts` | Full implementation — account context picker with debounced search; paginated payment table; 3-step transfer wizard (`TransferStep = 1|2|3`); standing order form; `isCredit()` helper for credit/debit display |
| `web/src/app/features/operations/payments/payments-list.html` | Full template — context picker with autocomplete, filter bar, paginated table with FX badge, 3-step transfer wizard modal (account selection → amount → confirm), standing order modal |
| `web/src/app/features/operations/payments/payments-list.scss` | Full Nubeero styles — context picker, autocomplete dropdown, type-icon cells, FX badge, wizard step indicators, confirm block, account-pair flex layout |
| `web/src/app/features/operations/payments/payment-detail/payment-detail.ts` | Full implementation — status-coloured header band, `canReverse` getter, reverse modal with reason field |
| `web/src/app/features/operations/payments/payment-detail/payment-detail.html` | Full template — status-band (left-border colour per status), FX info row, transfer route card with account deep-links, payment details card, FX details card (conditional), reverse modal |
| `web/src/app/features/operations/payments/payment-detail/payment-detail.scss` | Full Nubeero styles — status-band left-border colours, amount display, FX info chip, transfer route layout, detail grid |
| `CLAUDE.md` | Added `PaymentsListComponent` + `PaymentDetailComponent` to Angular Component Map; stub count `~58 → ~56` |
| `cba-log.md` | This entry |

#### Key Patterns / Decisions
- **No global `GET /payments` endpoint** — backend scopes payments to an account. List page uses an account context picker (debounced search) to load `GET /payments/accounts/{id}`; "no account selected" state shows a prompt rather than an empty table
- **3-step transfer wizard**: Step 1 = source/destination account autocomplete pickers; Step 2 = amount + description with cross-currency warning and balance-exceeded warning; Step 3 = read-only confirm block. `transferStep1Valid` and `transferStep2Valid` guards prevent advancing with incomplete data
- **FX display**: `payment.crossCurrency` boolean gates the FX info row in the header and the FX details card. Exchange rate shown as `1 SRC = N DST` using 4-6 decimal precision
- **Reversal**: Only `COMPLETED` payments expose the Reverse button (`canReverse` getter). Reason field required; error message clarifies the constraint
- **Status left-border**: `payment-header--completed/pending/failed/reversed` CSS classes give instant visual status without needing to read the badge

#### Build Verification
- `npx ng build --configuration=production` → **0 errors**

#### Compliance Checklist Update
- Account numbers in payment route are deep-linked to Account Detail — no dead-end navigation
- FX rate displayed to 4-6 decimal places for audit precision

---

### Session 14 — 2026-04-10

**Angular Account Detail — full AccountDetailComponent built with overview/transactions tabs, 5 teller/status action modals, paginated transaction history, and ApiService extended with `postParams`/`putParams` helper methods.**

#### New/Updated Files
| File | Change |
|------|--------|
| `web/src/app/core/api/api.service.ts` | Added `postParams<T>()` and `putParams<T>()` — POST/PUT with `@RequestParam` backends (no body) |
| `web/src/app/features/operations/accounts/account.service.ts` | Added `Transaction` interface; fixed `freeze`/`close` to use `putParams`; added `unfreeze`, `getTransactions`, `deposit`, `withdraw` |
| `web/src/app/features/operations/accounts/account-detail/account-detail.ts` | Full implementation: `ActiveTab` union, `ModalType` union, lazy transaction pagination, 5 action methods, display helpers |
| `web/src/app/features/operations/accounts/account-detail/account-detail.html` | Full template: header card (balance + type icon + status + action buttons), tab bar, overview grid (account details + customer cards), paginated transactions table, 5 modals (freeze/unfreeze/close/deposit/withdraw) |
| `web/src/app/features/operations/accounts/account-detail/account-detail.scss` | Full Nubeero styles: acct-header three-zone flex, tab bar, overview grid, data-table with credit/debit colour classes, pagination, modal backdrop + slide-up animation, form fields with currency prefix group, spinner |
| `CLAUDE.md` | Added `AccountDetailComponent` to Angular Component Map; stub count `~59 → ~58` |
| `cba-log.md` | This entry |

#### Key Patterns / Decisions
- `ModalType = 'freeze' | 'unfreeze' | 'close' | 'deposit' | 'withdraw' | null` — single `activeModal` property drives all 5 modals; `doTellerAction()` branches on `activeModal === 'deposit'` vs `'withdraw'`, keeping template DRY
- Transactions tab is lazy-loaded: `txnLoaded` flag prevents re-fetch on tab switch; invalidated after every teller action so balance + history stay in sync
- `putParams` added to ApiService for `PUT ?status=xxx` pattern used by freeze/unfreeze/close; `postParams` for `POST ?amount=xxx` deposit/withdraw pattern — both keep empty body `{}` to satisfy Spring's `@RequestParam` binding

#### Build Verification
- `npx ng build --configuration=production` → **0 errors**; warnings are pre-existing unused-import stubs and SCSS budget (mirrors existing loan-detail warning)

#### Compliance Checklist Update
- No new PII surfaces; account numbers displayed in full (no masking needed at this layer — teller access)

---

### Session 13 — 2026-04-10

**Angular Products UI — complete Products group: Fixed Deposits, Recurring Deposits, Share Products list + detail pages built and pushed (commit `ac09073`).**

#### New/Updated Angular Files

| File | Change |
|------|--------|
| `features/products/product.service.ts` | **Extended** — 3 new entity interfaces (`FixedDepositProduct`, `RecurringDepositProduct`, `ShareProduct`), 3 new request interfaces, 15 new service methods (5 per product type) |
| `features/products/fixed-deposits/fixed-deposits-list.ts` | **Rewritten** — client-side debounced search, active-only toggle, pagination, `compoundingLabels`, `termTypeLabels` |
| `features/products/fixed-deposits/fixed-deposits-list.html` | **Rewritten** — table with name, code, currency, rate, term range, calc type, status badge, pagination |
| `features/products/fixed-deposits/fixed-deposits-list.scss` | **Rewritten** — full Nubeero: page-header, toolbar, search-box, data-table, shimmer skeleton, empty-state, pagination |
| `features/products/fixed-deposits/fixed-deposit-detail/fixed-deposit-detail.ts` | **Rewritten** — 4 section tabs (`core/rates/term/penalty`), view/edit toggle, `enterEditMode(blank)`, create/edit/deactivate flow |
| `features/products/fixed-deposits/fixed-deposit-detail/fixed-deposit-detail.html` | **Rewritten** — back link, skeleton, error state; view: product-header + 3 cards (Interest, Term & Deposit, Pre-closure Penalty); edit: 4-tab form; deactivate modal |
| `features/products/fixed-deposits/fixed-deposit-detail/fixed-deposit-detail.scss` | **Rewritten** — full pattern: back-link, skeleton, product-header, sections-grid, cards, section-tabs, edit-card, form, modal |
| `features/products/recurring-deposits/recurring-deposits-list.ts` | **Rewritten** — same as FD list + `frequencyLabels`, `RecurringDepositProduct` interface |
| `features/products/recurring-deposits/recurring-deposits-list.html` | **Rewritten** — adds Frequency column to table |
| `features/products/recurring-deposits/recurring-deposits-list.scss` | **Rewritten** — identical pattern to FD list SCSS |
| `features/products/recurring-deposits/recurring-deposit-detail/recurring-deposit-detail.ts` | **Rewritten** — 5 section tabs (`core/rates/frequency/term/penalty`); `depositFrequencies` array; `mandatoryRecommendedDepositAmount` in form |
| `features/products/recurring-deposits/recurring-deposit-detail/recurring-deposit-detail.html` | **Rewritten** — adds Frequency meta-chip to header; Deposit Schedule view card; Frequency edit tab with frequency select + mandatory amount input |
| `features/products/recurring-deposits/recurring-deposit-detail/recurring-deposit-detail.scss` | **Rewritten** — full Nubeero pattern |
| `features/products/shares/shares-list.ts` | **Rewritten** — `ShareProduct` interface; unit price, total shares, shares issued columns |
| `features/products/shares/shares-list.html` | **Rewritten** — table: name, code, currency, unit price, total shares, shares issued, status; `—` fallback for optional numeric fields |
| `features/products/shares/shares-list.scss` | **Rewritten** — full Nubeero pattern |
| `features/products/shares/share-detail/share-detail.ts` | **Rewritten** — 3 section tabs (`core/shares/lockin`); `periodTypes`, `periodTypeLabels`; create/edit/deactivate |
| `features/products/shares/share-detail/share-detail.html` | **Rewritten** — view: product-header + Share Configuration card + Lock-in & Dividends card; edit: Core / Shares (authorized + limits) / Lock-in (min active period + lock-in period + dividend policy) tabs |
| `features/products/shares/share-detail/share-detail.scss` | **Rewritten** — full Nubeero pattern |

#### Key Patterns

| Pattern | Details |
|---------|---------|
| FD vs RD distinction | Recurring adds a dedicated Frequency tab; `depositFrequency` + `mandatoryRecommendedDepositAmount` are RD-only fields |
| Share Product tabs | 3 tabs only (core/shares/lockin) — no interest rates; equity model is fundamentally simpler |
| Optional numeric display | `@if (p.unitPrice !== undefined)` + `—` muted fallback avoids rendering `0` as a misleading value for truly unset fields |
| `periodTypes` reuse | Lock-in + min-active-period both use the same DAYS/WEEKS/MONTHS/YEARS selector via `periodTypeLabels` map |

#### Build Verification

- `npx ng build --configuration=production` → **no errors**
- Pre-existing NG8113 warnings in system/admin stubs only — not introduced by this session

#### Compliance Checklist Update

| Item | Status |
|------|--------|
| Fixed Deposit Products list — search, active filter, pagination | ✅ |
| Fixed Deposit Product detail — view mode (interest, term, penalty) | ✅ |
| Fixed Deposit Product detail — edit mode (4 tabs) | ✅ |
| Fixed Deposit Product create + deactivate | ✅ |
| Recurring Deposit Products list — with Frequency column | ✅ |
| Recurring Deposit Product detail — view mode (frequency, term, penalty) | ✅ |
| Recurring Deposit Product detail — edit mode (5 tabs incl. Frequency) | ✅ |
| Recurring Deposit Product create + deactivate | ✅ |
| Share Products list — unit price, shares issued columns | ✅ |
| Share Product detail — view mode (share config, lock-in, dividends) | ✅ |
| Share Product detail — edit mode (3 tabs: core/shares/lockin) | ✅ |
| Share Product create + deactivate | ✅ |

---

### Session 12 — 2026-04-10

**Angular Products UI — full Loan Products + Deposit Products CRUD pages built and pushed (commit `9025133`).**

#### New/Updated Angular Files

| File | Change |
|------|--------|
| `features/products/product.service.ts` | **New** — `ProductService` with 10 methods; typed interfaces: `LoanProduct`, `LoanProductCreateRequest`, `DepositProduct`, `DepositProductCreateRequest`, `GlAccountRef`, `FundRef`, `ChargeRef`, `AllowAttributeOverrides` |
| `features/products/loan-products/loan-products-list.ts` | **Rewritten** — client-side debounced search, active-only toggle, 15-per-page pagination, `repaymentLabel()`, `interestTypeLabel()`, `productVariant()` |
| `features/products/loan-products/loan-products-list.html` | **Rewritten** — page header + New Product CTA, search + active-only toolbar, skeleton rows, empty state, table (name, code, principal range, rate range, term, repayment, interest, status badge), pagination |
| `features/products/loan-products/loan-products-list.scss` | **Rewritten** — full Nubeero: page-header, toolbar, search-box, active-toggle, data-table, shimmer skeleton, empty-state, pagination |
| `features/products/loan-products/loan-product-detail/loan-product-detail.ts` | **Rewritten** — `isNew` detection, view/edit toggle, `enterEditMode(blank)` deep-copy, 5 section tabs (`principal/interest/schedule/grace/accounting`), `save()`/`cancelEdit()`/`deactivate()`, label maps for all enums |
| `features/products/loan-products/loan-product-detail/loan-product-detail.html` | **Rewritten** — back link, skeleton, error state; view mode: product-header card + 6 section cards (principal, interest, repayment, grace, overrides, GL accounts, charges); edit mode: 5-tab form with full Mifos field set; deactivate confirm modal |
| `features/products/loan-products/loan-product-detail/loan-product-detail.scss` | **Rewritten** — product-header layout, sections-grid (2-col), section-tabs, edit-card, form groups, modal, shimmer |
| `features/products/deposit-products/deposit-products-list.ts` | **Rewritten** — client-side search, account-type filter (SAVINGS/CHECKING/FIXED_DEPOSIT), active-only toggle, pagination, `accountTypeLabel()`, `compoundingLabel()` |
| `features/products/deposit-products/deposit-products-list.html` | **Rewritten** — type-filter `<select>` in toolbar, table (name, code, type chip, min balance, interest rate, compounding, status), pagination |
| `features/products/deposit-products/deposit-products-list.scss` | **Rewritten** — mirrors loan-products-list SCSS + `type-select` + `type-chip` styles |
| `features/products/deposit-products/deposit-product-detail/deposit-product-detail.ts` | **Rewritten** — 5 section tabs (`core/interest/lockin/overdraft/accounting`), `glAccountFields[]` array for view-mode GL table, conditional overdraft fields, full create/edit/deactivate flow |
| `features/products/deposit-products/deposit-product-detail/deposit-product-detail.html` | **Rewritten** — back link, skeleton, error state; view mode: product-header + 6 cards (core identifiers, balance, interest config, lock-in, overdraft, charges, GL accounts span-2); edit mode: 5-tab form with conditional overdraft section; deactivate modal |
| `features/products/deposit-products/deposit-product-detail/deposit-product-detail.scss` | **Rewritten** — identical pattern to loan product detail SCSS |

#### Key Patterns Introduced / Reinforced

| Pattern | Where | Description |
|---------|-------|-------------|
| View/edit inline toggle | `LoanProductDetail`, `DepositProductDetail` | `editMode` flag; `form` is separate from `product`; cancel is free (no rollback needed) |
| `isNew` from route param | Both detail components | `'new'` param → blank form → create → navigate to `../p.id` |
| Section tabs (edit mode) | Both detail components | `activeSection: DetailSection` union type; `@if (activeSection === 'xxx')` per section |
| `$any()` cast for union params | `loan-product-detail.html:220` | Template `@for` array infers `sec.id` as `string`; `setSection($any(sec.id))` avoids TS2345 |
| `glAccountFields[]` array | `deposit-product-detail.ts` | Drives `@for` loop in view mode to avoid 8 copy-paste `@if` blocks |
| Conditional overdraft fields | `deposit-product-detail.html` | `@if (form.allowOverdraft)` shows limit/rate/min-balance inputs only when overdraft is on |

#### Build Verification

- `node node_modules/@angular/cli/bin/ng.js build` → **no errors** (NG8113 unused import warnings in unimplemented stubs only — pre-existing)
- Two build fixes: `$any(sec.id)` cast for `DetailSection` union, stale `product.fund` reference removed from deposit detail view

#### Compliance Checklist Update

| Item | Status |
|------|--------|
| Loan Products list — search, active filter, pagination | ✅ |
| Loan Product detail — view mode (all Mifos fields) | ✅ |
| Loan Product detail — edit mode (5 tabs, GL accounts, charges) | ✅ |
| Loan Product create (isNew flow) | ✅ |
| Loan Product deactivate | ✅ |
| Deposit Products list — search, type filter, pagination | ✅ |
| Deposit Product detail — view mode (overdraft, lock-in, GL accounts) | ✅ |
| Deposit Product detail — edit mode (5 tabs, conditional overdraft) | ✅ |
| Deposit Product create (isNew flow) | ✅ |
| Deposit Product deactivate | ✅ |

---

### Session 11b — 2026-04-09

**Backend + docs: extended Loan Products and Deposit Products to full Mifos parity — new enums, @Embeddable AllowAttributeOverrides, GL account @ManyToOne linkages, charges @ManyToMany join tables, V20 Flyway migration, Postman collection + api-reference.html updated.**

#### New/Updated Backend Files

| File | Change |
|------|--------|
| `backend/src/main/java/com/cba/product/AllowAttributeOverrides.java` | **New** — `@Embeddable` with 8 boolean override fields; stored as individual columns prefixed `allow_override_` |
| `backend/src/main/java/com/cba/product/LoanProduct.java` | **Rewritten** — shortName UNIQUE, fund @ManyToOne, defaultPrincipal, installmentAmountInMultiplesOf, full interest/amortization/days config, repayment schedule config, grace periods, @Embedded AllowAttributeOverrides, 8 GL account @ManyToOne fields, @ManyToMany charges |
| `backend/src/main/java/com/cba/product/DepositProduct.java` | **Rewritten** — shortName UNIQUE, minRequiredOpeningBalance, interestPostingPeriodType, daysInYearType, daysInMonthType, lockinPeriod, withdrawalFeeForTransfers, allowOverdraft + overdraft fields, accountingType, 8 GL account @ManyToOne fields, @ManyToMany charges |
| `backend/src/main/java/com/cba/product/dto/LoanProductRequest.java` | **Rewritten** — full field set with nested `AllowAttributeOverridesRequest` record, all GL UUID fields, chargeIds |
| `backend/src/main/java/com/cba/product/dto/LoanProductResponse.java` | **Rewritten** — nested `GlAccountRef`, `FundRef`, `ChargeRef`, `AllowAttributeOverridesResponse` records; `from(LoanProduct)` factory |
| `backend/src/main/java/com/cba/product/dto/DepositProductRequest.java` | **Rewritten** — full Mifos deposit field set |
| `backend/src/main/java/com/cba/product/dto/DepositProductResponse.java` | **Rewritten** — nested `GlAccountRef` + `ChargeRef`; `from()` factory |
| `backend/src/main/java/com/cba/charge/ChargeDefinitionRepository.java` | **New** — `findByActiveTrue()`, `findByChargeAppliesTo()` |
| `backend/src/main/java/com/cba/product/ProductService.java` | **Rewritten** — injects `GlAccountRepository`, `ChargeDefinitionRepository`, `FundRepository`; `resolveGl()`, `requireFund()`; full field mapping; charges replace-all pattern |
| `backend/src/main/resources/db/migration/V20__product_mifos_parity.sql` | **New** — 30+ `ADD COLUMN IF NOT EXISTS` on loan_products; 20+ on deposit_products; backfill short_name; SET NOT NULL; `loan_product_charges` + `deposit_product_charges` join tables; FK indexes |

#### Documentation Updates

| File | Change |
|------|--------|
| `docs/api-reference.html` | Products section fully replaced — loan products: 5 category field tables; deposit products: 5 category field tables; Full API Matrix updated |
| `docs/cba-postman-collection-v2.json` | Create/Update Loan Product + Create/Update Deposit Product request bodies fully updated with all Mifos fields |

#### Compliance Checklist Update

| Item | Status |
|------|--------|
| Loan Products — full Mifos field parity (backend) | ✅ |
| Deposit Products — full Mifos field parity (backend) | ✅ |
| AllowAttributeOverrides — @Embeddable, 8 boolean columns | ✅ |
| GL account linkages — @ManyToOne, nested GlAccountRef response | ✅ |
| Charges join tables — @ManyToMany, replace-all on update | ✅ |
| V20 migration — backfill + NOT NULL constraint | ✅ |
| Postman collection — all 4 product CRUD requests updated | ✅ |
| api-reference.html — Products section fully documented | ✅ |

---

### Session 11 — 2026-04-09

**Operations group fully wired: Dashboard, Customers List, Accounts List, Loans List, Customer Detail (5-tab), and Loan Detail (5-tab) all connected to backend API with real data, Nubeero styling, skeleton loaders, and action workflows.**

#### New/Updated Angular Files

| File | Change |
|------|--------|
| `features/operations/dashboard/dashboard.service.ts` | New — `getKpis()` via `forkJoin`, `getRecentTransactions()`, `getKycPendingCustomers()` |
| `features/operations/dashboard/dashboard.ts` | Fleshed — KPI data binding, loanPortfolio bars, `avatarColor()`, `txnAmountClass()` |
| `features/operations/dashboard/dashboard.html` | Fleshed — KPI grid (4-col), main grid (2-col), transaction table, portfolio bars, KYC queue |
| `features/operations/dashboard/dashboard.scss` | Full Nubeero styles: kpi-grid, main-grid, data-table, progress bars, activity list |
| `features/operations/customers/customer.service.ts` | New — `list()`, `get()`, `updateKycStatus()`, `getIdentifiers()`, `getAddresses()`, `getBeneficiaries()` + interfaces |
| `features/operations/customers/customers-list.ts` | Fleshed — debounced search (`Subject` + `debounceTime(300)` + `switchMap`), KYC filter tabs, pagination |
| `features/operations/customers/customers-list.html` | Full table with search/filter toolbar, avatar cells, KYC badges, skeleton rows, pagination |
| `features/operations/customers/customers-list.scss` | Page layout, stats row, filter tabs, data table, avatar, shimmer skeleton, pagination |
| `features/operations/accounts/account.service.ts` | New — `list()`, `get()`, `create()`, `freeze()`, `close()` + `Account` interface |
| `features/operations/accounts/accounts-list.ts` | Fleshed — type filter tabs, pagination, `statusVariant()`, `typeIcon()` |
| `features/operations/accounts/accounts-list.html` | Type filter tabs, table with account number, type, balance, currency, status, pagination |
| `features/operations/loans/loan.service.ts` | Extended — `LoanCharge`, `Guarantor`, `Collateral`, `AuditEntry` interfaces + 8 new methods |
| `features/operations/loans/loans-list.ts` | Fleshed — pipeline stage cards, `selectLoan()` lazy-loads schedule, detail panel |
| `features/operations/loans/loans-list.html` | Pipeline grid, loan table with row-click, sliding detail panel + schedule preview |
| `features/operations/loans/loans-list.scss` | Pipeline grid, `loan-layout` CSS grid `--split` modifier, detail panel header |
| `features/operations/customers/customer-detail/customer-detail.ts` | New — 5-tab lazy loading, KYC state machine (`kycTransitions`), `confirmKycChange()` |
| `features/operations/customers/customer-detail/customer-detail.html` | Profile card, KYC dropdown, 5-tab bar, Overview/Accounts/Loans/ID & Address/Beneficiaries |
| `features/operations/customers/customer-detail/customer-detail.scss` | Profile card, KYC dropdown, tab bar, action tiles, skeleton animation, id/address cards |
| `features/operations/loans/loan-detail/loan-detail.ts` | New — 5-tab lazy loading, 3 modals, status-gated action getters, `sum()` totals method |
| `features/operations/loans/loan-detail/loan-detail.html` | Status band, repayment progress bar, 5 tabs, 3 modals (Approve/Reject/Repayment) |
| `features/operations/loans/loan-detail/loan-detail.scss` | Status band color variants, timeline, audit trail, modal overlay, form styles |
| `angular.json` | Font inlining disabled (network-free build), `anyComponentStyle` budget → 16 kB |

#### Key Patterns Introduced

| Pattern | Where | Description |
|---------|-------|-------------|
| Debounced search | `customers-list.ts` | `Subject<string>` + `debounceTime(300)` + `distinctUntilChanged()` + `switchMap` — cancels in-flight requests |
| Lazy tab loading | `customer-detail.ts`, `loan-detail.ts` | Per-tab `Loaded` boolean flag; data fetched only on first visit, cached in component state |
| KYC state machine | `customer-detail.ts` | `kycTransitions` record maps each status to its legal next states; UI renders only valid transitions |
| Status-gated actions | `loan-detail.ts` | `canApprove`, `canDisburse`, `canReject`, `canRepay` computed getters drive button visibility |
| Schedule cache invalidation | `loan-detail.ts` | After repayment: `scheduleLoaded = false; schedule = []` forces re-fetch on next tab visit |
| sum() method | `loan-detail.ts` | Replaces non-existent `sumBy` pipe; `sum(field: keyof RepaymentInstallment): number` |
| Sliding detail panel | `loans-list.ts` | CSS grid shifts `1fr` → `1fr 380px` on row click via `--split` BEM modifier |

#### Build Verification

- All 6 new/updated components compile cleanly — no errors
- Warnings: NG8113 unused imports in system/admin stubs only (pre-existing, expected)
- `loan-detail.scss` 9.24 kB → budget raised to 16 kB (justified: modals + timeline + audit trail)
- Font inlining disabled to allow offline/CI builds (fonts loaded from Google CDN at runtime)

#### Compliance Checklist Update

| Item | Status |
|------|--------|
| Operations Dashboard — connected to backend API | ✅ |
| Customers List — search, filter, pagination | ✅ |
| Accounts List — type filter, pagination | ✅ |
| Loans List — pipeline view, sliding detail panel | ✅ |
| Customer Detail — 5 tabs, KYC workflow | ✅ |
| Loan Detail — 5 tabs, approve/disburse/repayment/reject | ✅ |

---

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

### Backend — Core Standards

| Requirement | Status | Notes |
|-------------|--------|-------|
| UUID primary keys | ✅ | `gen_random_uuid()` on all tables |
| NUMERIC(19,4) for money | ✅ | All balance/amount/rate columns; `BigDecimal` in Java |
| Optimistic locking (`version`) | ✅ | All mutable entities |
| Flyway owns schema (`ddl-auto: validate`) | ✅ | 20 migrations V1–V20; never `create/update` in non-test |
| `@Transactional` on all service methods | ✅ | `readOnly = true` on queries |
| `@Transactional(REQUIRES_NEW)` for audit | ✅ | `AuditLogService` — survives main TX rollback |
| `SELECT FOR UPDATE` for money transfers | ✅ | `AccountRepository.findByIdWithLock()` |
| Deadlock-safe UUID ordering | ✅ | `min/max(UUID)` lock acquisition order |
| Standard response envelope | ✅ | `ApiResponse<T>` — `{data, meta, errors}` |
| OpenAPI 3.1 docs | ✅ | springdoc at `/swagger-ui.html` |
| Annuity EMI formula | ✅ | `RepaymentScheduleEngine` |
| Double-entry ledger | ✅ | Debit + Credit `Transaction` records per transfer |
| Audit trail on write operations | ✅ | `AuditLogService.log()` in all services |
| Append-only audit log (no UPDATE/DELETE) | ✅ | Enforced by service convention |
| `tenant_id` column on all tables | ✅ | V1 schema; nullable in v1, ready for v2 multi-tenancy |
| Base path `/api/v1/` | ✅ | All controllers |
| No business logic in controllers | ✅ | Controllers call service → return `ResponseEntity<ApiResponse<T>>` |

### Backend — Security

| Requirement | Status | Notes |
|-------------|--------|-------|
| PII field-level encryption (AES-256) | ✅ | `EncryptedStringConverter` + `PBEWITHHMACSHA512ANDAES_256` |
| `ENCRYPTION_KEY` from environment variable | ✅ | `FieldEncryptor` — never in code |
| Keycloak JWT + RBAC | ✅ | `realm_access.roles`; roles: ADMIN, TELLER, CUSTOMER, API_CLIENT |
| No PII in logs | ✅ | Encrypted PII never logged directly |
| Testcontainers (no mock DB in integration tests) | ✅ | `AbstractIntegrationTest` |
| Two-Factor Authentication (OTP) | ✅ | `com.cba.user` — 6-digit, 10min expiry, EMAIL/SMS |
| Maker-Checker workflow | ✅ | `com.cba.social` — PENDING → APPROVED/REJECTED |
| Roles & Permissions RBAC table | ✅ | `com.cba.role` — `Role` → `Permission` many-to-many |

### Backend — Modules

| Module | Status | Package | Flyway |
|--------|--------|---------|--------|
| Customer (KYC + onboarding) | ✅ | `com.cba.customer` | V1 |
| Account (savings/checking/FD) | ✅ | `com.cba.account` | V1 |
| Loan (full lifecycle) | ✅ | `com.cba.loan` | V1 |
| Payment (transfers + standing orders) | ✅ | `com.cba.payment` | V1 |
| Loan Products | ✅ | `com.cba.product` | V1, V20 |
| Deposit Products | ✅ | `com.cba.product` | V1, V20 |
| Open Banking (AISP/PISP/CBPII) | ✅ | `com.cba.openbanking` | V1 |
| Notification (event-driven) | ✅ | `com.cba.notification` | — |
| Audit Log | ✅ | `com.cba.audit` | V1 |
| Teller / Cash Management | ✅ | `com.cba.teller` | V5 |
| Group & Center (microfinance) | ✅ | `com.cba.group` | V7 |
| Office & Staff | ✅ | `com.cba.office` | V6 |
| User Management (Keycloak sync) | ✅ | `com.cba.user` | V6 |
| Self Service (customer-facing) | ✅ | `com.cba.selfservice` | V11 |
| GL / Accounting | ✅ | `com.cba.accounting` | V8 |
| Reports (dynamic SQL engine) | ✅ | `com.cba.report` | V9 |
| CoB Scheduler (Spring Batch + Quartz) | ✅ | `com.cba.cob` | V10 |
| Batch API (multi-request) | ✅ | `com.cba.batch` | — |
| Charges | ✅ | `com.cba.charge` | V12 |
| Fixed Deposit | ✅ | `com.cba.deposit` | V13 |
| Recurring Deposit | ✅ | `com.cba.deposit` | V13 |
| Share Products & Accounts | ✅ | `com.cba.share` | V14 |
| Loan Guarantors & Collateral | ✅ | `com.cba.loan` | V15 |
| Loan Reschedule / Re-aging / Re-amortization | ✅ | `com.cba.loan` | V15 |
| Floating Rates | ✅ | `com.cba.system` | V16 |
| Taxes (Components + Groups) | ✅ | `com.cba.system` | V16 |
| System Config (Codes, GlobalConfig, Funds, PaymentTypes) | ✅ | `com.cba.system` | V16 |
| Hooks & Holidays | ✅ | `com.cba.social` | V17 |
| SMS Campaigns | ✅ | `com.cba.social` | V17 |
| Report Mailing Jobs | ✅ | `com.cba.social` | V17 |
| Standing Instructions | ✅ | `com.cba.social` | V17 |
| Notes & Documents | ✅ | `com.cba.social` | V17 |
| Client Identifiers & Addresses | ✅ | `com.cba.customer` | V18 |
| Beneficiaries | ✅ | `com.cba.customer` | V18 |
| Client Images | ✅ | `com.cba.customer` | V18 |
| Two-Factor Authentication | ✅ | `com.cba.user` | V18 |
| DataTables | ✅ | `com.cba.social` | V18 |
| Credit Bureau | ✅ | `com.cba.system` | V18 |
| Surveys | ✅ | `com.cba.system` | V18 |
| Accounting Rules | ✅ | `com.cba.accounting` | V19 |
| Provisioning Criteria (IFRS 9) | ✅ | `com.cba.accounting` | V19 |
| Multi-Currency + Exchange Rates | ✅ | `com.cba.currency` + `com.cba.tenant` | V3, V4 |
| Global Search | ✅ | `com.cba.search` | — |
| Roles & Permissions | ✅ | `com.cba.role` | V6 |

### Backend — Multi-Currency

| Requirement | Status | Notes |
|-------------|--------|-------|
| Tenant base currency (not hardcoded USD) | ✅ | `TenantContext` + `TenantService.getBaseCurrency()` |
| Auto-inverse exchange rates | ✅ | `ExchangeRateService.setRate()` |
| Cross-currency transfer with FX audit | ✅ | `PaymentService.transfer()` — stores rate used |
| 3 demo tenant currencies | ✅ | USD, KES, GHS — `V4__multi_currency_demo_data.sql` |

### Angular Web Frontend

| Requirement | Status | Notes |
|-------------|--------|-------|
| Angular 17+ standalone components | ✅ | All components use `standalone: true` + `inject()` |
| `@if`/`@for` control flow (not `*ngIf`/`*ngFor`) | ✅ | Migrated in Session 19 |
| Nubeero design tokens applied | ✅ | All SCSS uses `@use 'assets/styles/tokens' as *` |
| Lazy-loaded feature modules | ✅ | `accounting`, `admin`, `groups`, `open-banking`, `operations`, `products`, `reports`, `system` |
| Auth bypass for Vercel preview | ✅ | `NG_APP_AUTH_BYPASS=true` skips Keycloak |
| Dashboard | ✅ | KPIs, transaction table, loan portfolio bars, KYC queue |
| Customers (list + detail) | ✅ | Debounced search, KYC state machine, 5-tab detail |
| Accounts (list + detail) | ✅ | Type filter, overview/transactions, freeze/close/deposit/withdraw modals |
| Payments (list + detail) | ✅ | 3-step transfer wizard, standing orders, FX details, reversal |
| Teller (list + detail) | ✅ | Cashier management, session open/close, cash-in/out/settle |
| Loans (list + detail) | ✅ | Pipeline view, approve/disburse/repayment/reject, 5-tab detail |
| Loan Products (list + detail) | ✅ | View/edit toggle, GL linkages, charges, 5 section tabs |
| Deposit Products (list + detail) | ✅ | Overdraft config, GL linkages, 5 section tabs |
| Fixed Deposit Products (list + detail) | ✅ | Penalty rate, term range, 4 section tabs |
| Recurring Deposit Products (list + detail) | ✅ | Deposit frequency, 5 section tabs |
| Share Products (list + detail) | ✅ | Unit price, dividend policy, 3 section tabs |
| GL Accounts | ✅ | Type filter tabs, enable/disable, create/edit modal |
| Journal Entries | ✅ | T-ledger grouped view, manual entry, reversal |
| Provisioning Criteria | ✅ | IFRS 9 age bands, GL account dropdowns |
| Reports | ✅ | Dynamic param form, schema-on-read results, CSV export |
| CoB Scheduler | ✅ | Job cards, Run Now trigger, history panel |
| Report Mailing Jobs | ✅ | RRULE schedule presets, output type chips |
| Users | ✅ | Create modal, enable/disable, delete |
| Roles | ✅ | Permissions matrix grouped by category, select-all-in-group |
| Offices | ✅ | Hierarchy display, parent office dropdown |
| Hooks | ✅ | WEB/SMS type chips, event selection chips |
| Maker-Checker | ✅ | Status filter tabs, approve/reject PENDING entries |
| TPP Management | ✅ | PSD2 TPP registry: clientId, country, scope chips, cert expiry |
| Groups (list + detail) | ✅ | Members, Collection Sheet, GLIM Accounts tabs |
| Centers (list + detail) | ✅ | Groups + All Members tabs |
| Open Banking Consents (list + detail) | ✅ | AISP/PISP/CBPII filter, Authorise/Revoke flow |
| Codes & Values | ✅ | Inline accordion, load-on-expand, inline add/edit form |
| Global Config | ✅ | Type-aware inline edit (string/number/boolean), enabled toggle |
| Floating Rates | ✅ | Accordion, dynamic period rows in create/edit modal |
| Taxes | ✅ | Two-tab: Tax Components + Tax Groups with component bundles |
| **Total Angular components** | **48 / 48** | **0 stubs remaining** |

### CI/CD & Deployment

| Requirement | Status | Notes |
|-------------|--------|-------|
| Backend CI (GitHub Actions) | ✅ | `backend-ci.yml` — test → OWASP → SpotBugs → Docker → k8s |
| Angular CI (GitHub Actions) | ✅ | `web-ci.yml` — lint → test → build → Vercel deploy |
| Mobile CI (GitHub Actions) | ✅ | `mobile-ci.yml` — analyze → test → APK/IPA build |
| Security scanning | ✅ | `security-scan.yml` — CodeQL, Trivy, Gitleaks, Snyk, ZAP |
| GitHub Pages (API docs) | ✅ | `pages.yml` — `docs/api-reference.html` auto-deployed |
| Vercel deployment (Angular) | ✅ | `--prebuilt` CI flow; live at `cba-web-nine.vercel.app` |
| Dependabot auto-updates | ✅ | `.github/dependabot.yml` — Maven, npm, pub, Docker, Actions |
| OWASP false-positive suppressions | ✅ | `docs/owasp-suppressions.xml` |
| SonarCloud quality gate | ✅ | 70% line coverage minimum (backend + web) |

### Documentation

| Requirement | Status | Notes |
|-------------|--------|-------|
| API Reference (HTML) | ✅ | `docs/api-reference.html` — Mifos apiLive.htm style |
| Postman Collection v2 | ✅ | `docs/cba-postman-collection-v2.json` — 8-language code samples |
| Postman Coming Soon | ✅ | `docs/cba-postman-collection-coming-soon.json` |
| Figma design archive | ✅ | `RqbeDCCJiD36eettFSsKZn` — 38 frames across 10 pages |

### Not Yet Built

| Item | Status | Notes |
|------|--------|-------|
| Docker Compose | ❌ | Phase 4 — postgres, keycloak, backend, web, mailhog |
| Kubernetes manifests | ❌ | Phase 4 — namespace `cba-platform`, HPA, Sealed Secrets |
| Keycloak realm JSON | ❌ | Phase 4 — `cba` realm, FAPI 2.0, demo users |
| Flutter mobile app | ❌ | Phase 3 — `mobile/` directory is empty |
