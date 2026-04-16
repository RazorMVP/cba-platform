---
name: cba
description: Core Banking Application (CBA) builder for Java. Use this skill whenever the user wants to create, scaffold, build, generate, or extend a core banking system, fintech platform, online banking application, or embedded finance solution from scratch. Triggers on phrases like: "build a banking app", "create core banking system", "scaffold banking application", "online banking platform", "fintech app", "banking API", "loan management system", "savings accounts system", "payment processing", "mobile banking app", "open banking API", "FAPI compliance", or any request involving bank accounts, loans, deposits, payments, or financial services infrastructure. This skill MUST be used even if the user only mentions one component (e.g., "build the loan module") — always load the full skill to ensure architectural consistency across the entire system.
---

# Core Banking Application (CBA) Skill

You are building a **production-grade Core Banking Application** from scratch. This skill guides you through scaffolding and explaining every layer of a full-stack banking system:

- **Backend**: Java 21 + Spring Boot 3 + PostgreSQL + Keycloak (FAPI 2.0)
- **Web Frontend**: React 19 + Vite 6 + Tailwind CSS v4 (replacing Angular 17+ — see Phase 2R)
- **Web Frontend (legacy)**: Angular 17+ (enterprise banking portal — live during React transition)
- **Mobile Frontend**: Flutter 3+ (customer mobile banking app)
- **Infrastructure**: Docker Compose (dev) + Kubernetes (prod)
- **Reference**: Apache Fineract / Mifos architecture patterns

---

## What This Skill Produces

A **monorepo** with three independently deployable applications:

```
cba-platform/
├── backend/          # Spring Boot 3 API (Java 21)
├── web/              # Angular 17 banking portal (legacy — live during React transition)
├── web-react/        # React 19 + Vite 6 + Tailwind v4 (new frontend — see Phase 2R)
├── mobile/           # Flutter 3 mobile app
├── infrastructure/   # Docker Compose + Kubernetes + Keycloak
├── docs/             # OpenAPI specs, architecture diagrams
└── README.md
```

Each layer is scaffolded with working code, database migrations, demo data, test fixtures, and a guided walkthrough.

---

## Reference Files (read these as needed)

| File | Read When |
|------|-----------|
| `references/architecture.md` | Planning project structure, module boundaries, API design |
| `references/modules.md` | Scaffolding any banking module (accounts, loans, payments, etc.) |
| `references/security.md` | Implementing auth, FAPI 2.0, Keycloak, encryption, PCI-DSS |
| `references/stack.md` | Choosing dependencies, `pom.xml` setup, Angular/Flutter packages |
| `references/deployment.md` | Docker Compose, Kubernetes manifests, environment config |
| `references/design.md` | **Nubeero design system** — tokens, colors, layout, component map, Figma page spec |

## Design Assets

Pre-built HTML prototypes (open in browser, screenshot for Figma import):
- `designs/tokens.scss` — complete SCSS design token set (Nubeero brand)
- `designs/screens/backoffice/dashboard.html` — KPI dashboard, transaction table, charts
- `designs/screens/backoffice/customers.html` — customer list, KYC badges, search/filter
- `designs/screens/backoffice/loans.html` — loan pipeline, detail panel, repayment schedule

**Design system source**: Nubeero Figma (file `3tO8jrYsfJBjL4POZR3EEM`, node `2418:9329`)
**Target Figma page**: "CoreBanking-Nubeero" in Nubeero Pro workspace

---

## Execution Phases

Work through these phases **in order**. Always start with Phase D (Design) when the user requests UI work. After each phase, explain what was generated and why, then ask the user to confirm before proceeding to the next.

### Phase D — Design (run before any frontend code)
Read `references/design.md` before generating any UI.

1. **Apply the Nubeero design system** — all UI must use tokens from `designs/tokens.scss`
2. **Scaffold screens from prototypes** — use the HTML files in `designs/screens/` as the pixel-level reference, not general patterns
3. **Angular theming** — apply the Angular Material Nubeero theme from `references/design.md`
4. **Flutter theming** — apply `ThemeData` matching the same token set (dark shell, white cards)
5. **Figma export** — open HTML prototypes in browser → screenshot → import to "CoreBanking-Nubeero" Figma page using the html.to.design plugin or manual screenshot method (see `references/design.md` for instructions)

