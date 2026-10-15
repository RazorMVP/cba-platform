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
| card-service ↔ backend | (always real) | `CARD_SERVICE_BASE_URL` (or `CARD_SERVICE_HOST`/`PORT`); service token: `CBA_BACKEND_SERVICE_SECRET`, `CARD_SERVICE_TOKEN_URI` |
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
- **backend ↔ card-service:** `CARD_SERVICE_BASE_URL` (compose/k8s; `CARD_SERVICE_HOST/PORT` still honoured) (REST).
- **backend → card-service service token** (cont. 33): card-service accepts only Keycloak JWTs, so the backend uses the
  `client_credentials` grant of Keycloak client **`cba-backend-service`** (realm role **`CARD_READER`** only: list/get card,
  balance, authorization history).
  - **Secret:** one value, `CBA_BACKEND_SERVICE_SECRET`, given to **both** Keycloak (filled into `cba-realm.json` at import)
    and the backend. Generate with `openssl rand -base64 32`; k8s keys `keycloak-secret` + `backend-secret`.
  - **Token URI:** `CARD_SERVICE_TOKEN_URI`, on the **same Keycloak host as card-service's issuer**
    (`http://keycloak:8180/realms/cba/...` in compose/k8s), or card-service rejects the token's `iss`.
  - **Existing realm:** `--import-realm` skips a realm that already exists, so on an existing Keycloak create the client by
    hand (Clients → Create: client authentication ON, only "Service accounts roles" flow; Service account roles → assign
    realm role `CARD_READER`, after creating that role) and copy its secret.
  - **Rotate:** regenerate the client secret in Keycloak, update both secrets, restart the backend (the cached token
    expires within its lifetime, default 300 s).
  - **Verify:** `curl -u cba-backend-service:$SECRET -d grant_type=client_credentials $TOKEN_URI` returns a token whose
    `realm_access.roles` is `["CARD_READER"]`; AISP `/accounts` for a customer with cards lists them. Secret unset → the
    backend logs a WARN at startup and card data is omitted.
