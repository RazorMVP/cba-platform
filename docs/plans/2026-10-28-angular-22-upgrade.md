> **Status:** merged — PR #209, merge commit `0482f3c`. The lint rule it left on was turned off in the follow-up (cont. 51). Approved 2026-10-28 in plan mode (user: "1 first then we do 2(plan an Angular 22 upgrade)"; D1a by default). Body verbatim.

# Upgrade the web app (`web/`) from Angular 21 to Angular 22

> Status: awaiting approval (user: "1 first then we do 2 (plan an Angular 22 upgrade)").
> Previous content of this file — the docs-sites plan — is in `docs/plans/2026-10-28-docs-sites-deps.md` (#208, `aa4bb86`).

## Context
Angular 22 (current 22.2.x) has been out since 2026-06-03; `web/` is on 21.2.25 / CLI 21.2.28. Dependabot's #190
(CLI 22) was closed once #207 fixed its security reason, so this is a planned upgrade, not an alert. Goal: Angular 22
with **no change in behaviour** (same screens, same change detection), tests and production build green.

## What was checked (read-only)
**Requirements**
- Node: Angular 22 needs `^22.22.3 || ^24.15.0 || >=26`. Local is **v22.23.3**, CI uses Node `'22'` → no Node change.
  (CLAUDE.md's "needs Node ≥ 24.15 for CLI v22" note is wrong and gets corrected.)
- **TypeScript 6.0** is required (`>=6.0 <6.1`; latest 6.0.3). `typescript-eslint` 8.56.1 caps TS at `<6.0` → needs ^8.72.
- `zone.js` ~0.16 still supported; vitest ^4 still supported; `@angular/build` 22 brings vite 8.3.5.

**Angular 22 breaking changes vs this app**
| Change | Effect here | Handled by |
|---|---|---|
| Components without `changeDetection` default to **OnPush** | Would break ~95 components that set plain properties in `subscribe()` (CLAUDE.md "zone-based, REQUIRED") | `ng update` migration `change-detection-eager` adds `ChangeDetectionStrategy.Eager` to every component — keeps today's behaviour |
| New template diagnostics (`nullishCoalescingNotNullable`, `optionalChainNotNullable`), optional-chaining semantics | 74 `?.` in 25 templates | migrations `strict-safe-navigation-narrow` + `safe-optional-chaining` (wrap in `$safeNavigationMigration()`) |
| `strictTemplates` default | Already `true` in `tsconfig.json` | nothing |
| Router `paramsInheritanceStrategy` → `'always'` | All `:id` routes are flat siblings of their list route; no param routes have children | nothing (verified) |
| Unused component styles removed when the host is destroyed | `::ng-deep` only in 2 files, both under `:host`; component `@keyframes` (`fade-in`, `shimmer`, `spin`) all also defined globally, `slideIn` used only in its own file | nothing (verified) |
| Forms `min`/`max` "no longer accept strings" | 97 static `min="0"`-style attributes in 20 templates; but 22.2.3's `MinValidator.min` is still `string \| number \| null` | nothing expected; the build would flag it if wrong → then bind `[min]="0"` |
| `ComponentFactoryResolver`, `provideRoutes`, Hammer.js, `checkNoChanges`, `data-` input binding, upload progress (`withXhr`) | 0 uses (no `@NgModule`, no `reportProgress`, no `data-` attrs) | nothing |

**Libraries** (from the inventory: PrimeNG, ng2-charts/chart.js, Angular Material/CDK and NgRx have **zero imports** —
NgRx is only an empty `provideStore({})` / `provideEffects([])` in `app.config.ts`; `styles.scss` has 1 `.mat-mdc` rule)
| Package | Now | Angular 22 version | Notes |
|---|---|---|---|
| keycloak-angular | 21.0.0 | 22.0.0 | "Angular 22 support" only; app uses `provideKeycloak`, `withAutoRefreshToken`, `createAuthGuard` |
| keycloak-js | 26.x | unchanged | |
| primeng / primeicons | 21.1 / 7 | 22.1.4 | major with removals (pTemplate, styleClass, 16px base font) — irrelevant since unused |
| ng2-charts / chart.js | 10 / 4.5 | 11.0.0 | unused |
| @angular/material, cdk | 21.2.14 | 22.2.5 | unused; only list/a11y/menu removals |
| @ngrx/store, effects, entity | 21.1 | 22.0.1 | unused; removes callback `tapResponse` |
| angular-eslint | 21.3.1 | 22.6.0 | |

## Decision for the user — D1: the four unused libraries
- **D1a (recommended): remove** `primeng`, `primeicons`, `ng2-charts`, `chart.js`, `@angular/material`, `@angular/cdk`,
  `@ngrx/store`, `@ngrx/effects`, `@ngrx/entity`; drop `provideStore({})`/`provideEffects([])` and the dead
  `.mat-mdc` rule. Nothing imports them, each brings its own major bump and its own future Dependabot alerts, and
  re-adding one later is a one-line install. CLAUDE.md's stack sections get corrected ("installed, not used" → removed).
- **D1b: keep and bump** to the Angular 22 versions above (no code uses them, so only `package.json`/lockfile change).
Default if approved without a reply: **D1a**.

## Steps (branch `chore/web-angular-22`, one PR)
1. **Baseline** on `main`: `CI=true npx ng test --no-watch` (expect 115 files / 1170 tests) and
   `npx ng build --configuration production`; record bundle sizes.
2. **D1a:** `npm uninstall` the unused packages; remove `provideStore`/`provideEffects` (+ imports) from
   `src/app/app.config.ts` and the `.mat-mdc` rule from `src/styles.scss`; build + test green before moving on.
3. **Upgrade with migrations** (Node 22.23.3 meets CLI 22's engine, so the cont. 23 "ng update needs Node 24" trap no
   longer applies):
   `npx ng update @angular/core@22 @angular/cli@22 angular-eslint@22` (+ `keycloak-angular@22`, `typescript@~6.0.3`,
   `typescript-eslint@^8.72` via the same call or a follow-up `npm install`).
   **Fallback** if npm crashes (`edgesOut`, the Vitest 4.1 bug from cont. 48): edit the floors in `package.json` by hand
   (`@angular/*` ^22.2.3, `@angular/build`/`cli` ^22.2.5, `angular-eslint` 22.6.0, `typescript` ~6.0.3,
   `typescript-eslint` ^8.72.1, `keycloak-angular` ^22.0.0), write the lockfile with the cont. 48 sequence
   (`--package-lock-only --legacy-peer-deps` → plain `--package-lock-only` → `npm ci`), then run the migrations:
   `npx ng update @angular/core --migrate-only --from=21.2.25 --to=22.2.3` and
   `npx ng update @angular/cli --migrate-only --from=21.2.28 --to=22.2.5`.
4. **Check the migrations did their job:** every non-spec `@Component` (95) now has `ChangeDetectionStrategy.Eager`
   (grep count must match); review the `$safeNavigationMigration` and tsconfig diffs; no hand edits to them.
5. **`undici` overrides** (added in #207 for `@angular/build` 21's exact pin): after the upgrade run `npm ls undici`;
   remove each override whose undici major no longer appears, keep the other (jsdom 28 still uses `undici ^7`), and
   confirm `npm audit` stays 0.
6. Fix whatever the TS 6.0 compile, lint or tests surface (expected: little — tsconfig already `strict`, `module:
   preserve`, `target: ES2022`, no `baseUrl`/`downlevelIteration`/`moduleResolution node`; app `types: []`, spec
   `types: ["vitest/globals"]`). Any change beyond the migrations is listed in the PR.

## Verification
- `npm ci` (plain) + `npm audit --audit-level=high` exit 0 (aim: 0 at any level).
- `CI=true npx ng test --no-watch`: 115 files / **1170** tests, same as baseline.
- `npx ng build --configuration production` green; budgets (500 kB / 1 MB initial) hold; size change reported.
- `npx ng lint`: no new errors vs the 7 pre-existing (report-only in CI).
- **Browser check** (CLAUDE.md requires it for change-detection changes): `npx ng serve` with auth bypass; open the
  dashboard, customers list → a customer detail, accounts, loans, cards, reports. Each must reach its loaded state
  with no interaction (`skeletons: 0, spinners: 0`), no console errors, plus the `XMLHttpRequest` zone-patch probe.
- PR CI: Web CI gate (Lint & Test, Security Audit, Build & Deploy → Vercel preview), E2E smoke against the preview.

## Docs (same PR)
- `CLAUDE.md`: web versions table (Angular / CLI / build 22.x, TypeScript 6.0, typescript-eslint, keycloak-angular 22,
  removed libraries per D1, Node row corrected, `undici` override row updated); Angular Change Detection section —
  **new components must set `changeDetection: ChangeDetectionStrategy.Eager`** (v22 defaults to OnPush, which this
  app's plain-property pattern can't use); "Angular Dependency Upgrades" — the CLI-22-needs-Node-24 trap is gone,
  `ng update` path recorded; tech-stack lines for PrimeNG/Material/NgRx per D1.
- `cba-log.md`: Session 125 cont. 50 entry with the gate proof line (no API surface change).
- `docs/plans/2026-10-28-angular-22-upgrade.md` + README row; mark the docs-sites plan merged (`aa4bb86`).
- Merge only on the user's "merge <PR>" with CI green.
