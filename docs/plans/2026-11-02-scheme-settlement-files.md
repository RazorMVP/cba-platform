# Backlog 21: correct the card scheme settlement files (card-service)

> Status: approved 2026-11-02 (no D1 reply → default **D1a**); in progress on `fix/card-settlement-files`.

## Context
`docs/deferred-backlog.md` item 21 lists defects in the nightly settlement-file export. Today they're harmless, because every exporter
is `enabled: false`. Once a scheme is switched on, they send wrong amounts and lose late batches. Goal: the
export produces correct files and can't hold a DB connection for minutes. It must also not send presentment files for a
bank that only **issues** cards.

## What was checked (read-only)
| # | Finding | Where |
|---|---|---|
| 1 | `settlement_items.amount` = `auth.getAmount()` = **DE4 minor units**, stored `NUMERIC(19,4)` (2500 → `2500.0000`). Visa, Mastercard and UnionPay write `toPlainString().replace(".","")` → `25000000` (**×10⁴**). Their trailer totals use `longValue()` (correct), so each file contradicts itself | `SettlementService.java:113`, `VisaBase2Exporter:71`, `MastercardIpmExporter:83`, `UnionPayCupsExporter:92` |
| 2 | Verve (NIBSS) and Afrigo (PAPSS) write `2500.0000`, which is neither minor nor major units, and default a missing currency to `"566"`. Visa defaults to `"840"`. CLAUDE.md's multi-currency rules forbid both defaults | `VerveNibssExporter:78`, `AfrigoPapssExporter:67`, `VisaBase2Exporter:72` |
| 3 | The export SQL takes interchange, fee and net from `interchange_log`: acquirer **minor** units before V13, issuer **major** units after. `settlement_items` already holds the 15b figures (`interchange_amount`, `scheme_fee_amount`, `gl_net`), all in minor units | `SettlementFileExportService.buildExportRecords`, V13 §3 and §5 |
| 4 | `SettlementExportRecord` javadoc says "minor units": true for the item data, false for `interchange_log` | `SettlementExportRecord.java` |
| 5 | The nightly run takes only CLOSED batches with `settlement_date = today`. A batch that closes late is never exported automatically | `runNightlyExport` |
| 6 | `exportBatch` is `@Transactional` and `Thread.sleep`s 300 s between up to 3 attempts, holding a connection for ~10 min. Nothing stops the nightly run and a manual trigger from sending the same file twice: the unique index only covers `TRANSMITTED` | `executeWithRetry`, V7 `idx_transmissions_batch_scheme_success` |
| 7 | The manual trigger exports an **OPEN** batch, which sends a partial file and then blocks the full one. It stamps **today's** date on a past batch; the web calls it with no date | `exportBatch`, `SettlementExportController:51`, `web/.../cards.service.ts:405` |
| 8 | **Issuer vs acquirer.** All 5 formats are acquirer presentments: BASE II clearing records, IPM First Presentment 1240, and the NIBSS/PAPSS/CUPS acquirer files. Under the schemes' rules the **acquirer** submits clearing records; the **issuer** receives them and sends back chargebacks (IPM 1442), retrieval requests and fee collections (1740). Sources: Visa Core Rules (clearing records submitted by the Acquirer); Mastercard *IPM Clearing Formats*. card-service only ever authorizes **this bank's own cards**: the FEP routes every message to it, and an unknown card gets RC 14. No acquired-transaction data exists, so these files would present the bank's own cardholders' spend | backlog 21, FEP routing in CLAUDE.md |

## Decision for you — D1: what an issuer-only bank does with the presentment export
- **D1a (recommended):** fix every defect below, **and** add `card.settlement.export.role` (`ISSUER` default | `ACQUIRER`).
  Under `ISSUER`:
  - the nightly run and the retry poller do nothing (one INFO line each);
  - a manual export answers `409 PRESENTMENT_REQUIRES_ACQUIRER_ROLE`;
  - nothing is sent even if someone flips a scheme to `enabled: true`.

  The fixed serializers and transmitter stay ready for an acquiring business. A **new backlog item 22** records what an issuer does send (chargebacks from the dispute module, fee collections), which needs the scheme specs, like item 7.
- **D1b:** fix the defects only. The files stay presentment-shaped, and enabling a scheme would present issuer transactions.
- **D1c:** delete the presentment export (5 exporters, nightly job, export endpoint) and keep only the transmitter. This removes an API and the web "Export" button.

Default if approved without a reply: **D1a**.

## Steps (branch `fix/card-settlement-files`, one PR)
1. **Amounts.** Add a package-private helper `SettlementAmounts.minor(BigDecimal)`: `setScale(0, RoundingMode.UNNECESSARY)`. It throws on a fractional minor amount (bad data fails loudly) and maps null to 0.
   - All 5 exporters use it for every data row and every header/trailer total.
   - NIBSS and PAPSS rows switch to minor-unit integers too, matching their own header totals. Their real specs are unconfirmed; recorded in the javadoc.
   - A record with no currency throws `IllegalArgumentException` (no `840`/`566` default).
