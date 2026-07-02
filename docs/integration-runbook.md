# External Integrations — Credential-Flip Runbook

> How to take each external integration from its dev/simulated default to a real, live
> connection. The platform is built so that **going live is a config flip + credentials**,
> not a code change — every integration has a pluggable provider whose default is a
> no-op/simulator that needs no external system.

**Golden rule:** the default of every integration below is safe for dev/demo/CI (simulated
or pointed at a local container). Setting the `*_PROVIDER`/`*_GATEWAY` env var to a real
value **and** supplying its credentials is what activates the real path. Leave a provider on
its default and the app runs fully with no external dependency.

Legend for **State**:
- 🟢 **Adapter built** — real code path exists behind a flag; flip + credentials to go live.
- 🟡 **Credential-ready** — real client already wired; supply connection details only.
- 🔴 **Contract-gated** — needs a vendor/scheme relationship and/or certification before it can work at all.

---

## Quick reference — every env var

| Integration | Activate with | Credentials / connection vars |
|-------------|---------------|-------------------------------|
| SMS gateway | `SMS_PROVIDER=HTTP` | `SMS_HTTP_URL`, `SMS_HTTP_API_KEY`, `SMS_HTTP_SENDER` |
| Credit bureau | `CREDIT_BUREAU_PROVIDER=HTTP` | `CREDIT_BUREAU_URL`, `CREDIT_BUREAU_API_KEY`, `CREDIT_BUREAU_MIN_SCORE` |
| External payments | `EXTERNAL_PAYMENT_GATEWAY=HTTP` | `EXTERNAL_PAYMENT_URL`, `EXTERNAL_PAYMENT_API_KEY` |
| Push notifications | `PUSH_PROVIDER=HTTP` | `PUSH_HTTP_URL`, `PUSH_HTTP_API_KEY` |
| Email / SMTP | (always real) | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` |
| Object storage (images) | `app.image.storage=S3` | `app.image.s3.{bucket,region,access-key,secret-key,endpoint-override}` |
| Keycloak / OIDC | `app.auth-bypass=false` | `KEYCLOAK_HOST`, `KEYCLOAK_PORT`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_ADMIN_USERNAME/PASSWORD` |
| Redis | (always real in prod) | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |
| PostgreSQL | (always real) | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` |
| Field encryption | (always real) | `ENCRYPTION_KEY` |
| card-service ↔ backend | (always real) | `CARD_SERVICE_HOST`, `CARD_SERVICE_PORT` |
| Card settlement export (×5 schemes) | `card.settlement.export.schemes.<s>.enabled=true` | per-scheme SFTP/HTTPS creds (see below) |
| HSM (fep-service) | `fep.hsm.provider=THALES` | `HSM_HOST`, `HSM_PORT` + LMK ceremony |
| Card scheme ISO 8583 links | n/a (network) | leased line/VPN + scheme certification |

---

## 1. SMS gateway 🟢

- **What / where:** `com.cba.notification.sms.SmsProvider`; dispatch via `com.cba.social.SmsDispatchService`; used by `POST /api/v1/smscampaigns/{id}/send`.
- **Default:** `SMS_PROVIDER=NONE` → `NoOpSmsProvider` logs and simulates a successful send (message rows become `SENT`, no SMS billed).
- **Go live:**
  1. `SMS_PROVIDER=HTTP`
  2. `SMS_HTTP_URL=` your gateway's send endpoint (e.g. Africa's Talking / a Twilio-compatible gateway / an in-house aggregator).
  3. `SMS_HTTP_API_KEY=` bearer token (omit for open/dev gateways).
  4. `SMS_HTTP_SENDER=` sender id / short code.
- **Body posted:** `{ "to", "from", "message" }` JSON + `Authorization: Bearer <key>`. A gateway with a different shape needs a sibling `SmsProvider` behind a new `havingValue` — no change to the dispatch layer.
- **Verify:** `POST /api/v1/smscampaigns/{id}/send` with one recipient → response `provider` reads `HTTP` and `sent=1`.

## 2. Credit bureau 🟢

- **What / where:** `com.cba.system.bureau.CreditBureauProvider`; policy in `com.cba.system.CreditBureauCheckService`; `POST /api/v1/creditbureaus/check`.
- **Default:** `CREDIT_BUREAU_PROVIDER=SIMULATED` → deterministic 300–850 score, no external call.
- **Go live:**
  1. `CREDIT_BUREAU_PROVIDER=HTTP`
  2. `CREDIT_BUREAU_URL=` bureau lookup endpoint (TransUnion / Metropol / CRC / etc.).
  3. `CREDIT_BUREAU_API_KEY=` bearer token.
  4. (optional) `CREDIT_BUREAU_MIN_SCORE=` pass threshold (default 600).
- **Body posted:** `{ "nationalId", "fullName", "country" }`; expects a numeric `score` (and optional `reference`) in the 2xx body. Different bureau contract → sibling provider.
- **Note:** each real bureau usually needs its own adapter (different auth, field names, sandbox). Add one `HttpXxxCreditBureauProvider` per bureau behind distinct `havingValue`s; the `CreditBureauIntegration.implClass` column can label which bureau an admin registered.
- **Contract:** 🔴 a bureau membership agreement + sandbox credentials are required before the HTTP path returns real data.

## 3. External payments — SWIFT / SEPA / ACH 🟢

- **What / where:** `com.cba.payment.gateway.ExternalPaymentGateway`; wired into `PaymentService.initiateExternalPayment`; `POST /api/v1/payments/external`.
- **Default:** `EXTERNAL_PAYMENT_GATEWAY=SIMULATED` → accepts and returns a synthetic network reference (external payments settle immediately in dev).
- **Go live:**
  1. `EXTERNAL_PAYMENT_GATEWAY=HTTP`
  2. `EXTERNAL_PAYMENT_URL=` PSP / correspondent-bank submit endpoint.
  3. `EXTERNAL_PAYMENT_API_KEY=` bearer token.
- **Safety contract:** the gateway is called **before** the source account is debited. A rejection (or transport error) throws → the transaction rolls back → **no funds leave the account**. Verify this behaviour is preserved in any custom gateway before production.
- **Async settlement:** the current model is a synchronous submit-ack (`COMPLETED` on accept). Real SWIFT/SEPA settlement is asynchronous — a status webhook → `PROCESSING → COMPLETED/RETURNED` lifecycle is future work.
- **Contract:** 🔴 needs a real PSP / correspondent-banking / SWIFT-service-bureau relationship.

## 4. Push notifications 🟢

- **What / where:** `com.cba.notification.push.PushSender`; fan-out via `com.cba.notification.PushDispatchService`; `POST /api/v1/notifications/push`; device registry `push_devices`.
- **Default:** `PUSH_PROVIDER=NONE` → `NoOpPushSender` logs (token-masked) and simulates.
- **Go live:**
  1. `PUSH_PROVIDER=HTTP`
  2. `PUSH_HTTP_URL=` your push relay / FCM proxy endpoint.
  3. `PUSH_HTTP_API_KEY=` bearer token.
- **Dead-token handling:** a 404/410 (or FCM `UNREGISTERED`) auto-deactivates the device — no manual cleanup.
- **Native FCM/APNs:** the HTTP relay is deliberately generic. A native FCM HTTP v1 client needs a Google service-account JSON + OAuth2 token minting; APNs needs a `.p8` key + JWT. Add each as a sibling `PushSender` behind a new `havingValue` (e.g. `FCM`, `APNS`). This becomes a hard dependency for the Flutter mobile app (Phase 3).

## 5. Email / SMTP 🟡

- **What / where:** Spring `JavaMailSender` (real — `spring-boot-starter-mail`); used by notifications and report-mailing.
- **Default:** dev/docker point at **MailHog** (`MAIL_HOST=localhost`, `MAIL_PORT=1025`), so mail is captured, not sent.
- **Go live:** set `MAIL_HOST`, `MAIL_PORT` (587), `MAIL_USERNAME`, `MAIL_PASSWORD` to a real provider (SES / SendGrid / Mailgun / corporate relay). `starttls` is already enabled in the prod profile. No code change.

## 6. Object storage — client images 🟡

- **What / where:** `com.cba.customer.storage.StorageProvider` — `FileSystemStorageProvider` (default), `DatabaseStorageProvider`, `S3StorageProvider`.
- **Default:** `app.image.storage=FILE_SYSTEM`.
- **Go live (S3/MinIO/GCS):** `app.image.storage=S3` + `app.image.s3.{bucket,region,access-key,secret-key}`; set `endpoint-override` for MinIO / GCS-S3 / LocalStack.

## 7. Keycloak / OIDC 🟡

- **Default:** dev/docker run with `app.auth-bypass=true` (`DevAuthBypassFilter` injects an ADMIN principal — no Keycloak needed).
- **Go live:** deploy Keycloak, import the `cba` realm, set `KEYCLOAK_HOST/PORT`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_ADMIN_USERNAME/PASSWORD`, and set `app.auth-bypass=false`. The web/partner/mobile clients are defined in the realm export.