### Phase 0 — Confirm & Orient
1. Greet the user and summarize what will be built (full-stack banking platform)
2. Ask: "Do you want to build the **entire platform** now, or start with a specific layer (backend / web / mobile)?"
3. Read `references/architecture.md` to internalize the full system design before generating any files
4. Create the monorepo root: `cba-platform/` with a root `README.md` and `.gitignore`

### Phase 1 — Backend (Spring Boot 3 + PostgreSQL)
Read `references/stack.md` and `references/modules.md` before starting.

1. **Scaffold the project**
   - Generate `backend/pom.xml` with all required dependencies (see `references/stack.md`)
   - Create the standard package structure: `com.cba.{module}` per banking domain
   - Set up `application.yml` with profiles: `dev`, `test`, `prod`

2. **Database layer**
   - Flyway migrations in `backend/src/main/resources/db/migration/`
   - Schema: `V1__init_schema.sql` (all core tables)
   - Demo data: `V2__demo_data.sql` (sample bank, customers, products)
   - Test fixtures: loaded via `@Sql` in test classes, not mixed with prod migrations

3. **Core modules** — scaffold each as a separate package:
   - `customer` — KYC, onboarding, profile management
   - `account` — savings, checking, fixed deposit accounts
   - `loan` — origination, disbursement, repayment schedule, arrears
   - `payment` — internal transfers, external payments, standing orders
   - `product` — loan products, deposit products, interest rate configuration
   - `notification` — email/SMS event hooks
   - `audit` — immutable audit log for every state-changing operation
   - `openbanking` — FAPI 2.0 endpoints (accounts, transactions, payments consent)

4. **API layer**
   - REST controllers with OpenAPI 3.1 annotations (`springdoc-openapi`)
   - Generate `docs/openapi.yaml` after scaffolding
   - HATEOAS links where appropriate

5. **Security** — read `references/security.md` before this step
   - Keycloak integration (resource server + FAPI 2.0 profile)
   - JWT validation, RBAC roles: `ADMIN`, `TELLER`, `CUSTOMER`, `API_CLIENT`
   - Field-level encryption for PII (AES-256 via Jasypt)
   - Audit trail on all write operations

### Phase 2 — Web Frontend (Angular)
Read `references/stack.md` (Angular section) before starting.

1. Scaffold with Angular CLI: `ng new cba-web --routing --style=scss --standalone`
2. Install Angular Material + PrimeNG for banking UI components
3. Implement modules mirroring the backend: dashboard, customers, accounts, loans, payments, reports
4. HTTP client service layer connecting to backend OpenAPI spec
5. Keycloak-Angular for OIDC login, route guards per RBAC role
6. Responsive layout: sidebar nav, data tables, form wizards

### Phase 2R — React Frontend Rewrite (active — replacing Angular)

> **Status**: In progress. `web-react/` is the new frontend. Phase 0 complete (commit `9b1e9ca`). Features are being ported one phase at a time. Angular `web/` stays live during transition and is removed after React reaches feature parity.

**Tech stack:** React 19 + Vite 6 + React Router v6 + TanStack Query v5 + Axios + shadcn/ui + Tailwind CSS v4 + TypeScript strict

**Build order for React screens** (mirror the Angular Component Map in CLAUDE.md):
1. ✅ **Phase 0 — Foundation** (commit `9b1e9ca`) — Shell, Sidebar, Topbar, shared components (StatusBadge, DataTable, KpiCard, Modal, PageHeader), apiClient, AuthContext, globals.css, vercel.json, CI job
2. ✅ **Phase 1 — Operations** — Dashboard, Customers, Accounts, Loans, Payments, Tellers ✅ Complete — Session 55
3. ✅ **Phase 2 — Products** — Loan products, Deposit products, Fixed/Recurring deposits, Shares ✅ Complete — Session 55
4. ✅ **Phase 3 — Accounting** — GL accounts, Journal entries, Provisioning, Financial Activity Accounts ✅ Complete — Session 55
5. ✅ **Phase 4 — Cards** — Full card management platform (12 screens) ✅ Complete — Session 55
6. 🔲 **Phase 5 — Reports** — Reports list, CoB Scheduler, Report Mailing Jobs
7. 🔲 **Phase 6 — Admin** — Users, Roles, Offices, Hooks, Maker-Checker, Notifications, TPP Management
8. 🔲 **Phase 7 — Groups & System** — Groups, Centers, Codes, Global Config, Floating Rates, Taxes, Account Algorithms
9. 🔲 **Phase 8 — Open Banking** — Consents list, Consent detail
10. 🔲 **Cutover** — Update `web-ci.yml` to point Vercel at `web-react/`, archive `web/`

