---
id: error-reference
title: Error Reference
sidebar_position: 9
description: Complete catalogue of NubBank API error codes, descriptions, and remediation steps.
---

# Error Reference

All NubBank API errors follow the standard envelope:

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

## HTTP Status Code Summary

| HTTP Status | Meaning | When |
|-------------|---------|------|
| `200 OK` | Success | GET / action completed |
| `201 Created` | Resource created | POST created a new resource |
| `400 Bad Request` | Validation error | Invalid input — see `errors[].field` |
| `401 Unauthorized` | Authentication failed | Missing or expired token / API key |
| `403 Forbidden` | Authorisation failed | Valid credentials, insufficient scope |
| `404 Not Found` | Resource not found | ID does not exist (or belongs to another user) |
| `409 Conflict` | Duplicate / state conflict | Idempotency key clash, resource already in terminal state |
| `422 Unprocessable Entity` | Business rule violation | Logical constraint (insufficient funds, consent expired, etc.) |
| `429 Too Many Requests` | Rate limit exceeded | See [Rate Limiting](/docs/rate-limiting) |
| `500 Internal Server Error` | Server error | Unexpected condition — contact support |

---

## Authentication Errors

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `AUTH_TOKEN_MISSING` | 401 | No Authorization header provided | Include `Authorization: Bearer {token}` or `Authorization: ApiKey {key}` |
| `AUTH_TOKEN_EXPIRED` | 401 | Access token has expired | Refresh the token using `POST /auth/realms/cba/protocol/openid-connect/token` with `grant_type=refresh_token` |
| `AUTH_TOKEN_INVALID` | 401 | Token signature verification failed | Re-authenticate; do not reuse tokens from other environments |
| `AUTH_TOKEN_REVOKED` | 401 | Token has been revoked | Re-authenticate |
| `API_KEY_MISSING` | 401 | No API key provided | Include `Authorization: ApiKey {key}` header |
| `API_KEY_INVALID` | 401 | API key not found or inactive | Verify the key in the Partner Portal; generate a new key if needed |
| `API_KEY_REVOKED` | 401 | API key has been revoked | Issue a new API key in the Partner Portal |
| `INSUFFICIENT_SCOPE` | 403 | Token does not have required scope | Re-authenticate with the correct scope (e.g. `payments`, `accounts`) |
| `FAPI_DPoP_REQUIRED` | 401 | DPoP-bound token required for FAPI 2.0 | Attach a `DPoP` header with a proof JWT |
| `FAPI_NONCE_MISSING` | 400 | x-fapi-auth-date or nonce missing | Include all required FAPI headers |

---

## Consent Errors (Open Banking)

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `OB_CONSENT_NOT_FOUND` | 404 | Consent not found | Verify the ConsentId; check it belongs to your client |
| `OB_CONSENT_INVALID` | 422 | Consent is not in a valid state | Create a new consent |
| `OB_CONSENT_EXPIRED` | 422 | Consent has expired | Create a new consent with a future `ExpirationDateTime` |
| `OB_CONSENT_NOT_AUTHORISED` | 422 | Consent has not been authorised by the user | Complete the PKCE redirect authorisation flow |
| `OB_CONSENT_REVOKED` | 422 | Consent has been revoked | Create a new consent |
| `OB_CONSENT_SCOPE_MISMATCH` | 403 | Token scope does not match consent scope | Re-authenticate with the correct scope |
| `OB_PERMISSION_MISSING` | 403 | Consent does not include required permission | Include the required permission in the consent request |
| `OB_ACCOUNT_NOT_FOUND` | 404 | Account not found or not covered by consent | Verify the account is included in the consent |

---

## Payment Errors (PISP)

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `PAYMENT_CONSENT_INVALID` | 422 | Payment consent is not AUTHORISED | Complete the authorisation redirect before submitting payment |
| `PAYMENT_CONSENT_EXPIRED` | 422 | Payment consent has expired | Create a new consent |
| `PAYMENT_DUPLICATE` | 409 | Duplicate submission — same idempotency key with different data | Generate a new `x-idempotency-key` for modified payment data |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | Payment amount does not match consent | Submit the exact amount specified in the consent |
| `PAYMENT_CREDITOR_MISMATCH` | 422 | Creditor account does not match consent | Creditor account must match the consent exactly |
| `PAYMENT_STATUS_INVALID` | 409 | Payment is not in a state that allows this operation | Check current status before retrying |
| `INSUFFICIENT_BALANCE` | 422 | Payer account has insufficient funds | Notify the user; check account balance |
| `PAYMENT_LIMIT_EXCEEDED` | 422 | Payment exceeds daily or transaction limit | Check applicable limits; consider splitting payment |
| `BENEFICIARY_ACCOUNT_INVALID` | 400 | Creditor sort code / account number is invalid | Verify the account number with `SchemeName = UK.OBIE.SortCodeAccountNumber` |
| `PAYMENT_REJECTED` | 422 | Payment rejected by downstream processing | Check `MultiAuthorisation` or `Charges` fields in the payment response |

