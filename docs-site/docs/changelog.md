---
id: changelog
title: Changelog
sidebar_position: 10
description: NubBank API version history, breaking changes, and deprecation notices.
---

# Changelog

All notable changes to the NubBank API are documented here.

Breaking changes are marked **[BREAKING]**. Deprecations are marked **[DEPRECATED]**. New features are marked **[NEW]**. Bug fixes are marked **[FIX]**.

---

## v2.1.0 — 2026-04-15

### Open Banking v3.1

- **[NEW]** Card accounts now surface in `GET /open-banking/v3.1/accounts` with `accountType: CARD` and subtypes `DEBIT_CARD`, `CREDIT_CARD`, `PREPAID_CARD`
- **[NEW]** Card balances available via `GET /open-banking/v3.1/accounts/{id}/balances` — debit/prepaid returns wallet balance; credit returns available credit (`creditLimit − outstanding`)
- **[NEW]** Card authorization history mapped to Open Banking transaction objects via `GET /open-banking/v3.1/accounts/{id}/transactions`
- **[NEW]** `POST /open-banking/v3.1/funds-confirmations` now supports card accounts — requires `card_balances_read` consent scope
- **[NEW]** Three new consent scopes: `CARD_READ`, `CARD_TRANSACTIONS_READ`, `CARD_BALANCES_READ`

### Card API

- **[NEW]** Dedicated BaaS Card API at `/card-api/v1/` — dual-mode auth (API Key + FAPI 2.0 JWT)
- **[NEW]** Spending analytics endpoints: `/cards/{id}/analytics/by-category`, `/cards/{id}/analytics/by-merchant`, `/analytics/summary`
- **[NEW]** Webhook management: register, list, deregister endpoints; HMAC-SHA256 delivery signing
- **[NEW]** 17 webhook event types across Authorization, Card Lifecycle, Fraud, and Dispute categories
- **[NEW]** Full disputes workflow: 7-state chargeback machine with scheme reason codes (Visa, Mastercard, Verve, Afrigo, UnionPay)
- **[NEW]** Settlement file export framework: Visa BASE II, Mastercard IPM, Verve NIBSS, Afrigo PAPSS, UnionPay CUPS

### Rate Limiting

- **[NEW]** Redis-backed fixed-window rate limiting on all API paths
- **[NEW]** Response headers on every request: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- **[NEW]** `Retry-After` header on `429` responses
- **[NEW]** Four tiers: SANDBOX (30 rpm), BASIC (100 rpm), PRO (500 rpm), ENTERPRISE (2000 rpm)

---

## v2.0.0 — 2026-02-01

### [BREAKING] Authentication

- **[BREAKING]** Basic HTTP authentication removed from all endpoints — all calls must use OAuth2 Bearer tokens or API keys
- **[BREAKING]** Minimum TLS version raised from 1.1 to 1.2
- **[NEW]** FAPI 2.0 security profile enabled: PAR (Pushed Authorisation Requests), DPoP token binding, and PKCE now required for all third-party consent flows

### [BREAKING] Response Envelope

- **[BREAKING]** All responses now wrapped in `{ data, meta, errors }` envelope. Previously, some endpoints returned the resource directly at the root level.

  **Before:**
  ```json
  { "id": "123", "status": "ACTIVE" }
  ```

  **After:**
  ```json
  { "data": { "id": "123", "status": "ACTIVE" }, "meta": {}, "errors": [] }
  ```

### Open Banking v3.1

- **[NEW]** Full UK Open Banking v3.1 compliance — replaces v2.0 endpoints
- **[NEW]** AISP: consents, accounts, balances, transactions
- **[NEW]** PISP: domestic payment consents + payments
- **[NEW]** CBPII: funds confirmation

### [DEPRECATED] Open Banking v2.0

- **[DEPRECATED]** `/open-banking/v2.0/` endpoints deprecated — sunset date **2027-02-01**. Migrate to `/open-banking/v3.1/`