2. **Record source.** `buildExportRecords` drops the `interchange_log` lateral join and reads the item's own minor-unit figures:
   - `interchange_amount` and `scheme_fee_amount` (0 when null);
   - net = `COALESCE(gl_net, amount)`;
   - scheme = `COALESCE(si.scheme, <existing BIN scan>)`.

   Fix the `SettlementExportRecord` javadoc: all amounts minor units, issuer view.
3. **No sleep, no long transaction.** Remove `@Transactional` from `exportBatch`; each status write is its own repository save.
   - **Claim** (V14): replace the TRANSMITTED-only unique index with one on `(batch_id, scheme) WHERE status IN ('PENDING','SENDING','TRANSMITTED','ACKNOWLEDGED')`. Inserting the PENDING row is the claim; a duplicate-key error means another run owns it, so skip.
   - **Attempt:** PENDING → SENDING (version-checked save), then export + transmit.
     - Success → `TRANSMITTED`.
     - Failure → back to `PENDING` with `next_retry_at = now + retry-delay` (new column).
     - After `max-retries` → `FAILED`.
   - **Retry poller:** `@Scheduled(fixedDelay)` picks PENDING rows that are due and rebuilds the records. This is the same pattern as `WebhookDeliveryService.retryDueDeliveries`.
   - **Crash mid-send:** a `SENDING` row older than 30 min becomes `FAILED` with "INTERRUPTED: confirm with the scheme whether `<file>` arrived before re-exporting". It is **never auto-resent**, since a duplicate presentment is worse than a late one.
   - V14 also turns any leftover `PENDING` row into `FAILED` before creating the index.
4. **Late batches.** The nightly run takes `CLOSED` or `SETTLED` batches with `settlement_date` within `lookback-days` (default 7, new property) that have no live transmission per enabled scheme. Filenames and headers use the **batch's** settlement date.
5. **Manual trigger.**
   - Refuses an `OPEN` batch: `409 BATCH_NOT_CLOSED`.
   - `settlementDate` becomes optional and defaults to the batch's date; a different date → `400 SETTLEMENT_DATE_MISMATCH`.
   - The first attempt runs synchronously and returns `TRANSMITTED` or `PENDING` with `nextRetryAt`.
6. **Role guard (D1a).** `role` property in `SettlementExportProperties` + `application.yml` (`ISSUER`); checked first in nightly, poller and manual paths.
7. **Web.** `SettlementTransmission` type gains `nextRetryAt` and the `SENDING` status. The status badge maps SENDING → info. The export button shows the 409 message instead of failing silently.

## Tests
- Exporter tests (`SchemeExportersTest`, `VisaBase2ExporterTest`) build amounts at **scale 4** (`new BigDecimal("2500.0000")`), the shape the DB returns. Assert the exact field (`000000002500`) and that it matches the trailer total. Add: fractional minor amount → exception; no currency → exception.
- `SettlementFileExportServiceIntegrationTest` (Testcontainers): figures from the item columns, not `interchange_log`; a scale-4 amount in, minor integer out.
- New service tests:
  - role ISSUER → nothing sent, manual → 409;
  - OPEN batch → 409; date mismatch → 400;
  - failed attempt → PENDING + `nextRetryAt` with no sleep (the test finishes in milliseconds);
  - poller retries a due row;
  - max retries → FAILED;
  - stale SENDING → FAILED, not resent;
  - second concurrent claim → skipped (integration, real unique index);
  - a batch closed yesterday is exported today with yesterday's date.
- Snapshot: `CardOpenApiSnapshotTest`, regenerated with `-Dupdate.api.snapshot=true` (the transmission shape gains `nextRetryAt`).

## Verification
- `cd card-service && ./mvnw -o test` (unit), then `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration`: all green, new count reported (from 232).
- `cd web && CI=true npx ng test --no-watch` + `npm run check:change-detection` + production build.
- PR CI: Card Service CI gate, Web CI gate, CodeQL, Gitleaks, Dependency Review.

## Docs (same PR)
- `docs/card-api-reference.html` + `docs-site/static/` copy: the export endpoint's new errors (409 ×2, 400), its date default, transmission statuses (`SENDING`) and `nextRetryAt`.
- Postman collection + static copy: same endpoint, optional `settlementDate`, the new error examples.
- `CLAUDE.md`:
  - card-service versions row (cont. 53, test count);
  - Settlement File Export section: role guard, minor units, claim/retry, lookback, gotchas.
- `cba-log.md`: cont. 53 entry with the gate grep output.
- `docs/deferred-backlog.md`: item 21 → ✅ DONE; new **item 22**, issuer outbound files (chargebacks / retrieval / fee collections).
- `docs/plans/2026-11-02-scheme-settlement-files.md` + README row.
- Merge only when you say "merge <PR>" and CI is green.
