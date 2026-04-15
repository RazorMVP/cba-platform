# React Migration Design — CBA Backoffice

**Date:** 2026-04-15
**Status:** Approved
**Author:** RazorMVP + Claude (Session 52–53)

---

## 1. Decision

Full rewrite of the Angular `web/` backoffice portal to React (`web-react/`). Angular stays live on production during the build. Cutover is a single CI change when React reaches feature parity.

### Why React over Angular

Team React skill-set alignment. The Angular codebase remains functional and is not removed until parity is confirmed.

### Why Vite SPA over Next.js

This is a fully authenticated backoffice tool — no public pages, no SEO requirements, no server-side rendering need. Vite SPA is simpler to deploy, faster to build, and avoids Next.js App Router complexity that adds no value here.

---

## 2. Tech Stack

| Concern | Choice | Reason |
|---------|--------|--------|
| Build tool | Vite 6 | Fast HMR, simple config, SPA-optimized |
| Routing | React Router v6 | File-based lazy routes; mirrors Angular lazy modules |
| Data fetching | TanStack Query v5 | Cache, loading/error states, optimistic updates |
| HTTP client | Axios | Interceptors for auth header + base URL |
| UI components | shadcn/ui | Copy-paste into `src/shared/components/` — owned code, no version lock |
| Styling | Tailwind CSS v4 | CSS-first config (`@theme`), no `tailwind.config.ts` |
| State | React Context + TanStack Query | No Zustand or Redux |
| Auth | Bypass flag (dev) → Keycloak at cutover | `VITE_AUTH_BYPASS=true` in dev/preview |
| Language | TypeScript strict mode | |

---

## 3. Design Direction

### 3.1 Scope

**Bold redesign.** Same navy/dark-shell brand foundation. New typographic system, new layout density, new interaction patterns. This is clearly a new product generation — not a pixel-faithful React port.

### 3.2 Users

Bank operations staff (tellers, loan officers, branch managers, admins) working at a desk during business hours, 6–8 hours/day. Speed and accuracy matter more than novelty. The interface must reduce cognitive load, project calm authority, and make users feel capable.

### 3.3 Brand Personality

**Trustworthy, clear, approachable.** Banking is intimidating — this UI should reduce that, not amplify it.

### 3.4 Theme

Dark shell + white content cards. Dark sidebar and topbar reduce peripheral eye strain for desk workers. White card surfaces deliver the contrast needed for dense financial data.

### 3.5 Typography

| Role | Font | Weights | Notes |
|------|------|---------|-------|
| Headings, section labels, page titles | **Epilogue** (Google Fonts variable) | 500, 600, 700 | Geometric, confident, designed. Not a reflex pick. |
| Body, UI text, data, labels | **Geist** (Vercel open source) | 400, 500 | Purpose-built for data-dense interfaces. True tabular numerals. |

**Type scale** (fixed `rem` — app UI, not marketing):

| Token | Size | Usage |
|-------|------|-------|
| `text-xs` | 12px / 0.75rem | Timestamps, metadata, table cell captions |
| `text-sm` | 14px / 0.875rem | Table body, form labels, secondary text |
| `text-base` | 16px / 1rem | Primary body, input values |
| `text-lg` | 18px / 1.125rem | Section headings, modal titles |
| `text-xl` | 20px / 1.25rem | Page titles |
| `text-2xl` | 24px / 1.5rem | KPI numbers, key metrics |

`font-variant-numeric: tabular-nums` applied globally to all numeric data — financial figures must never reflow on update.

### 3.6 Colour System (OKLCH)