## 8. Redis 🟡

- Rate limiting + cache. Dev may use `spring.cache.type=simple` and skip Redis; prod uses Redis (`REDIS_HOST/PORT/PASSWORD`). card-service's `binLookup` cache also uses it.

## 9. PostgreSQL / encryption / card-service link 🟡

- **PostgreSQL:** `DB_HOST/PORT/NAME/USERNAME/PASSWORD` (two DBs: main + card).
- **Field encryption:** `ENCRYPTION_KEY` (AES-256; PII converter). Must be stable across restarts or existing ciphertext is unreadable.
- **backend ↔ card-service:** `CARD_SERVICE_HOST/PORT` (REST).

## 10. Card scheme settlement file export ×5 🟢🔴

- **What / where:** `com.cba.card.settlement.*` — real binary exporters (Visa BASE II, Mastercard IPM, NIBSS, PAPSS/JSON, CUPS) + SFTP/HTTPS transmitter, in **card-service**.
- **Default:** all 5 schemes `enabled: false`.
- **Go live per scheme:** `card.settlement.export.schemes.<scheme>.enabled=true` + its SFTP/HTTPS creds:
  - Visa: `VISA_SFTP_HOST/USER/KEY_PATH`
  - Mastercard: `MC_SFTP_HOST/USER/KEY_PATH`
  - Verve: `VERVE_SFTP_HOST/USER/KEY_PATH`
  - Afrigo/PAPSS: `PAPSS_ENDPOINT` + `PAPSS_API_KEY` (HTTPS)
  - UnionPay: `CUP_SFTP_HOST/USER/KEY_PATH`
