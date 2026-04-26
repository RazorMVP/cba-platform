---
id: changelog
title: Changelog
sidebar_position: 10
description: API changelog — breaking changes, new endpoints, and deprecations.
---

# Changelog

---

## 2026-04-26 — Partner Portal v1.1

- **New:** `GET /api/v1/partners/{orgId}/consents` — list Open Banking consents by organisation
- **New:** `DELETE /api/v1/partners/{orgId}/consents/{consentId}` — revoke a consent via Partner API
- **New:** `PUT /api/v1/partners/{orgId}` — update organisation name and website
- **New:** `PUT /api/v1/partners/users/{userId}` — update partner user email
- **New:** `POST /api/v1/partners/users/{userId}/change-password`
- **New:** Webhook delivery service — HMAC-SHA256 signed, exponential backoff (15s→60s→5m→30m→2h)
- **New:** 17 webhook event types across consent, payment, account, partner lifecycle categories
- **New:** Card API reference (`/card-api-reference.html`)
- **New:** Partner API reference (`/partner-api-reference.html`)

## 2026-04-15 — Partner Portal v1.0

- **New:** Partner self-registration (`POST /api/v1/partners/register`)
- **New:** Partner login + JWT (`POST /api/v1/partners/auth/login`)
- **New:** API key issuance/revocation (`GET/POST/DELETE /api/v1/partners/{orgId}/api-keys`)
- **New:** Production application (`POST /api/v1/partners/{orgId}/applications`)
- **New:** Usage statistics (`GET /api/v1/partners/{orgId}/usage`)
- **New:** Admin: list partners, approve/reject production (`GET /api/v1/partners`, `POST .../approve`, `POST .../reject`)
- **New:** Webhook management (`GET/POST/DELETE /api/v1/partners/{orgId}/webhooks`)
- **New:** Webhook delivery log (`GET /api/v1/partners/{orgId}/webhooks/{id}/deliveries`)

## 2026-03-01 — Open Banking v3.1

- UK Open Banking AISP, PISP, CBPII endpoints live
- FAPI 2.0 with PKCE enforced on all payment endpoints
- Card accounts surfaced in AISP responses (`card_read`, `card_balances_read`, `card_transactions_read` scopes)

---

## Deprecation Policy

- Breaking changes are introduced with a **new API version** (URL path bump, e.g. `/v2/`)
- Deprecated versions receive a **6-month support window** from the announcement date
- Non-breaking additions (new optional fields, new endpoints) ship without a version bump
- Deprecation notices appear in the `Deprecation` and `Sunset` response headers