**Key patterns** (for consistency across all React screens):
- All routes defined in `web-react/src/app/router.tsx` — add new routes here before building screens
- Axios instance at `web-react/src/app/core/api/apiClient.ts` — use for all API calls
- TanStack Query for server state — use `useQuery` / `useMutation` with consistent key format: `['resource', id]`
- shadcn/ui copied into `web-react/src/shared/components/` — owned code, not a runtime dependency
- Tailwind v4 CSS-first config: tokens defined in `web-react/src/styles/globals.css` `@theme` block
- Opacity utilities: `bg-white/[0.08]` (not `bg-white/8`) — v4 opacity scale is 5, 10, 15 (no 8)
- `tabular-nums` on numeric `<td>` only — use `ColumnDef.numeric: true` in DataTable, never on status text

### Phase 3 — Mobile Frontend (Flutter)
Read `references/stack.md` (Flutter section) before starting.

1. Scaffold: `flutter create cba_mobile --org com.cba --platforms android,ios`
2. Feature-first folder structure: `features/{auth,dashboard,accounts,loans,payments,profile}/`
3. `flutter_appauth` for OIDC/Keycloak authentication
4. Riverpod for state management, Dio for HTTP client
5. Biometric authentication support (`local_auth`)
6. PIN fallback, session timeout, certificate pinning

### Phase 4 — Infrastructure
Read `references/deployment.md` before starting.

1. **Docker Compose** (`infrastructure/docker-compose.yml`):
   - Services: `postgres`, `keycloak`, `backend`, `web`, `mailhog` (dev email)
   - Volumes, health checks, depends_on ordering

2. **Kubernetes** (`infrastructure/k8s/`):
   - Namespace: `cba-platform`
   - Deployments + Services for each component
   - ConfigMaps and Secrets (with sealed-secrets annotations)
   - Ingress with TLS termination
   - HorizontalPodAutoscaler for backend

3. **Keycloak realm** (`infrastructure/keycloak/cba-realm.json`):
   - Pre-configured realm: `cba`
   - Clients: `cba-backend`, `cba-web`, `cba-mobile`
   - FAPI 2.0 security profile enabled
   - Demo users: `admin@cba.com`, `teller@cba.com`, `customer@cba.com`

### Phase 5 — Walkthrough & Handoff
1. Print a "Getting Started" guide explaining how to run the full stack locally
2. Show the sequence: `docker-compose up` → Keycloak ready → backend starts → web/mobile connect
3. Point to the Swagger UI URL, the Angular portal URL, and Flutter build commands
4. Summarize what each module does and which reference doc to consult for extensions
5. Ask: "Which part would you like to explore or extend first?"

---

## Mandatory Documentation Updates

**After every change, addition, or fix — no exceptions — you MUST complete ALL of the following steps before finishing:**

### Step 1 — Update `cba-log.md` (Change Log)
- Add a new session entry at the top of the Change History section.
- Entry format:
  ```
  ### Session N — YYYY-MM-DD
  **One-line summary of what was built (commit SHA if pushed).**

  #### New/Updated Files
  | File | Change |
  |------|--------|
  | ... | ... |

  #### Key Patterns / Decisions (if any)

  #### Build Verification
  #### Compliance Checklist Update
  ```
- Mark the "Not Yet Built" and "Partially Built" tables in the Backend Audit section to reflect completed work.

