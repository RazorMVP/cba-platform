# Backlog 9 — drop SonarCloud, enforce the 70% backend coverage rule with JaCoCo

> Status: approved 2026-11-17 (user: "Let's Fix #9 first and use option 2"). Merged #260 `2342f4b` (2026-11-17); CI ran the check for the first time — 1126/1126, "All coverage checks have been met".

## Context

| Problem | Finding |
|---|---|
| The `sonar` job failed on every backend run (~15 s, `SONAR_TOKEN` / `SONAR_ORG` never set) | `continue-on-error: true`, so it blocked nothing — red noise only |
| CLAUDE.md: "Backend: 70% line coverage minimum" | **Never enforced.** `backend/pom.xml` already had the JaCoCo `check` rule (BUNDLE, LINE ≥ 0.70) but it runs in `verify`: `mvn test` never reaches it, the CI integration run passed `-Djacoco.skip=true` (`b7ccb71`, 2026-04-23, on the mistaken belief that unit tests had checked it), and the sonar job passed `-Djacoco.check.skip=true` |
| JaCoCo 0.8.12 can't instrument Java 25, so the rule couldn't run locally either | 0.8.15 (Maven Central) can |

Options offered: 1 set up SonarCloud, 2 drop it and enforce coverage in Maven, 3 leave it. Chosen: **2**.

## Measured before switching it on (2026-11-17)
`./mvnw clean verify -Pfull-integration -Djacoco.version=0.8.15 -Djacoco.haltOnFailure=false` — 1126/1126 tests:
- under the rule's excludes: **82.4%** line coverage (6997 of 8487 lines) → "All coverage checks have been met"
- every class, no excludes: 74.3%

So the rule can block from day one without turning builds red.

## Changes
- `.github/workflows/backend-ci.yml`: remove the `sonar` job (not in the gate's `needs`, nothing depends on it); drop `-Djacoco.skip=true` from the full-integration step so `check` runs over unit + integration coverage; upload `backend/target/site/jacoco/` as artifact `backend-coverage-report` (replaces the dashboard).
- `backend/pom.xml`: `jacoco.version` 0.8.12 → 0.8.15; comment on where the rule runs.
- Delete `sonar-project.properties` (only the SonarCloud scanner read it).
- `.env.example`, `docs/integration-runbook.md`: remove the `SONAR_TOKEN` / `SONAR_ORG` entries.
- Docs: `docs/deferred-backlog.md` item 9 → done; CLAUDE.md (pipeline table, coverage + security-testing tables, secrets/variables, setup checklist, Java 25 JaCoCo note, versions table); cba-log cont. 72.

## Not changed
- The excludes list (controllers, config, filters…) — unchanged; narrowing it is a separate decision.
- Web coverage (the "70% Web" line in CLAUDE.md) — Vitest has no threshold; out of scope.
- What Sonar did beyond coverage (smells, duplication) is not replaced. Security analysis stays with CodeQL, SpotBugs, OWASP, Dependabot, Trivy, Gitleaks.

## Verification
1. Local full-integration run with the rule on: 1126/1126, `jacoco:0.8.15:check` — "All coverage checks have been met".
2. `./mvnw -o jacoco:check@check` from the edited pom resolves 0.8.15 and passes.
3. CI on the PR: `Test (Java 21 + Testcontainers)` green with the check, `backend-coverage-report` artifact present, no SonarCloud job; required gates green; merge on "merge N".