---

## Funds Confirmation Errors (CBPII)

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `CBPII_CONSENT_INVALID` | 422 | Funds confirmation consent is not valid | Create a new `funds-confirmation-consent` |
| `CBPII_CONSENT_EXPIRED` | 422 | Funds confirmation consent has expired | Create a new consent with updated `ExpirationDateTime` |
| `CBPII_ACCOUNT_NOT_FOUND` | 404 | Debtor account not found | Verify the account number in the consent |
| `CBPII_INVALID_AMOUNT` | 400 | InstructedAmount must be positive | Provide a positive monetary value |

---

## Card API Errors

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `CARD_NOT_FOUND` | 404 | Card not found | Verify the card ID |
| `CARD_INACTIVE` | 422 | Card is not in ACTIVE state | Check card status; activate if needed |
| `CARD_BLOCKED` | 422 | Card is blocked | Unblock via `PUT /cards/{id}/controls` or contact card support |
| `CARD_EXPIRED` | 422 | Card has expired | Issue a replacement card |
| `CARD_CANCELLED` | 422 | Card has been permanently cancelled | Issue a new card |
| `CARD_ALREADY_ACTIVE` | 409 | Card is already in ACTIVE state | No action needed |
| `CARD_PIN_NOT_SET` | 422 | PIN must be set before use | Set PIN via `POST /cards/{id}/pin/change` |
| `CARD_PIN_ATTEMPTS_EXCEEDED` | 422 | PIN retry limit exceeded | Card is blocked; contact support |
| `CARD_LIMIT_INVALID` | 400 | Limit value is invalid | `perTransactionLimit` must be ≤ `dailyPurchaseLimit`; all values must be ≥ 0 |
| `CARD_PRODUCT_NOT_FOUND` | 404 | Card product not found | Verify the `productId` from `GET /card-products` |
| `CARD_DISPUTE_INVALID_STATE` | 409 | Dispute cannot be moved to requested state | Check current dispute state; only valid transitions are allowed |
| `CARD_DISPUTE_NOT_FOUND` | 404 | Dispute not found | Verify the dispute ID |
| `API_KEY_SCOPE_INSUFFICIENT` | 403 | API key lacks required scope for this operation | Re-issue the API key with required scopes (`cards:write`, `webhooks:write`, etc.) |
| `WEBHOOK_NOT_FOUND` | 404 | Webhook not found | Verify the webhook ID |
| `WEBHOOK_URL_INVALID` | 400 | Callback URL must be HTTPS | Use an `https://` URL |
| `TOKEN_NOT_FOUND` | 404 | Token not found | Verify the token reference |
| `TOKEN_INVALID_STATE` | 422 | Token is not in a state that allows this operation | Check token status |

---

## Account Errors (Internal API)

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `ACCOUNT_NOT_FOUND` | 404 | Account not found | Verify the account ID |
| `ACCOUNT_NOT_ACTIVE` | 422 | Account is not in ACTIVE state | Activate the account before transacting |
| `ACCOUNT_FROZEN` | 422 | Account is frozen | Unfreeze via `POST /{id}?command=unfreeze` |
| `ACCOUNT_CLOSED` | 422 | Account is closed | Closed accounts are read-only |
| `ACCOUNT_DORMANT` | 422 | Account is dormant | Reactivate the account |
| `ACCOUNT_BALANCE_TOO_LOW` | 422 | Balance would fall below minimum balance | Ensure balance stays above `minimumBalance` |
| `ACCOUNT_OVERDRAFT_NOT_PERMITTED` | 422 | Overdraft is not enabled for this account | Check deposit product configuration |
| `INSUFFICIENT_FUNDS` | 422 | Account has insufficient funds for this debit | Check available balance; overdraft limit if applicable |
| `DUPLICATE_ACCOUNT_NUMBER` | 409 | Account number already exists | Use a different account number or let the system auto-generate |

---