| Token | Value | Usage |
|-------|-------|-------|
| `--bg-app` | `oklch(10% 0.018 250)` | App shell background |
| `--bg-sidebar` | `oklch(15% 0.028 250)` | Left navigation |
| `--bg-card` | `oklch(99% 0.004 250)` | Content cards, panels |
| `--bg-subtle` | `oklch(96% 0.006 250)` | Table row alternates, subtle sections |
| `--color-primary` | `oklch(28% 0.045 250)` | CTA buttons, active states |
| `--color-text` | `oklch(8% 0.020 250)` | Primary text |
| `--color-muted` | `oklch(55% 0.010 250)` | Secondary text, labels |
| `--color-accent` | `oklch(72% 0.13 68)` | **Warm amber — focus rings, active nav, key status only** |
| `--color-border` | `oklch(88% 0.008 250)` | Dividers, input borders |
| Semantic success | `oklch(48% 0.15 145)` | ACTIVE, APPROVED badges |
| Semantic warning | `oklch(65% 0.14 70)` | PENDING, IN_REVIEW badges |
| Semantic error | `oklch(50% 0.18 25)` | REJECTED, FAILED badges |
| Semantic info | `oklch(55% 0.14 250)` | INFO badges, links |

All neutrals tinted toward hue 250 (navy) at chroma 0.005–0.010 for subconscious brand cohesion.

### 3.7 Spacing

4pt scale with semantic token names:

| Token | Value | Usage |
|-------|-------|-------|
| `--space-1` | 4px | Icon gaps, tight inline |
| `--space-2` | 8px | Badge padding, compact elements |
| `--space-3` | 12px | Input padding, chip gaps |
| `--space-4` | 16px | Card internal padding (compact) |
| `--space-6` | 24px | Card padding (standard) |
| `--space-8` | 32px | Section separators |
| `--space-12` | 48px | Page section gaps |
| `--space-16` | 64px | Major layout gaps |

