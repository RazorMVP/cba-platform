> **Status:** merged — PR #207, merge commit `3a689ef`; production deploy green. Approved 2026-10-27 in plan mode (user: "merge 207", then plan mode on). Body verbatim.

# Merge PR #207 — web dependency security fix

> Status: awaiting approval. (The previous content of this file — the backlog 15b plan — is already kept in
> `docs/plans/2026-10-23-backlog-15b-card-interchange-settlement.md`, merged #195 `88e9bf7`.)

## Context
The user asked to "merge 207". PR #207 (`fix/web-deps-vite-mcp-vitest`) clears the 7 open Dependabot alerts in
`web/` (vite 7.3.6, `@modelcontextprotocol/sdk` 1.31.0, `@vitest/mocker` 4.1.11) plus advisories that re-resolving
surfaced (`undici` high, `fast-uri`, `ip-address` moderate). With #191–#193 already merged, it closes the last of the
13 alerts on `main`. Plan mode was switched on before the merge ran, so this plan records what was checked and
what the merge will do.

## Checked against the codebase (read-only, done)
- **PR state:** head `15abdac`, `mergeable: MERGEABLE`, `mergeStateStatus: CLEAN`; every required check passes
  (Backend / Card Service / Web CI gates, Gitleaks, Dependency Review, CodeQL java + js-ts). Non-required
  `Security Audit (npm)`, `Lint & Test (Angular)`, `Build & Deploy → Vercel`, `E2E Tests (Playwright)` also pass.
  `main` has not moved since the branch was cut.
- **Files changed:** `web/package.json`, `web/package-lock.json`, `CLAUDE.md`, `cba-log.md` only.
- **Code impact:** no file in `web/src`, `web/e2e` or web config imports `undici`, `vite`, `vitest`, `@vitest/*`,
  `@modelcontextprotocol/sdk`, `fast-uri` or `ip-address`; there is no `vite.config`/`vitest.config`.
  `angular.json` uses `@angular/build:application` / `dev-server` / `unit-test` with no custom test options, so
  every changed package is reached only through the Angular build tools. No application code changes behaviour.
- **CI:** `web-ci.yml` runs `npm ci` + `npm audit --audit-level=high` on Node 22 — both passed on the PR.
- **Local proof (from the PR):** plain `npm ci`, `npm audit` 0 vulnerabilities, 1170/1170 tests (same as the
  pre-change baseline), production build green; 7 lint errors / 63 warnings are pre-existing (no lint package or
  source changed) and CI lint is report-only.

## Steps
1. **Recheck right before merging:** head still `15abdac`, required checks all pass. Stop if either changed.
2. **Squash-merge #207** pinned to `15abdac` (REST `PUT /pulls/207/merge`, `merge_method=squash` — `gh pr merge`
   failed with a GitHub GraphQL error on #198), delete the branch, fast-forward local `main`.
3. **Production deploy follows automatically:** the push to `main` runs `web-ci.yml` with `vercel build --prod` /
   `deploy --prebuilt --prod` to `cba-web-nine.vercel.app`. Watch that run (Lint & Test, Security Audit, Deploy) and
   report the result; on failure, report the failing step — no automatic rollback or retry.
4. **Dependabot PRs it replaces:**
   - #157 (`@angular/build`/`cli` 21.2.28), #160 (vitest 4.1.12), #158 (`@vitest/coverage-v8` 4.1.12) become
     no-ops; Dependabot closes them itself on its next rebase. Confirm, don't close by hand.
   - **#190 is NOT replaced:** it moves `@angular/cli` to **^22.2.3** and `angular-eslint` to 22.6.0 — a major
     (Angular 22; local Node v24.14.0 is below CLI v22's floor v24.15.0). Leave it open and untouched; report it as a
     separate decision (close it, or plan an Angular 22 upgrade).
5. **Confirm the alerts closed:** Dependabot alerts API shows 0 open (it rescans `main` after the merge).

## Docs
- The PR already updates `CLAUDE.md` (web versions table, Vitest lockfile trap, `undici` overrides) and `cba-log.md`
  (Session 125 cont. 48). Nothing else owed: gate grep shows no endpoint changes.
- Copy this plan to `docs/plans/2026-10-27-merge-207-web-deps.md` + README row (PR #207, merge commit) — committed
  in the **next** PR, since nothing may be committed to `main` directly.

## Verification
- `gh pr view 207` → `MERGED`, merge commit on `origin/main`; branch gone.
- The `main` push run of `web-ci.yml` is green, including Security Audit and the production deploy.
- Dependabot alerts: 0 open. #157/#160/#158 closed by Dependabot; #190 open, reported.