### Internal API

- **[NEW]** All banking modules available via `/api/v1/` — customers, accounts, loans, payments, products, accounting, reports, tellers, administration
- **[NEW]** Batch API: `POST /api/v1/batches` — execute multiple sub-requests in a single HTTP call with JSON Path reference resolution
- **[NEW]** Exchange rate management: `POST/GET/DELETE /api/v1/exchange-rates` — cross-currency transfers with auto-generated inverse rates

### Card API

- **[NEW]** Card issuance, controls, limits, PIN management via `/card-api/v1/`
- **[NEW]** Terminal simulator for sandbox testing: `POST /api/v1/simulate/purchase`, `/withdrawal`, `/balance`, `/reversal`
- **[NEW]** 3D Secure 2.x ACS: frictionless + challenge flows, CAVV generation, OTP verification
- **[NEW]** ISO 8583-1987 TCP socket processing on port 8583 for direct terminal integration

---

## v1.2.0 — 2025-09-01

### Internal API

- **[NEW]** Close of Business (CoB) scheduler with Spring Batch: interest accrual, arrears classification, standing order execution
- **[NEW]** Dynamic report engine: SQL templates with `${param}` placeholders, 7 seed reports
- **[NEW]** Teller / cash management module: sessions, float, cash-in/out, settlement reconciliation

### [FIX]

- Fixed: audit log `old_values` / `new_values` JSON serialization — bare status strings now correctly quoted as `"PENDING_KYC"` (not `PENDING_KYC`)
- Fixed: `JOIN FETCH` + Spring Data `Page` — added `countQuery` to all paginated queries using joins

---

## v1.1.0 — 2025-06-15

### Internal API

- **[NEW]** Open Banking consent model: `AWAITING_AUTHORISATION → AUTHORISED → REVOKED`
- **[NEW]** Multi-currency support: `X-Tenant-ID` header, exchange rate service, cross-currency transfers
- **[NEW]** Customer self-service API at `/api/v1/self/*` — `GET /self/accounts`, `/self/loans`, `/self/userdetails`
- **[NEW]** Two-factor authentication: OTP generation and verification for platform users
- **[NEW]** Maker-Checker workflow for sensitive operations

### Security

- **[NEW]** Field-level PII encryption via JPA `AttributeConverter` (AES-256, PBEWITHHMACSHA512ANDAES_256)
- **[NEW]** Brute-force protection via Keycloak — lock after 5 failed login attempts

---

## v1.0.0 — 2025-03-01

Initial release of the NubBank API.

### Features

- Customer onboarding and KYC lifecycle (`PENDING_KYC → ACTIVE → SUSPENDED → CLOSED`)
- Savings and checking account management
- Loan origination, disbursement, and repayment scheduling
- Internal payment transfers (double-entry ledger)
- Loan and deposit product configuration
- Immutable audit logging with 10-year retention
- Keycloak OIDC authentication (RBAC: `ADMIN`, `TELLER`, `CUSTOMER`, `API_CLIENT`)
- PostgreSQL schema managed via Flyway migrations

---

## Deprecation Policy

NubBank provides a **minimum 12-month deprecation window** for breaking changes:

1. The deprecated feature is marked `[DEPRECATED]` in the changelog with a sunset date
2. Deprecated endpoints return a `Deprecation` response header pointing to the migration guide
3. On the sunset date, the endpoint is removed and returns `410 Gone`

For questions about upcoming deprecations, contact [api-support@nubbank.com](mailto:api-support@nubbank.com).

---

## Migration Guides

- [Open Banking v2.0 → v3.1 Migration](https://github.com/RazorMVP/cba-platform/wiki/OB-v2-to-v3-migration) — consent scope changes, endpoint URL updates, response shape differences
- [Basic Auth → OAuth2 Migration](https://github.com/RazorMVP/cba-platform/wiki/basic-auth-migration) — PKCE setup, token exchange, scope mapping
