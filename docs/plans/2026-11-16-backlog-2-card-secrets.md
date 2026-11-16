# Backlog 2, PR B (encrypt card secrets at rest) and PR C (full PAN in acquirer files)

> Status: approved 2026-11-16 (user: "A now / B, then C"; plan approved; decisions D1a, D2a). PR A (#253, `7496e7d`) merged before this plan. PR B #254 merged `38710ab` (2026-11-16). PR C #255 merged `3001968` (2026-11-16); its CI run surfaced a wallet-limit race from #241, fixed first in #256 (`999972c`). Backlog 2 done. Backlog 14 stays on the backlog.

## Context

| Problem today | Outcome |
|---|---|
| `cards.pan_encrypted`, `cards.cvv_encrypted` and `token_vault.dpan_encrypted` hold **clear values**: card-service has no converter, and `@EnableEncryptableProperties` only decrypts `ENC(...)` config values. Anyone with a database dump, backup or read access has every card number | The PAN and DPAN are stored as ciphertext (`ENC1:` + Jasypt AES-256), decrypted only where needed |
| The CVV is a **random** 3-digit number made at issue (`CardService.java:66`), stored, never returned, never read. A real CVV comes from the card keys in the HSM (`verifyCvv`, `CW`), so the stored value could never even match | It isn't stored (D1) |
| card-service starts with any key, including the public dev default `dev-encryption-key-change-in-prod` or the unedited `<CHANGE_ME_MIN_32_CHARS>` from `platform-secrets.yaml` | It refuses to start with a known public or short key, unless dev mode says so (D2) |
| Settlement files carry only the masked PAN (backlog 2) | PR C: the full PAN in the file body, only when acquiring is switched on |

**Packaging:** two PRs. Each merged on your "merge N" with CI green.

| PR | What | Migrations |
|---|---|---|
| **B** | Encrypt PAN and DPAN, drop the CVV, key guard | card-service V23 (SQL), V24 (Java) |
| **C** | Full PAN in acquirer settlement files | none |

## Rules checked
PCI DSS v4.0: **§3.5.1** a stored PAN must be unreadable (strong cryptography); **§3.3.3** an issuer may keep sensitive authentication data (CVV) only with a legitimate issuing business need, and protected; **§3.6/§3.7** keys protected and not default/known values; **§3.2.1** keep no more cardholder data than needed.

## Decisions for you
Reply "go" to take every recommendation, or name one to change it (e.g. "D1 b").

**D1. The CVV.**
- **a (recommended):** stop storing it. Drop `cards.cvv_encrypted`; remove `cvv` from the staff issue request (`CardController.IssueCardRequest`) and the random CVV from the BaaS issue path. A client that still sends `cvv` is not refused (Jackson ignores unknown fields). Nothing reads it, and a real CVV is computed and checked by the HSM.
- b: keep it and encrypt it like the PAN.

**D2. The encryption key.**
- **a (recommended):** card-service refuses to start when `jasypt.encryptor.password` is blank, shorter than 32 characters, or one of the known public values (`dev-encryption-key-change-in-prod`, `dev_encryption_key_at_least_32_chars`, `<CHANGE_ME_MIN_32_CHARS>`, `test-encryption-key-do-not-use-in-production`), unless `card.crypto.allow-dev-key=true` (env `CARD_ALLOW_DEV_ENCRYPTION_KEY`). That switch is set in the test profile, docker-compose and the documented dev-run command, and never in Kubernetes.
- b: log a warning and start anyway.

## PR B — encrypt at rest

**My calls (say if you disagree):**
- **Decrypt explicitly, not with a JPA converter.** A converter would decrypt the PAN on every card load, the authorization path included, and hand the clear PAN to any code holding a `Card`. Instead a small `CardSecrets` component encrypts on write and decrypts at the two places that need the clear PAN (de-tokenize; PR C's export). The entity field holds ciphertext, which finally matches the column name.
- **Ciphertext carries a version prefix `ENC1:`.** Reads require it and fail closed on anything else, so a value that isn't proven ciphertext is never treated as one. The version leaves room for key rotation later.
- **Existing rows are migrated by a Flyway Java migration**, before the app serves anything. A startup job would leave a window where reads hit clear values.

**Files:**
- NEW `card-service/…/card/CardSecrets.java` (`@Component`): `encrypt(String)` → `"ENC1:" + encryptor.encrypt(v)`; `decrypt(String)` requires the prefix (else `IllegalStateException`). Uses jasypt-spring-boot's `StringEncryptor` bean (`jasyptStringEncryptor`, already configured: `PBEWITHHMACSHA512ANDAES_256`, random IV, `application.yml` §jasypt). No new dependency.
- NEW `V23__drop_card_cvv.sql` (D1a): `ALTER TABLE cards DROP COLUMN cvv_encrypted`.
- NEW `db/migration/V24__EncryptCardSecrets.java` (`BaseJavaMigration`, a Spring bean so Spring Boot hands it to Flyway with the encryptor injected; verify Boot 3.5's `FlywayAutoConfiguration` picks up `JavaMigration` beans): for every `cards.pan_encrypted` and `token_vault.dpan_encrypted` not starting `ENC1:`, unwrap a V2 demo `ENC(...)`, require 12–19 digits (anything else fails the migration: unknown state), write `ENC1:` + ciphertext, in batches. Logs counts only.
- `card/Card.java`: drop `cvvEncrypted`; comments say ciphertext.
- `card/CardService.java`: `setPanEncrypted(cardSecrets.encrypt(pan))`; the `cvv` parameter and the random CVV go.
- `card/CardController.java`, `openbanking/CardApiController.java`: `cvv` out of the issue request records.
- `token/TokenService.java`: tokenize encrypts the DPAN; `detokenize` returns `cardSecrets.decrypt(card.getPanEncrypted())`. Fix the wrong "Jasypt encrypts at persistence" comments.
- `bureau/CdpGenerator.java`: `panEncryptedForBureau` stays the stored value (now card-service ciphertext); comment says a bureau still can't decrypt it (backlog 7).
- NEW `config/EncryptionKeyGuard.java` (D2a), checked at startup; `application.yml` `card.crypto.allow-dev-key: ${CARD_ALLOW_DEV_ENCRYPTION_KEY:false}`; `application-test.yml` true; `infrastructure/docker-compose.yml` card-service env true.

**After deploying (runbook, `docs/integration-runbook.md`):** PostgreSQL keeps the old row versions (with the clear values) until vacuumed: run `VACUUM FULL cards, token_vault` in a quiet window. Expire or re-encrypt backups and dumps taken before PR B. Neither can be done from the code.

**Tests:**
- `CardSecretsTest`: round trip; two encryptions of one PAN differ (random IV); a value without `ENC1:` is refused; null stays null.
- `EncryptionKeyGuardTest`: each known value, blank and 31 characters refused; 32+ unknown accepted; the switch allows dev keys.
- NEW `CardSecretsMigrationIntegrationTest`: insert clear and `ENC(...)` rows, run the migration, then every value starts `ENC1:`, decrypts to the original, and contains no PAN digits; a second run changes nothing; a non-digit value fails the migration.
- `CardPanExposureIntegrationTest`: a card issued through the API is stored as `ENC1:…` without its PAN digits; the response still masks.
- `TokenServiceTest`, `CardServiceTest`: encrypt/decrypt via `CardSecrets`; DPAN stored encrypted. Fixtures inserting `cvv_encrypted` (`SettlementFileExportServiceIntegrationTest`) drop that column.

## PR C — full PAN in acquirer files (after B)

- `SettlementFileExportService.buildExportRecords`: the SQL also selects `c.pan_encrypted`; each record's `pan` = `cardSecrets.decrypt(...)` (the masked PAN stays in `maskedPan` for logs and audit). Reached only under `card.settlement.export.role=ACQUIRER`; the default `ISSUER` exports nothing (item 21).
- Files stay in memory (never written to disk), are sent over pinned SFTP or HTTPS with optional mTLS, and `settlement_transmissions` stores no file bytes — checked, unchanged.
- Tests: an ACQUIRER export's file body contains the full PAN; the service's log output and the transmission row don't. Under ISSUER, nothing is built.
- Docs: switching to ACQUIRER is the moment PCI scope grows (cardholder data transmitted to schemes); it still needs the compliance decision recorded in item 2.

## Every PR
Branch off main; PR; merge only on your "merge N" with required gates green. Docs: `CLAUDE.md` (Last git commit, PCI checklist, dev-run command gets `CARD_ALLOW_DEV_ENCRYPTION_KEY=true`), `cba-log.md` (cont. 68, 69), `docs/card-api-reference.html` + `docs/api-reference.html` + Postman (+ `docs-site/static` copies: the issue request loses `cvv`), regenerated OpenAPI snapshot, `docs/deferred-backlog.md` items 2 and 7, `docs/integration-runbook.md` (key requirement + vacuum/backup step). Gate grep output pasted each time.

## Verification
- `cd card-service && DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration -Dupdate.api.snapshot=true`, then again without the flag (now 374).
- Start card-service from source against a database holding the V2 demo card: it migrates, `GET /api/v1/cards/{id}` still works, a terminal-simulator purchase with a tokenized card still de-tokenizes. With `CARD_ALLOW_DEV_ENCRYPTION_KEY` unset and the default key, startup fails with a clear message.
- `psql`: `SELECT pan_encrypted FROM cards` shows only `ENC1:…`.
- After each merge: `git log --oneline -1`; card-service migrations V1 → V24 with no gaps.

## Not in this set
- Key rotation (the `ENC1:` prefix allows a later `ENC2:`); keys in an HSM/KMS.
- Backlog 7 (bureau key wrapping) and backlog 14 (credit cards).
