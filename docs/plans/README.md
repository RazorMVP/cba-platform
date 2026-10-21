# Implementation plans

Every change that goes through plan mode has its approved plan kept here, one file per plan, named `YYYY-MM-DD-<topic>.md` (the approval date). Plan mode itself writes to a single file outside the repo (`~/.claude/plans/<random-name>.md`) and overwrites it with the next plan, so this folder is the durable copy.

**Rule:** once a plan is approved, copy it here and commit it with the PR that implements it. Keep the body verbatim; add a status line at the top and update it when the work merges.

| Approved | Plan | PR | Merge commit |
|---|---|---|---|
| 2026-10-13 | [GL posting 2c — IFRS 9 impairment, write-off, CBN reserve](2026-10-13-gl-posting-2c-ifrs9-impairment.md) | #145 | `73d209f` |
| 2026-10-13 | [card-service — Keycloak realm roles](2026-10-13-card-service-realm-roles.md) | #148 | `2540fea` |
| 2026-10-14 | [GL posting 2d — interest waivers + LOANS opening balances](2026-10-14-gl-posting-2d-interest-waivers.md) | #149 | `298975a` |
| 2026-10-14 | [Reports API ↔ web contract](2026-10-14-reports-api-contract.md) | #151 | `1960dcf` |
| 2026-10-14 | [Backend → card-service service token](2026-10-14-backend-card-service-token.md) | #152 | `a894869` |
| 2026-10-15 | [Backlog 11 — card-service internal endpoints auth](2026-10-15-backlog-11-card-internal-auth.md) | #153 | `d866430` |
| 2026-10-15 | [Backlog 12 — card-service → backend token + balance endpoint](2026-10-15-backlog-12-card-backend-token.md) | #154 | `0ba2ff5` |
| 2026-10-15 | [Card approvals reserve funds (atomic hold)](2026-10-15-card-holds-reservation.md) | #154 | `0ba2ff5` |
| 2026-10-15 | [Merge #154, clear the Dependabot alerts](2026-10-15-dependabot-alerts.md) | #155, #154, #156 | `c50a95e` |
| 2026-10-19 | [Backlog 18 — partner-portal React Router 6 → 7](2026-10-19-backlog-18-react-router-7.md) | #183 | `bdc5a5d` |
| 2026-10-19 | [Backlog 16 + 6 — backend rate limiter on verified identity and the partner's tier](2026-10-19-backlog-16-6-rate-limit.md) | #188 (replaced #187) | `afabb47` |

**Not here:** work done without an approved plan, including the overdue fix (#150, `f3f22fe`) and everything before 2026-10-13. Plans from those sessions weren't kept. `cba-log.md` is the record for them.