## Loan Errors

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `LOAN_NOT_FOUND` | 404 | Loan not found | Verify the loan ID |
| `LOAN_INVALID_STATE` | 409 | Loan command not valid in current state | Check the loan lifecycle state machine |
| `LOAN_ALREADY_DISBURSED` | 409 | Loan has already been disbursed | No action; loan is already active |
| `LOAN_IN_ARREARS` | 422 | Loan is in arrears — additional checks required | Resolve arrears before performing this operation |
| `LOAN_WRITTEN_OFF` | 422 | Loan has been written off | Written-off loans are read-only |
| `LOAN_REPAYMENT_EXCEEDS_OUTSTANDING` | 422 | Repayment amount exceeds outstanding balance | Set amount ≤ outstanding balance or use `closeAsWrittenOff` |
| `LOAN_CHARGE_NOT_FOUND` | 404 | Loan charge not found | Verify the charge ID belongs to this loan |
| `LOAN_PRODUCT_NOT_FOUND` | 404 | Loan product not found | Verify the `productId` |
| `LOAN_PRINCIPAL_OUT_OF_RANGE` | 400 | Principal amount outside product min/max range | Check `GET /loan-products/{id}` for allowed range |

---

## Customer Errors

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `CUSTOMER_NOT_FOUND` | 404 | Customer not found | Verify the customer ID |
| `CUSTOMER_KYC_NOT_ACTIVE` | 422 | Customer KYC is not ACTIVE | Complete KYC verification before opening accounts |
| `CUSTOMER_ALREADY_EXISTS` | 409 | Customer with this national ID already exists | Retrieve existing customer record |
| `CUSTOMER_INVALID_STATE` | 409 | Command not valid in customer's current state | Check customer status |
| `CUSTOMER_HAS_ACTIVE_ACCOUNTS` | 422 | Customer cannot be closed — active accounts exist | Close all accounts before closing the customer |

---

## Validation Errors

These errors include a `field` property identifying which input caused the problem.

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `VALIDATION_REQUIRED` | 400 | Field is required | Supply the missing field |
| `VALIDATION_FORMAT` | 400 | Field format is invalid | Check date formats (`YYYY-MM-DD`), ISO codes, etc. |
| `VALIDATION_MIN_LENGTH` | 400 | Field is shorter than minimum length | Increase field length |
| `VALIDATION_MAX_LENGTH` | 400 | Field exceeds maximum length | Truncate field value |
| `VALIDATION_MIN_VALUE` | 400 | Value is below minimum | Increase the numeric value |
| `VALIDATION_MAX_VALUE` | 400 | Value exceeds maximum | Decrease the numeric value |
| `VALIDATION_ENUM` | 400 | Value is not one of the allowed values | Check the API reference for valid enum values |
| `INVALID_CURRENCY` | 400 | Currency code is not a valid ISO 4217 code | Use a 3-letter ISO 4217 currency code (e.g. `GBP`, `USD`, `KES`) |
| `INVALID_SORT_CODE` | 400 | Sort code is not 6 digits | UK sort code format: `NNNNNN` (no hyphens) |

---

## Rate Limit Errors

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit exceeded | Wait until `Retry-After` seconds, then retry with exponential backoff |

See [Rate Limiting](/docs/rate-limiting) for tier limits and backoff strategy.

---

## Server Errors

| Code | HTTP | Message | Remediation |
|------|------|---------|-------------|
| `INTERNAL_ERROR` | 500 | An unexpected error occurred | Retry with exponential backoff; contact [api-support@nubbank.com](mailto:api-support@nubbank.com) if it persists |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable | Check the [status page](https://status.nubbank.com); retry after a delay |
| `UPSTREAM_TIMEOUT` | 504 | Upstream service did not respond in time | Retry; check `x-fapi-interaction-id` header for trace correlation |

---

## Error Handling Best Practices

### Always check `errors[]`

Even on `4xx`, inspect `errors[].code` rather than relying on the HTTP status alone — the code gives precise remediation guidance.

### Use `x-fapi-interaction-id` for support

Every request should include:
```
x-fapi-interaction-id: {uuidv4}
```

If you need to open a support ticket, include the interaction ID and timestamp so the NubBank team can trace the full request.

### Idempotency keys on retries

On `5xx` errors or network failures, retry with the **same** `x-idempotency-key` — the server will return the same response if it already processed the request, preventing duplicate operations.

On `4xx` validation errors, **generate a new** idempotency key after fixing the data — the old key is bound to the failed attempt.