- ⚠️ Replace JSch `StrictHostKeyChecking=no` with a real `known_hosts` before production.
- **Contract:** 🔴 requires scheme membership + real interchange rate tables.

## 11. HSM — Thales payShield (fep-service) 🟢🔴

- **What / where:** `com.cba.fep.hsm.HsmAdapter` — `SoftwareHsmAdapter` (default, software crypto) vs `ThalesPayShieldAdapter` (stub).
- **Go live:** `fep.hsm.provider=THALES` + `HSM_HOST/PORT`; complete the ThalesPayShieldAdapter TCP command implementations.
- **Contract:** 🔴 real payShield 9000/10000 + an LMK/ZMK/ZPK key ceremony.

## 12. Card scheme ISO 8583 network links 🔴

- **What / where:** fep-service (Netty ISO 8583 :8583); today driven only by the card-service terminal simulator.
- **Go live:** leased line / VPN to each scheme switch (Visa/MC/Verve/Afrigo/UnionPay) + **end-to-end certification** per scheme. No dev shortcut — this is pure vendor/certification work. Related contract-gated items: **3-D Secure Directory Server** registration per scheme (`com.cba.card.threeds`) and **card personalization bureau** transmission (`com.cba.card.bureau` → Thales/HID/Idemia).

---

## CI/CD & ops accounts (non-runtime)

| Purpose | Secret(s) |
|---------|-----------|
| Vercel deploys (web / partner-portal / docs) | `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID_*` |
| SonarCloud | `SONAR_TOKEN`, `SONAR_ORG` |
| Snyk | `SNYK_TOKEN` |
| OWASP NVD | `NVD_API_KEY` |
| Kubernetes deploy | `KUBE_CONFIG_STAGING`, `KUBE_CONFIG_PROD` |
| GHCR image push | `GITHUB_TOKEN` (built-in) |

---

## What's still real engineering (not just credentials)

- **Per-vendor adapters** for credit bureau, SMS, push, external payments — each real provider (TransUnion vs Metropol; Twilio vs Africa's Talking; FCM vs APNs; each PSP) may need its own `havingValue` sibling with vendor-specific auth and field mapping. The framework is done; each vendor is a small, isolated addition.
- **Async external-payment settlement** lifecycle (status webhooks).
- **Thales payShield** command implementations + HSM key ceremony.
- **Scheme ISO 8583 connectivity + certification**, 3DS DS registration, personalization-bureau file transmission — all vendor/certification-gated.
