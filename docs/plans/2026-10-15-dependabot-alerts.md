> **Status:** merged — Step 0 #155 (`8f50fe8`); Step 1 #154 (`0ba2ff5`); Step 2 #156 (`c50a95e`); Step 3 dismissals done 2026-10-19 (jasypt #2/#4 `not_used`; braces #222/#236 + Trivy #1024/#1036; sprintf-js left both lockfiles). Approved 2026-10-15.

# Merge #154, then clear the 222 Dependabot alerts

## Context
- **#154** (card-service → backend token, atomic card holds, second security-review fixes `0da131f`) is MERGEABLE.
  - Every required gate passes: Backend, Card Service, FEP, Web, Gitleaks, Dependency Review and both CodeQL checks.
  - The only red check is **SonarCloud**, which fails on `main` too (run 38730967432). It isn't configured: backlog 9, no `SONAR_TOKEN`.
  - The user said "merge 154".
- **Dependabot shows 222 open alerts on `main`** (10 critical, 106 high). They were investigated read-only:

| Manifest | Alerts | Severity |
|---|---|---|
| `docs-site/package-lock.json` | 77 | 5 crit, 40 high |
| `partner-docs/package-lock.json` | 77 | 5 crit, 40 high |
| `partner-portal/package-lock.json` | 65 | 26 high |
| `backend/pom.xml` | 2 | poi-ooxml medium, jasypt low |
| `card-service/pom.xml` | 1 | jasypt low |

- **Why they piled up:**
  - `dependabot.yml` watches npm only in `/web`.
  - None of the three npm apps' CI (`docs-ci.yml`, `partner-docs-ci.yml`, `partner-portal-ci.yml`) runs `npm audit`.
  - `web/` itself is clean.
- **Real exposure** (this decides the order of work):
  - **docs-site and partner-docs** are static Docusaurus sites. The criticals live in the build and dev-server toolchain, which never ships in the deployed HTML/JS: `proxy-addr`, `compression` and `websocket-driver` (webpack-dev-server), `shell-quote`, `tinypool` (SSG workers).
  - **partner-portal** ships `axios` (1.15.x; fixes up to 1.20.0) and `form-data` in the browser bundle. `vite`, `postcss`, `undici`, `js-yaml` and `nanoid` are dev-only.
  - **jasypt (low):** the advisory covers a one-way hash without salt (digesters). `FieldEncryptor` uses PBE encryption with `RandomSaltGenerator` and `RandomIvGenerator`, so the vulnerable code is not used. 3.0.5 is the latest release, so there is no fix.
  - **poi-ooxml 5.3.0** (medium, OOXML input validation): used by the XLSX report export. 5.5.1 is current.
- **`npm audit fix` is the wrong tool for Docusaurus.** For the 15 "major" fixes it proposes *downgrading* `@docusaurus/core` to 3.7.0.