- **fep-service → card-service service token** (cont. 34): card-service's `/api/v1/internal/**` (authorize, advise,
  reverse, **PAN de-tokenisation**) and the BIN endpoints fep uses require a Keycloak JWT; the internal ones need realm
  role **`FEP_SERVICE`**, held only by client **`cba-fep-service`**.
  - **Secret:** `CBA_FEP_SERVICE_SECRET`, the same value for **Keycloak** (realm import placeholder) and **fep-service**.
    k8s keys `keycloak-secret` + `fep-service-secret`. Token URI `FEP_CARD_SERVICE_TOKEN_URI` (same Keycloak host as
    card-service's issuer). fep-service is on Spring Security 6.2, so the token is fetched by a small built-in client
    (`CardServiceTokenProvider`): shared, renewed 30 s before expiry.
  - **Existing realm:** not re-imported. Create role `FEP_SERVICE`, then client `cba-fep-service` (client authentication
    ON, only "Service accounts roles"), assign it `FEP_SERVICE`, copy the secret.
  - **Unset secret:** fep-service logs a WARN and calls without a token; a card-service without dev bypass refuses
    them, so every authorization declines with RC 96.
  - **Verify:** the client_credentials token's `realm_access.roles` is `["FEP_SERVICE"]`; `curl -X POST
    $CARD/api/v1/internal/detokenize` without a token → 401.
- **card-service edge (cont. 34):**
  - the public ingress sends `/api/v1/internal` and `/actuator` to `card-service-blackhole` (no endpoints → 503);
  - `card-service-networkpolicy.yaml` admits only fep-service, backend and the ingress controller on 8081. The
    controller's namespace is assumed to be `ingress-nginx`; change it to yours. It needs a NetworkPolicy-enforcing CNI.

### 9a. Before the first deploy — service-credential checklist

Nothing here affects a local `docker compose` run: there, both secrets default to `local-dev-only-change-me`
for Keycloak and the service alike, and card-service runs with the dev auth bypass. Every item below fails
**quietly**: nothing crashes, but card authorizations decline with **RC 96** (fep-service) or Open Banking
card data comes back empty (backend), and the logs show `No card-service token …`.

**1. One secret per service client, given to both sides.**

| Variable | Keycloak (realm import) | Service | k8s secrets |
|---|---|---|---|
| `CBA_FEP_SERVICE_SECRET` | client `cba-fep-service` | fep-service | `keycloak-secret`, `fep-service-secret` |
| `CBA_BACKEND_SERVICE_SECRET` | client `cba-backend-service` | backend | `keycloak-secret`, `backend-secret` |

- Generate each with `openssl rand -base64 32`, and put the **same** value in both secrets of its row.
- Mismatch → Keycloak answers `401 invalid_client`. Unset in the service → a startup WARN, then calls go without a token.

**2. An existing realm is not updated.** `--import-realm` skips a realm that already exists (Keycloak
behaviour), so on a Keycloak that already has realm `cba`, create these by hand in the admin console:
1. Realm roles → create `FEP_SERVICE` (and `CARD_READER` if missing).
2. Clients → create `cba-fep-service`, with:
   - **Client authentication** ON;
   - every flow off except **Service accounts roles**;
   - default client scope `roles`.

   Do the same for `cba-backend-service` if it's missing.
3. Each client's **Service account roles** tab → assign **only** its role (`FEP_SERVICE` / `CARD_READER`).
   `KeycloakRealmServiceClientTest` guards the realm *files*, not a live server.
4. **Credentials** tab → copy or set the secret, and use that value in both k8s secrets (item 1).

Then check: `curl -s -u cba-fep-service:$SECRET -d grant_type=client_credentials $KEYCLOAK/realms/cba/protocol/openid-connect/token`
returns a token whose `realm_access.roles` is exactly `["FEP_SERVICE"]` (`["CARD_READER"]` for the backend client).

**3. NetworkPolicy namespace and CNI.**
- `infrastructure/k8s/card-service/card-service-networkpolicy.yaml` admits only:
  - fep-service;
  - backend;
  - pods in the namespace whose `kubernetes.io/metadata.name` is `ingress-nginx` (Kubernetes sets this label on every
    namespace since 1.21).
- Find your controller's namespace with `kubectl get pods -A | grep -i ingress`, and change the policy if it differs
  (e.g. `kube-system` for k3s Traefik). If it's wrong, the **whole public card-service surface** fails with 502/504:
  card API, 3DS ACS, admin card screens. fep-service and backend still work, which hides the cause.
- The policy only works on a CNI that enforces NetworkPolicy (Calico, Cilium, …). Elsewhere it is silently ignored.
  You lose that layer, but the FEP_SERVICE token check and the ingress blackhole still protect `/api/v1/internal/**`.
- Kubelet probes come from the node and are normally allowed. If pods turn unready right after the policy lands,
  check the CNI first.

**4. What the pipeline applies, and what it doesn't.**
- Applied automatically: the card-service deploy job runs `kubectl apply -f infrastructure/k8s/card-service/`. That
  ships the NetworkPolicy and the blackhole Service, so item 3 must be right **before** the first deploy.
- Applied once by hand: Keycloak, fep-service, `secrets/` and `configmaps/` are the one-time cluster bootstrap
  (`docs/deferred-backlog.md` item 8). Items 1 and 2 belong there.

**5. Verify after deploying.**
1. `curl -s -o /dev/null -w "%{http_code}\n" -X POST https://card-api.<domain>/api/v1/internal/detokenize` → **503**
   (the ingress blackhole; the request never reaches card-service).
2. A test authorization through fep-service approves or declines on its merits, not RC 96.
3. AISP `/accounts` for a customer with cards lists them.

## 10. Card scheme settlement file export ×5 🟢🔴

- **What / where:** `com.cba.card.settlement.*` — real binary exporters (Visa BASE II, Mastercard IPM, NIBSS, PAPSS/JSON, CUPS) + SFTP/HTTPS transmitter, in **card-service**.
- **Default:** all 5 schemes `enabled: false`.
- **Go live per scheme:** `card.settlement.export.schemes.<scheme>.enabled=true` + its SFTP/HTTPS creds:
  - Visa: `VISA_SFTP_HOST/USER/KEY_PATH` + **`VISA_SFTP_KNOWN_HOSTS`**
  - Mastercard: `MC_SFTP_HOST/USER/KEY_PATH` + **`MC_SFTP_KNOWN_HOSTS`**
  - Verve: `VERVE_SFTP_HOST/USER/KEY_PATH` + **`VERVE_SFTP_KNOWN_HOSTS`**
  - Afrigo/PAPSS: `PAPSS_ENDPOINT` + `PAPSS_API_KEY` (HTTPS)
  - UnionPay: `CUP_SFTP_HOST/USER/KEY_PATH` + **`CUP_SFTP_KNOWN_HOSTS`**
- 🔒 **Host-key pinning is mandatory for SFTP schemes.** Set `*_SFTP_KNOWN_HOSTS` to an
  OpenSSH `known_hosts` file path, or `*_SFTP_KNOWN_HOSTS_ENTRY` to a literal line
  (`[host]:port ssh-rsa AAAA...`) where mounting a file is awkward. With neither set, an
  enabled scheme **fails closed** — `transmit()` throws before opening a connection, so no
  settlement file and no authentication attempt reaches an unverified host. Take the key
  from the scheme's onboarding pack; do **not** harvest it by connecting and trusting
  whatever answers.
- 🔒 **Optional mutual TLS for Afrigo/PAPSS:** `PAPSS_MTLS_KEYSTORE` +
  `PAPSS_MTLS_KEYSTORE_PASSWORD` (PKCS12; `PAPSS_MTLS_KEYSTORE_TYPE` to override), plus
  `PAPSS_MTLS_TRUSTSTORE`/`_PASSWORD` only if PAPSS uses a private CA. Unset = one-way TLS
  with bearer auth (server cert still verified). Set-but-unloadable **fails closed** rather
  than silently downgrading to bearer-only.
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
