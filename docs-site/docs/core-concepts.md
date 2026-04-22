---
id: core-concepts
title: Core Concepts
sidebar_position: 3
description: Consent model, idempotency, pagination, date formats, and error handling.
---

# Core Concepts

Before building with NubBank APIs, understand these foundational concepts.

---

## Consent Model

All Open Banking operations require a **Consent** resource before any data or payment action can be performed.

### Consent Lifecycle

```
AWAITING_AUTHORISATION → AUTHORISED → REVOKED
```

| Status | Description |
|--------|-------------|
| `AWAITING_AUTHORISATION` | Consent created but user has not yet approved |
| `AUTHORISED` | User approved — API calls against this consent are permitted |
| `REVOKED` | Consent was revoked by the user or by the TPP — all further calls are rejected |

### Creating a Consent

Always create the consent **before** redirecting the user:

```bash
POST /open-banking/v3.1/account-access-consents
{
  "Data": {
    "Permissions": ["ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"],
    "ExpirationDateTime": "2026-12-31T00:00:00Z",
    "TransactionFromDateTime": "2026-01-01T00:00:00Z"
  },
  "Risk": {}
}
```

The response `ConsentId` is included in the PKCE authorisation redirect as the `intent_id` parameter.

### Consent Expiry

- Consents expire at `ExpirationDateTime` or after **90 days** of inactivity, whichever comes first
- Payment consents (PISP) are single-use — they expire after one successful payment initiation
- CBPII consents are multi-use within the validity period

---

## Idempotency

POST requests that create or initiate resources support idempotency. Include the same `x-idempotency-key` header to safely retry a request without duplicate side effects.

```bash
curl -X POST https://api.nubbank.com/open-banking/v3.1/domestic-payments \
  -H "x-idempotency-key: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer TOKEN" \
  -d '{ ... }'
```

**Rules:**
- The key must be unique per request intent (not reused across different payment amounts/recipients)
- Keys are valid for **24 hours** — the same key within 24 hours returns the cached response
- Keys must be UUIDs (v4)

---

## Pagination

All list endpoints use cursor-based pagination via query parameters.

### Request Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `20` | Results per page (max `100`) |
| `sort` | string | `createdAt,desc` | Sort field and direction |

### Response Envelope

```json
{
  "data": [ ... ],
  "meta": {
    "page": 0,
    "size": 20,
    "total": 150,
    "totalPages": 8
  },
  "errors": []
}
```

### Example

```bash
GET /api/v1/transactions?page=0&size=20&sort=createdAt,desc
GET /api/v1/transactions?page=1&size=20&sort=amount,asc
```

---

## Date and Time Formats

NubBank uses **ISO 8601** throughout:

| Context | Format | Example |
|---------|--------|---------|
| Request bodies | `YYYY-MM-DDThh:mm:ssZ` | `2026-04-15T14:30:00Z` |
| Response fields | `YYYY-MM-DDThh:mm:ss.SSSZ` | `2026-04-15T14:30:00.000Z` |
| Date-only fields | `YYYY-MM-DD` | `2026-04-15` |

All timestamps are **UTC**. Never pass local time without a timezone offset.

### Open Banking Date Arrays

The UK Open Banking spec returns dates as arrays in some responses:

```json
"BookingDateTime": "2026-04-15T14:30:00Z"
```

---

## Monetary Amounts

- All amounts are `NUMERIC` with up to **4 decimal places**
- Always use **strings** for amount fields in JSON bodies to avoid floating-point precision loss
- Currency is identified by **ISO 4217** alphabetic code (`GBP`, `USD`, `KES`, `NGN`)

```json
{
  "Amount": {
    "Amount": "10.50",
    "Currency": "GBP"
  }
}
```

---

## Error Handling

### Error Envelope

All errors use a consistent envelope:

```json
{
  "data": null,
  "meta": {},
  "errors": [
    {
      "code": "ACCOUNT_NOT_FOUND",
      "message": "Account 22289 not found",
      "field": "accountId"
    }
  ]
}
```

### HTTP Status Codes

| Status | Meaning |
|--------|---------|
| `200 OK` | Request succeeded |
| `201 Created` | Resource created |
| `400 Bad Request` | Validation error — check `errors` array |
| `401 Unauthorized` | Missing or invalid access token |
| `403 Forbidden` | Token valid but insufficient scope |
| `404 Not Found` | Resource does not exist (or belongs to another customer) |
| `409 Conflict` | Duplicate request (idempotency conflict) |
| `429 Too Many Requests` | Rate limit exceeded — see `Retry-After` header |
| `500 Internal Server Error` | Platform error — retry with exponential backoff |

### Error Codes

See the full [Error Reference](/docs/error-reference) for all error codes, descriptions, and remediation steps.

---

## Request Tracing

Every API request generates a unique trace. Include these headers for support queries:

| Header | Required | Description |
|--------|----------|-------------|
| `x-fapi-interaction-id` | Required (Open Banking) | UUID per request for end-to-end tracing |
| `x-request-id` | Optional (Card/Internal APIs) | Your own request identifier, echoed in responses |

The server always echoes `x-fapi-interaction-id` in the response.

---

## Environments

| Environment | Base URL | Purpose |
|-------------|----------|---------|
| Sandbox | `https://sandbox.nubbank.com` | Development and testing |
| Production | `https://api.nubbank.com` | Live transactions |

Sandbox credentials and production credentials are **separate** — sandbox keys cannot call production endpoints.

---

## Next Steps

- [Authentication](/docs/authentication) — OAuth2 PKCE, token refresh
- [Open Banking API](/docs/api/open-banking) — AISP, PISP, CBPII
- [Error Reference](/docs/error-reference) — all error codes