## Step 0 — Plan archive in the repo (user chose "2": in-repo, one file per plan, recover past plans)
Plan mode keeps one file outside the repo (`~/.claude/plans/<random>.md`) and overwrites it with each new plan, which already happened to the #154 plan. From now on every approved plan is also kept in git.
- **New folder `docs/plans/`**, one file per plan: `YYYY-MM-DD-<topic>.md`, dated by its approval.
- **Recover 9 plans word for word** from this session's transcript. The source is the `input.plan` of each approved `ExitPlanMode` call in `~/.claude/projects/-Users-razormvp-CoreBanking/ae6ed000-….jsonl` (the only transcript of the 91 that has any). Where a plan appears more than once, take the latest version.
  - `2026-10-13-gl-posting-2c-ifrs9-impairment.md` (#145)
  - `2026-10-13-card-service-realm-roles.md` (#148)
  - `2026-10-14-gl-posting-2d-interest-waivers.md` (#149)
  - `2026-10-14-reports-api-contract.md` (#151)
  - `2026-10-14-backend-card-service-token.md` (#152)
  - `2026-10-15-backlog-11-card-internal-auth.md` (#153)
  - `2026-10-15-backlog-12-card-backend-token.md` (#154)
  - `2026-10-15-card-holds-reservation.md` (#154 extension)
  - `2026-10-15-dependabot-alerts.md` (this plan, copied after approval)
- **Header per file:** status (merged / open), PR number and merge commit, and "recovered from session transcript" where that applies. The body stays verbatim.
- **Gaps stated, not invented:** the overdue fix (#150) and earlier work had no approved plan. `docs/plans/README.md` lists every plan in a table and points to `cba-log.md` for the rest.
- **Public repo check before commit:** grep the files for secret-shaped strings (token, secret, password values, the `TYPESAFE_API_KEY` and Vercel bypass values). The plans should hold none; if one does, redact it.
- **Make it stick:**
  - add a `CLAUDE.md` rule: after `ExitPlanMode` is approved, copy the plan to `docs/plans/` and commit it with that work's PR;
  - save a feedback memory, so future sessions do the same.
- **Its own small PR** `docs/plan-archive` (docs only), opened before the Dependabot PR.

## Step 1 — Merge #154
1. `gh pr merge 154 --squash --delete-branch` (repo convention: squash, as in #150–#153).
2. `git checkout main && git pull`.
3. Confirm `main` CI starts.

## Step 2 — One PR `fix/dependabot-npm-java` (branch off updated main)

**partner-portal** (all 18 audit findings are in-range):
- Raise the floor to `axios ^1.20.0`.
- Clean re-resolve: `rm -rf node_modules package-lock.json && npm install`.
- `npm audit --audit-level=high` → 0.

**docs-site and partner-docs** (identical manifests; same steps for both):
1. `@docusaurus/*` exact `3.10.0` → `3.10.2` (latest stable), then a clean re-resolve. This clears the in-range majority: proxy-addr 2.0.8, websocket-driver 0.7.5, shell-quote 1.12, fast-uri, js-yaml, nanoid, postcss, brace-expansion, compression, http-cache-semantics 4.3.0, joi, svgo and others.
2. **`tinypool`:** Docusaurus 3.10.2 still declares `^1.0.2`, and the fix exists only in 2.x. Add `"overrides": {"tinypool": "^2.2.0"}` and keep it **only if** `docusaurus build` succeeds and produces the same output: page count, no SSG errors, and a spot-check of three pages. Otherwise drop the override, and the alert goes to the dismissal list (build-time only).
3. Re-run `npm audit --audit-level=high` and record what remains. Expected leftovers have no upstream fix at all: `braces` ≤3.0.3 (latest is 3.0.3) and `sprintf-js` ≤1.1.3 (latest).

**backend:** `poi-ooxml` 5.3.0 → 5.5.1. Run `ReportExportServiceTest` and the full suite. Check that `poi` / `poi-ooxml` / `xmlbeans` / `commons-compress` don't conflict with the OWASP pins.

**Prevent recurrence:** add npm entries to `.github/dependabot.yml` for `/docs-site`, `/partner-docs` and `/partner-portal`, using the same weekly schedule and major-version ignore as `/web`.

**Not in this PR (maintainer decision, per CLAUDE.md):** a blocking `npm audit` gate for the three apps. I'll recommend it in the PR body only. Making the gate blocking would deadlock Dependabot the way it did on `/web`.

## Step 3 — Dismiss alerts that have no fix (needs the user's OK; dismissal is visible on GitHub)
Dismiss after the PR merges, each with a written reason:
- **jasypt** ×2 (alerts #2, #4): `not_used`. We use salted, IV'd PBE encryption, not the unsalted digester; 3.0.5 is the latest release.
- **braces** and **sprintf-js** in both docs sites (and **tinypool**, if the override fails): `tolerable_risk`. Build-time only, inputs are our own repo files, nothing is deployed, and no upstream fix exists.

## Docs
- `cba-log.md`: new entry "cont. 36", with before/after alert counts per manifest and the gate-grep proof line ("API surface unchanged").
- `CLAUDE.md`: versions table rows (partner-docs Docusaurus 3.10.2, backend POI 5.5.1), a Dependabot section noting npm coverage for the three apps, and the "audit fix downgrades Docusaurus" trap.
- No api-reference or Postman changes (no endpoints touched).

## Verification
- `partner-portal`: `npm test` (78), `npm run build`, `npm audit --audit-level=high` → 0.
- `docs-site` and `partner-docs`: `npm run build` (Docusaurus SSG), page count before and after, `npm audit` before and after counts.
- backend: `./mvnw -o clean verify -Pfull-integration` → 902/902, plus the OWASP gate in CI (POI's new transitive dependencies).
- After merge: the Dependabot alert count on `main` drops from 222 to about 6 or fewer, all of them on the dismissal list.
- Merge only on the user's word with CI green.
