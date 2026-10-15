> **Status:** merged — PR #151, merge commit `1960dcf`. Approved 2026-10-14.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# Reports screen: API ↔ web contract (category chip + the rest of the same mismatch)

## Context
#150 found that the web reports screen reads `reportCategory` but the API sends `category`, so the category chip and filter never show. Investigating (read-only) showed that this is one symptom of a wider contract mismatch. `GET /api/v1/reports` and `/{id}` return the **raw JPA entity** `report.Report`:

| Web reads (Mifos names) | API sends today (entity) | Effect |
|---|---|---|
| `reportCategory` | `category` | chip and filter always blank |
| `useReport` | `enabled` | — (not used by the UI) |
| `reportParameters` | `parameters` | run modal shows no parameter inputs; the "N params" count is missing |
| param `optional` | param `required` | required markers never shown |

**Suspected crash, to confirm first:** `Report.parameters` ↔ `ReportParameter.report` is bidirectional with no `@JsonIgnore`, and open-session-in-view is on (not configured, so the default applies). Serialising a report that has parameters (V9 seeds two on CustomerAcquisition) should hit infinite recursion and return 500. If so, the whole list has failed since V61 made the rows loadable.

**Also:** the "Create report" modal posts to `POST /api/v1/reports`, which **doesn't exist** (`ReportController` has no create mapping, although `ReportService.createReport` does). The form also sends `reportType: 'Table'`, which the DB CHECK rejects (`TABLE|CHART|SMS|PENTAHO`), and free-text categories the enum can't hold.

Mifos's Reports API uses exactly the web's names (`reportCategory`, `useReport`, `coreReport`, `reportParameters`). CLAUDE.md says to mirror Mifos and never to return entities (`JournalEntryResponse` rule). **So the backend changes to return a DTO, and the web model stays as it is.**

Branch `fix/reports-api-contract` off `main` (`f3f22fe`), one PR. Merge only on the user's word with CI green.

## Decision for the user (default = recommended)
- **2a (recommended):** add `POST /api/v1/reports` (ADMIN), validated, so the existing Create modal works.
  - Mifos allows ADMIN-defined SQL reports.
  - Our SELECT-only check runs at create time and again at run time.
- **2b:** leave create out and hide the Create button. This is smaller, but the endpoint stays missing.

## 1. Backend (`com.cba.report`)
- **New `report/dto/ReportResponse`** record:
  - fields `id, reportName, reportType, reportCategory, description, reportSql, coreReport, useReport, reportParameters`;
  - nested `Parameter(id, parameterName, parameterLabel, parameterType, defaultValue, optional = !required, sortOrder)`, ordered by `sortOrder`;
  - `static from(Report)`.
- **`ReportService`:**
  - `listReports` / `getReport` map to the DTO inside the existing `@Transactional(readOnly)`, so lazy parameters load in-session and the entity never reaches Jackson.
  - Check other callers of `listReports` / `getReport` (grep first) and keep an entity-returning method if anything else uses it.
- **`ReportController`:** the list and get endpoints return `ApiResponse<ReportResponse>`.
- **(2a)** `ReportRequest` record:
  - `reportName` @NotBlank ≤100;
  - `reportType` (default TABLE; must be in TABLE|CHART|SMS|PENTAHO, else `400 INVALID_REPORT_TYPE`);
  - `reportCategory` as a String parsed to `Report.Category` (default MIXED; unknown → `400 INVALID_REPORT_CATEGORY`);
  - `description`, `reportSql` @NotBlank, optional `reportParameters`.
  
  `ReportService.createReport(ReportRequest)` does the following:
  - runs the existing `validateSelectOnly` on the SQL and returns `400 INVALID_REPORT_SQL` if it fails;
  - always sets `coreReport = false`;
  - keeps the duplicate-name check (`REPORT_NAME_EXISTS`);
  - writes an audit entry;
  - returns `201` with a `ReportResponse`.
  
  `POST /api/v1/reports` is `@PreAuthorize("hasRole('ADMIN')")`, like delete.
- No migration is needed: V61 already normalised the categories.

## 2. Web (`web/src/app/features/reports/`)
- `report.service.ts`:
  - `ReportCategory = 'LOAN'|'SAVINGS'|'TELLER'|'CUSTOMER'|'ACCOUNTING'|'MIXED'`;
  - type `reportCategory` with it;
  - `reportType` values in uppercase.
- `reports-list`:
  - `categoryLabel()` (LOAN→"Loans", SAVINGS→"Savings", …, MIXED→"General") for the chip and the filter options; the filter still compares enum values.
  - Create modal (2a): the category free-text input becomes a `<select>` of enum values; type option values become `TABLE|CHART|…` with the labels unchanged; show the API error message (`errors[0].message`, the same helper pattern as loan-detail).
  - (2b instead: remove the Create button and modal.)
- Specs: fixtures use enum values; add tests for `categoryLabel`, the filter and the create payload.

## 3. Tests
- **First (before any fix):** a new `ReportApiIT` over real HTTP (`TestRestTemplate`, test-profile auth bypass) calls `GET /api/v1/reports`, to confirm the suspected 500. Record the result in cba-log.
- `ReportApiIT` after the fix:
  - list returns 200 with all 7 seeded reports;
  - CustomerAcquisition has `reportCategory = CUSTOMER` and 2 `reportParameters` with `optional = true`, and no `category`, `enabled` or `parameters` keys;
  - get by id;
  - (2a) POST creates a report (201, then visible in the list); DML SQL → 400; unknown category → 400; duplicate name → 400; a TELLER gets 403;
  - deleting a core report → 400.
- `ReportServiceTest`: update for the DTO and request.
- Regenerate `backend/docs/openapi-snapshot.yaml` (the Report schema changes).

## 4. Docs
- `docs/api-reference.html` and the docs-site copy: the reports rows/section get the response shape, `reportCategory` values and (2a) POST, body and errors.
- Postman and the docs-site copy: list and get examples; (2a) a "Create report" request.
- `CLAUDE.md`:
  - Reports module 15 (DTO, Mifos names, POST);
  - remove the "Web reads `reportCategory`… (follow-up)" note;
  - versions row.
- `cba-log.md` cont. 32 with the gate grep proof (tracked + untracked).

## 5. Verification
1. On the branch, before any edits: run `ReportApiIT`'s GET test against the current code and confirm the 500 or record what actually happens.
2. `cd backend && ./mvnw -o clean verify -Pfull-integration` must be all green (861 + new).
3. `cd web && CI=true npx ng test --no-watch` + `npm run build`.
4. Gate grep, docs, PR, CI green, then merge on the user's word.
