---
id: core-concepts
title: Core Concepts
sidebar_position: 3
description: Partner lifecycle, environments, tiers, and API conventions.
---

# Core Concepts

---

## Partner Lifecycle

```
Register → SANDBOX → Apply for Production → PENDING_REVIEW → PRODUCTION
                                                           ↘ SUSPENDED
```

| Status | Access | API Keys | Webhooks |
|--------|--------|----------|----------|
| `SANDBOX` | Full sandbox — immediate | ✓ | ✓ |
| `PENDING_REVIEW` | Sandbox continues while under review | ✓ | ✓ |
| `PRODUCTION` | Both sandbox and production | ✓ | ✓ |
| `SUSPENDED` | Read-only — no new transactions | ✗ | ✗ |

---

## Environments

| Environment | Base URL | Data |
|-------------|----------|------|
| Sandbox | `https://sandbox.nubbank.com` | Pre-seeded demo data, no real money |
| Production | `https://api.nubbank.com` | Live customers, real transactions |

API keys are environment-scoped — sandbox keys only work on the sandbox URL.

---

## Rate Limiting Tiers

| Tier | Requests / minute | Upgrade path |
|------|-------------------|--------------|
| BASIC | 100 | Default for all new API keys |
| PRO | 500 | Contact api-support@nubbank.com |
| ENTERPRISE | 2,000 | Enterprise agreement required |

Sandbox accounts always use BASIC limits regardless of their production tier.

---

## Request & Response Conventions

### Standard envelope

All API responses use a consistent envelope:

```json
{
  "data": { ... },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 150
  },
  "errors": []
}
```

### Pagination

```
GET /card-api/v1/cards?page=0&size=20&sort=createdAt,desc
```

| Parameter | Default | Max |
|-----------|---------|-----|
| `page` | `0` | — |
| `size` | `20` | `100` |
| `sort` | `createdAt,desc` | — |

### Dates & times

All timestamps are **ISO 8601 UTC**: `2026-04-26T14:30:00Z`.

### Monetary amounts

All amounts are in the **minor unit** of the currency (e.g. pence for GBP, cents for USD). `£12.50` is represented as `1250`.

### IDs

All resource IDs are **UUIDs** (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).

---

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| `200` | Success |
| `201` | Created |
| `204` | No content (DELETE success) |
| `400` | Validation error — see `errors[]` |
| `401` | Authentication required |
| `403` | Insufficient permissions |
| `404` | Resource not found |
| `409` | Conflict (e.g. duplicate key name) |
| `429` | Rate limit exceeded |
| `500` | Internal server error |

---

## Versioning

The API is versioned by URL path:

| Version | Base path | Status |
|---------|-----------|--------|
| v1 | `/api/v1/`, `/card-api/v1/`, `/open-banking/v3.1/` | Current |

Breaking changes will be introduced in a new version with a deprecation notice. Non-breaking additions (new fields, new optional params) may be added without a version bump.

---

## Idempotency

For mutating requests (POST, PUT), you can pass an `Idempotency-Key` header:

```
Idempotency-Key: a1b2c3d4-unique-per-request
```

Retrying a request with the same key within 24 hours returns the original response without re-executing the operation. Recommended for payment initiation and card issuance.