### Step 2 — Update `CLAUDE.md` (Body of Knowledge)
- Update the **Angular Component Map** table with newly built Angular components — change `🔲 Stub` → `✅ Built` and add a brief description.
- Update the **React Migration Checklist** table (in the "React Frontend Migration — Session 52" section) with newly built React screens — change `🔲 Queued` → `✅ Built` for each completed screen.
- Update the **Phase 2R build order** list in this skill (see below) when a React phase completes — add `✅ Complete — Session N, commit SHA`.
- Update any module catalogue entries, coding standards, or patterns that changed.
- Update the stub count in "All other feature pages" row.
- For new services/packages: add an implementation notes subsection with the verified package structure, resource file list, and any critical gotchas discovered during the build.
- For completed backend build order steps: mark them `✅` with the commit SHA.
- For completed React phases: mark the Phase 2R phase entry `✅ Complete — Session N, commit SHA`.

### Step 3 — Check and update API documentation (if applicable)
After any backend change, ask: **did this session add, remove, or modify any REST endpoints?**

If **yes**, update both of these files before committing:

#### `docs/cba-postman-collection-v2.json`
- Add new request items for every new endpoint (method, URL, headers, example body, example response)
- Remove or update items for any endpoints that changed signature or were deleted
- Maintain the existing Mifos-style folder structure — group by module (e.g. `Card Service / Cards`, `Card Service / Fraud Rules`)
- Include all supported query params, path params, and request body fields
- Add at least one example response per request (approved + declined where relevant for card endpoints)
- Include 8-language code samples (`cURL`, `Java`, `JavaScript`, `Python`, `Go`, `PHP`, `Ruby`, `C#`) on each request

#### `docs/api-reference.html`
- Mirror the Mifos `apiLive.htm` structure: add a new section anchor for the module, list all endpoints with method badge, path, description, request/response schema tables
- Update the full API matrix table at the top with new endpoint rows
- Keep the standalone HTML self-contained (no external CDN dependencies that could break offline)

If **no new endpoints** were added (e.g. build fixes, Flyway migrations only, Angular-only changes), skip this step — do not update the API docs unnecessarily.

### Step 4 — Commit and push all updated docs to GitHub
After completing Steps 1–3, stage and commit everything together:
```bash
git add cba-log.md CLAUDE.md
# If API docs were updated, also add:
git add docs/cba-postman-collection-v2.json docs/api-reference.html
git commit -m "docs(cba-log+claude): Session N — <one-line summary>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
git push origin main
```
This is **not optional** — all updated doc files must be committed and pushed to GitHub as part of every session. The push is the final act of every build session.

### When to update
- After completing any feature (backend, Angular, or React)
- After completing a React phase (Phase 0–8) — mark the phase `✅` in `CLAUDE.md` and in this skill's Phase 2R build order
- After completing any React screen — update the React Migration Checklist table in `CLAUDE.md`
- After any refactor that changes patterns described in CLAUDE.md
- After any build fix or architectural decision
- After any compile error is diagnosed and fixed (add to gotchas)
- After every session — even if only one file changed
- API docs: only when REST endpoints are added, changed, or removed

---

## Interactive Walkthrough Rules

After scaffolding each phase:
- **Explain** what was generated in plain language (2–4 sentences per major component)
- **Highlight** any non-obvious architectural decisions and why they were made
- **Ask** one targeted question before moving to the next phase, e.g.: "The loan module is scaffolded with a standard annuity repayment schedule. Do you need balloon payments or Islamic finance (murabaha) support?"
- **Wait** for confirmation before proceeding

---

## Coding Standards

- Java 21 — use records, sealed classes, pattern matching where appropriate
- All API responses use a standard envelope: `{ "data": ..., "meta": ..., "errors": [] }`
- Every service method is `@Transactional` by default; read-only methods use `@Transactional(readOnly = true)`
- No business logic in controllers — controllers delegate to service layer only
- All monetary amounts stored as `NUMERIC(19,4)` in PostgreSQL, handled as `BigDecimal` in Java
- Never store raw PII in logs — mask card numbers, account numbers, national IDs

---

## Key Reference Systems

- **Apache Fineract**: https://github.com/apache/fineract — study the domain model and API conventions
- **Mifos Core Banking**: https://docs.mifos.org/core-banking-and-embedded-finance/core-banking/overview
- **Mifos Embedded Finance**: https://docs.mifos.org/core-banking-and-embedded-finance/embedded-finance/overview
- **Mifos Architecture**: https://mifos.org/resources/technical-resources/architecture/
