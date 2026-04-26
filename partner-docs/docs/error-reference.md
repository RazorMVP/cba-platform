---
id: error-reference
title: Error Reference
sidebar_position: 7
description: Complete list of error codes, HTTP status mappings, and resolution guidance.
---

# Error Reference

All errors follow the standard envelope:

```json
{
  "data": null,
  "meta": {},
  "errors": [
    {
      "code": "ERROR_CODE",
      "message": "Human-readable description",
      "field": "fieldName or null"
    }
  ]
}
```

---

## Authentication & Authorization

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `UNAUTHORIZED` | 401 | Missing or invalid credentials | Check your API key or JWT token |
| `TOKEN_EXPIRED` | 401 | JWT has expired | Re-authenticate to get a new token |
| `INVALID_API_KEY` | 401 | API key not found or revoked | Issue a new API key from the Partner Portal |
| `FORBIDDEN` | 403 | Valid auth but insufficient scope/role | Check required scopes for this endpoint |
| `ORGANISATION_SUSPENDED` | 403 | Partner organisation is suspended | Contact api-support@nubbank.com |

---

## Consent & Open Banking

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `CONSENT_NOT_FOUND` | 404 | Consent ID does not exist | Verify the consent ID |
| `CONSENT_NOT_AUTHORISED` | 403 | Consent exists but not yet authorised | Customer must authorise first |
| `CONSENT_EXPIRED` | 403 | Consent passed its expiration date | Create a new consent |
| `CONSENT_REVOKED` | 403 | Consent was revoked | Create a new consent |
| `INVALID_SCOPE` | 400 | Requested scope not allowed for this consent | Check consent permissions |
| `PAYMENT_CONSENT_MISMATCH` | 400 | Payment initiation data doesn't match consent | Resubmit matching the consent |

---

## Card API

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `CARD_NOT_FOUND` | 404 | Card ID does not exist | Verify the card ID |
| `CARD_BLOCKED` | 422 | Card is blocked — transaction not permitted | Unblock the card first |
| `CARD_EXPIRED` | 422 | Card has passed its expiry date | Issue a replacement card |
| `CARD_CANCELLED` | 422 | Card is cancelled — cannot be reactivated | Issue a new card |
| `CARD_NOT_ACTIVE` | 422 | Card is not in ACTIVE status | Check card status and activate if needed |
| `PIN_RETRY_EXCEEDED` | 422 | PIN retry counter ≥ 3 | Customer must call the bank to reset |
| `INVALID_CARD_TYPE` | 400 | Command not valid for this card type | Check supported commands for the card type |
| `LIMIT_VIOLATION` | 400 | Per-transaction limit would be exceeded | Reduce the amount or increase the limit |

---

## Account & Customer

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `ACCOUNT_NOT_FOUND` | 404 | Account ID does not exist | Verify the account ID |
| `ACCOUNT_NOT_ACTIVE` | 422 | Account is not in ACTIVE status | Check account status |
| `CUSTOMER_NOT_FOUND` | 404 | Customer ID does not exist | Verify the customer ID |
| `INSUFFICIENT_BALANCE` | 422 | Account balance too low for this transaction | Check available balance |
| `MINIMUM_BALANCE_VIOLATION` | 422 | Transaction would breach minimum balance | Account has a minimum balance requirement |

---

## Validation

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `VALIDATION_ERROR` | 400 | Request body failed validation | Check `field` for the specific field name |
| `MISSING_REQUIRED_FIELD` | 400 | Required field is absent | Add the missing field |
| `INVALID_FIELD_FORMAT` | 400 | Field value does not match expected format | Check the API reference for format requirements |
| `INVALID_CURRENCY` | 400 | Currency code not recognised | Use ISO 4217 numeric code (e.g. `840` for USD) |
| `INVALID_AMOUNT` | 400 | Amount must be a positive integer in minor units | Use pence/cents, not pounds/dollars |
| `DUPLICATE_REQUEST` | 409 | Idempotency key matches an existing request | Use a new `Idempotency-Key` for a new request |

---

## Rate Limiting

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests | Wait `Retry-After` seconds before retrying |

**Response headers on 429:**

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1745678400
Retry-After: 60
```

---

## Webhook Errors

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `WEBHOOK_NOT_FOUND` | 404 | Webhook ID does not exist | Verify the webhook ID |
| `WEBHOOK_CALLBACK_UNREACHABLE` | 400 | Callback URL is not HTTPS or could not be reached | Use a valid HTTPS URL |
| `WEBHOOK_SECRET_TOO_SHORT` | 400 | Secret must be at least 32 characters | Use a longer secret |

---

## Server Errors

| Code | HTTP | Description | Resolution |
|------|------|-------------|------------|
| `INTERNAL_ERROR` | 500 | Unexpected server error | Retry with exponential backoff; contact support if persistent |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable | Retry after the `Retry-After` header interval |

---

## Handling Errors in Code

```javascript
// JavaScript — generic error handler
async function callApi(url, options) {
  const res = await fetch(url, options);
  const body = await res.json();

  if (!res.ok) {
    const err = body.errors?.[0];
    throw new ApiError(err?.code ?? 'UNKNOWN', err?.message ?? 'Unknown error', res.status);
  }

  return body.data;
}

class ApiError extends Error {
  constructor(code, message, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
```

```python
# Python — requests
import requests

def call_api(url, **kwargs):
    r = requests.request(**kwargs, url=url)
    if not r.ok:
        err = r.json().get('errors', [{}])[0]
        raise ApiError(err.get('code'), err.get('message'), r.status_code)
    return r.json()['data']
```
