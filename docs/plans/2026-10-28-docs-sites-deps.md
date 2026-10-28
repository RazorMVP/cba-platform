> **Status:** merged — PR #208, merge commit `aa4bb86`. Approved 2026-10-28 in plan mode (user: "1 first then we do 2(plan an Angular 22 upgrade)"). Body verbatim.

# Clear the 12 new Dependabot alerts in docs-site and partner-docs

> Status: awaiting approval. (Previous content of this file — the "merge #207" plan, approved and done: #207 merged
> `3a689ef`, prod deploy green — is copied into `docs/plans/` by this PR, see Docs.)
> Next after this: plan the Angular 22 upgrade (#190) — a separate plan.

## Context
After #207 cleared the original 13 alerts, 12 new ones opened on `main`, 6 per app, identical in
`docs-site/` and `partner-docs/` (both Docusaurus 3.10.2):

| Package | Have | Fixed in | Alerts per app | Pulled in by (range) | Runs when |
|---|---|---|---|---|---|
| `body-parser` | 1.20.8 | 1.20.9 | 1 high (GHSA-27r3-jxmh-p828, DoS) | `express` 4.22.1 (`~1.20.3`) | local dev server only (`docusaurus start`/`serve`) |
| `fast-uri` | 3.1.8 | 3.1.9 | 2 medium + 2 low | `ajv` 8.x (`^3.0.1`) | schema validation during build |
| `serialize-javascript` | 7.1.2 | 7.1.3 | 1 low (GHSA-m5mm-p8h7-m29v, XSS) | `copy-webpack-plugin` / `css-minimizer-webpack-plugin` (`^6`), **forced to v7 by our `overrides` `^7.1.2`** (cont. 37) | during build |

Nothing here ships in the deployed static sites, but the alerts are real and Dependabot would keep opening PRs.
`follow-redirects` 1.16.1 → **1.16.2** (via `http-proxy`, `^1.0.0`) has Dependabot PRs (#199, #205) but no alert;
included since it's the same in-range lockfile update.

Existing Dependabot PRs: #206/#201 (body-parser), #204/#200 (fast-uri), #199/#205 (follow-redirects) — one per
package per app; none fixes `serialize-javascript` (the floor is in our `overrides`, which Dependabot doesn't raise).
One PR for both apps replaces all six.

## Changes (branch `fix/docs-sites-deps`, one PR)
For **each** of `docs-site/` and `partner-docs/`:
1. `package.json` `overrides`: `"serialize-javascript": "^7.1.2"` → `"^7.1.3"` (the floor makes the fix durable;
   other overrides unchanged).
2. Lockfile only, in range: `npm install --package-lock-only` (picks up the new floor), then
   `npm update body-parser fast-uri follow-redirects --package-lock-only`. **Never `npm audit fix`** here — it
   "fixes" by downgrading `@docusaurus/core` to 3.7.0 (CLAUDE.md, partner-docs row). If npm crashes (`edgesOut`),
   use the cont. 48 sequence: `--legacy-peer-deps` lock-only, then a plain `--package-lock-only`, then `npm ci`.
3. Expected lockfile diff: only `body-parser`, `fast-uri`, `follow-redirects`, `serialize-javascript` versions
   (+ any of their own sub-deps that move). Anything else moving → stop and look before continuing.

## Verification (per app)
- Plain `npm ci` succeeds (strict peers).
- `npm audit` → 0 for these four packages; record what remains (known: `braces` ≤ 3.0.3, no fix, build-time globbing)
  and confirm no new high/critical.
- `npm run build` succeeds.
- **Output unchanged** (same check as cont. 37): build `main` and the branch, compare `find build -type f | sort`
  (same file set) and `sha256sum` of every `build/**/*.css` (byte-identical). JS chunk names come from content
  hashes — list any that differ; a difference must be explained before the PR is opened.
- CI on the PR: `docs-ci.yml` / `partner-docs-ci.yml` build jobs green; required gates green. (`Deploy Preview`
  fails on Dependabot PRs for lack of secrets; on this PR it should pass.)
- After merge: Dependabot alerts on `main` → 0 open; #199 #200 #201 #204 #205 #206 closed by Dependabot (don't
  close by hand).

## Docs (in the same PR)
- `CLAUDE.md`: partner-docs row — overrides list `serialize-javascript ^7.1.3`; note body-parser/fast-uri/
  follow-redirects patched (cont. 49); docs-site shares the same overrides.
- `cba-log.md`: new entry "Session 125 (cont. 49)" — table of the four packages, verification results, proof line
  "API surface unchanged — verified via gate grep; no api-reference/postman edits owed."
- `docs/plans/`: add `2026-10-27-merge-207-web-deps.md` (the approved #207 merge plan, status merged `3a689ef`)
  and `2026-10-28-docs-sites-deps.md` (this plan); two rows in `docs/plans/README.md`.
- Merge only on the user's "merge <PR>" with CI green.