**Component sizing:**
- Table rows: 44px (tighter than Angular's 56px; same information, less scroll)
- Input height: 40px
- Button height: 36px (sm) / 40px (default)
- Sidebar width: 256px
- Topbar height: 60px

### 3.8 Anti-References (must not look like these)

- Generic SaaS (Stripe/Linear) — no soft purples, no "Linear-clone" subtle border aesthetic
- Heavy enterprise (SAP/Oracle) — no dense grey toolbars, no nested table-within-table
- AI startup — no cyan gradients, no glowing cards, no neon on dark
- Material Design — no elevation system, no ripple effects

### 3.9 Design Principles

1. **Data over decoration** — every visual element earns its place by clarifying information or surfacing an action.
2. **Calm authority** — no energetic or playful animations. Transitions convey state. Easing: always `cubic-bezier(0.16, 1, 0.3, 1)` (ease-out-quint).
3. **Density with breathing room** — tight within groups, generous between groups. Not spread-out, not cramped.
4. **Approachable structure** — visible affordances, no hover-only disclosure for critical actions.
5. **Amber as the single accent** — `--color-accent` appears only on focus rings, active nav items, and key status badges. Nowhere else.

### 3.10 Accessibility

- WCAG 2.1 AA minimum
- All interactive elements keyboard-navigable
- All text contrast ≥ 4.5:1 on respective backgrounds
- `prefers-reduced-motion: reduce` respected — all transitions in `@media (prefers-reduced-motion: no-preference)` blocks
- Tabular numerals throughout financial data

---

## 4. Project Structure

```
web-react/
├── index.html
├── vite.config.ts
├── components.json          ← shadcn/ui config
├── tsconfig.json
├── package.json
├── vercel.json
└── src/
    ├── main.tsx
    ├── app/
    │   ├── router.tsx        ← React Router v6 lazy routes
    │   ├── layout/
    │   │   ├── Shell.tsx     ← App frame (sidebar + topbar + <Outlet />)
    │   │   ├── Sidebar.tsx
    │   │   └── Topbar.tsx
    │   ├── features/
    │   │   ├── operations/   ← customers, accounts, loans, payments, tellers
    │   │   ├── products/     ← loan products, deposit products, shares
    │   │   ├── accounting/   ← GL, journals, provisioning
    │   │   ├── cards/        ← full card management platform
    │   │   ├── reports/      ← reports, CoB scheduler, mailing jobs
    │   │   ├── admin/        ← users, roles, offices, hooks, maker-checker
    │   │   ├── groups/       ← groups, centers
    │   │   ├── system/       ← codes, config, floating rates, taxes
    │   │   └── open-banking/ ← consents, TPP management
    │   ├── core/
    │   │   ├── api/
    │   │   │   └── apiClient.ts   ← Axios; base URL from VITE_API_URL; auth header
    │   │   └── auth/
    │   │       └── AuthContext.tsx ← bypass flag + Keycloak stub
    │   └── shared/
    │       └── components/
    │           ├── StatusBadge.tsx
    │           ├── DataTable.tsx
    │           ├── KpiCard.tsx
    │           ├── PageHeader.tsx
    │           └── Modal.tsx
    └── styles/
        └── globals.css       ← @theme tokens + Tailwind directives + Geist/Epilogue imports
```

---

## 5. Migration Order

### Phase 0 — Foundation
Shell, Sidebar, Topbar, shared components, apiClient, AuthContext, globals.css. Nothing else starts until Phase 0 is complete.

### Phase 1 — Operations (highest daily use)
Dashboard → Customers list → Customer detail → Accounts list → Account detail → Payments list → Payment detail → Loans list → Loan detail → Teller list → Teller detail.

### Phase 2 — Products
Loan products → Deposit products → Fixed deposit products → Recurring deposit products → Share products.

### Phase 3 — Accounting
GL accounts → Journal entries → Provisioning criteria → Financial Activity Accounts.

### Phase 4 — Cards (12 screens)
Card list → Card detail → Card products → Fraud rules → Settlement → Disputes → Terminal Simulator → API Keys → Webhooks → BIN Management → Scheme Config → Interchange.

### Phase 5 — Reports
Reports list → CoB Scheduler → Report Mailing Jobs.

### Phase 6 — Admin
Users → Roles → Offices → Hooks → Maker-Checker → Notifications Admin → TPP Management.

### Phase 7 — Groups
Groups list + detail → Centers list + detail.

### Phase 8 — System
Codes & Values → Global Config → Floating Rates → Taxes → Account Algorithms.

### Phase 9 — Open Banking
Consents list → Consent detail.

---

## 6. CI/CD — Parallel Vercel Deployments

Two Vercel projects, one monorepo:

| Project | Root dir | URL |
|---------|----------|-----|
| `cba-platform-web` (Angular) | `web/` | Production — do not touch |
| `cba-platform-web-react` (React) | `web-react/` | Preview during build |

`web-ci.yml` gains a `react-deploy` job (additive — no Angular job modified). Both deploy independently on path-filtered pushes.

### Cutover procedure (single PR when parity reached)
1. Change `react-deploy` → `deploy` in `web-ci.yml`; change `--environment=preview` → `--environment=production`
2. Set `VITE_AUTH_BYPASS=false` in Vercel env vars
3. Delete `web/` directory
4. Merge

### Parity definition (all six must be true)
- All 57 Angular screens have a React equivalent
- Auth bypass works identically to Angular
- Every screen reads/writes against the real backend (no mock data)
- `/impeccable audit` passes on all Phase 1–3 screens
- End-to-end smoke test passes: onboard customer → open account → apply loan → approve → disburse → repay
- Angular app still builds without errors (parallel track discipline)

---

## 7. Environment Variables

| Variable | Dev | Preview | Production |
|----------|-----|---------|------------|
| `VITE_API_URL` | `http://localhost:8080` | staging API URL | prod API URL |
| `VITE_AUTH_BYPASS` | `true` | `true` | `false` |

---

## 8. Required GitHub Secrets (new)

| Secret | Purpose |
|--------|---------|
| `VERCEL_TOKEN_REACT` | Separate PAT for React Vercel project |
| `VERCEL_PROJECT_ID_REACT` | From `web-react/.vercel/project.json` after `vercel link` |

`VERCEL_ORG_ID` is shared with the Angular project (same org).
