---
id: open-banking
title: Open Banking v3.1
sidebar_position: 4
description: AISP, PISP, and CBPII endpoints, the consent flow, and how a consent limits what you can reach.
---

# Open Banking v3.1

NubBank's Open Banking API follows UK Open Banking v3.1 resource shapes for account information (AISP), payment initiation (PISP) and funds confirmation (CBPII).

**Base path:** `https://sandbox.nubbank.com/open-banking/v3.1/`

**Authentication:** every call from your platform uses your partner API key:

```text
Authorization: ApiKey cba_YOUR_KEY
```

---

## Consent Flow

You reach a customer's data only through a **consent**: one customer, the scopes they authorised, owned by your organisation.

```text
1. You create a consent      →  POST /open-banking/v3.1/consents
2. The customer authorises   →  PUT  /open-banking/v3.1/consents/{consentId}/authorise  (the customer, signed in)
3. You call AISP/PISP/CBPII  →  naming the consent
4. You or the customer revoke → DELETE /open-banking/v3.1/consents/{consentId}
```

**Consent status flow:**

```text
AWAITING_AUTHORISATION → AUTHORISED → REVOKED / EXPIRED
                                    → CONSUMED   (payment consents, once the payment is made)
```

### What a consent lets you do

- **Only your own consents.** A consent belongs to the organisation whose API key created it. Another organisation's consent id gets `404 CONSENT_NOT_FOUND`, exactly like one that doesn't exist.
- **Only that customer.** Accounts and cards outside the consent's customer get `404 ACCOUNT_NOT_FOUND`, including as a PISP source account or a CBPII account.
- **Only authorised and current.** Before the customer authorises: `403 CONSENT_NOT_AUTHORISED`. After revocation: `403 CONSENT_REVOKED`. After `expiryDate`: `403 CONSENT_EXPIRED`.
- **Only the authorised scopes.** A call outside them gets `403 CONSENT_SCOPE_MISSING`.
- **Only while your organisation is active.** If NubBank suspends your organisation, your API keys and portal logins stop working (`401`; a portal login is `403 PARTNER_SUSPENDED`). Your consents are kept and work again when you're reactivated.

### Create a consent

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/consents \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUSTOMER_ID",
    "scopes": ["accounts_read", "balances_read", "transactions_read"],
    "expiryDate": "2026-12-31T00:00:00Z"
  }'
```

- `customerId` is required. Only that customer can authorise the consent.
- `tppClientId` is optional. The consent is always your organisation's, and any other value is `400 TPP_CLIENT_MISMATCH`.
- `scopes` must come from the list below. Anything else is `400 INVALID_SCOPE`.

The response carries `consentId` (`ob-…`) and status `AWAITING_AUTHORISATION`.

---

## AISP — Account Information

Name the consent in the **`x-consent-id`** header on every AISP call. If it's missing, the call returns `400 CONSENT_ID_REQUIRED`.

### List accounts

```bash
curl https://sandbox.nubbank.com/open-banking/v3.1/aisp/accounts \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "x-consent-id: ob-a3f8c12e9b4d5e6f70" \
  -H "x-fapi-interaction-id: $(uuidgen)"
```

Bank accounts are listed with `accounts_read` and cards with `card_read`. You get whichever of the two your consent grants.

```json
{
  "Data": {
    "Account": [
      {
        "AccountId": "5b9e…",
        "AccountNumber": "****1234",
        "AccountType": "SAVINGS",
        "AccountSubType": "CurrentAccount",
        "Status": "ACTIVE",
        "Currency": "USD"
      }
    ]
  },
  "Meta": { "TotalPages": 1, "TotalCount": 1 },
  "Links": { "Self": "/open-banking/v3.1/aisp/accounts" }
}
```

### Get balances

```bash
curl https://sandbox.nubbank.com/open-banking/v3.1/aisp/accounts/{accountId}/balances \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "x-consent-id: ob-a3f8c12e9b4d5e6f70"
```

Needs `balances_read` for a bank account, or `card_balances_read` for a card.

### Get transactions

```bash
curl "https://sandbox.nubbank.com/open-banking/v3.1/aisp/accounts/{accountId}/transactions?fromBookingDateTime=2026-01-01T00:00:00Z" \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "x-consent-id: ob-a3f8c12e9b4d5e6f70"
```

Needs `transactions_read` for a bank account, or `card_transactions_read` for a card (you get its authorization history).

---

## PISP — Payment Initiation

A payment consent authorises **one payment**: an amount, a currency and a creditor account, named when you create the consent. The customer authorises that payment, not payments in general.

### Create a payment consent

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/consents \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUSTOMER_ID",
    "scopes": ["payments"],
    "expiryDate": "2026-12-31T00:00:00Z",
    "initiation": {
      "instructedAmount": "50.00",
      "currency": "USD",
      "creditorAccountId": "DESTINATION_ACCOUNT_ID",
      "reference": "INV-1001"
    }
  }'
```

- `scopes` is `["payments"]` alone. Ask for account access in a separate consent; mixing them is `400 PAYMENT_CONSENT_SCOPES`.
- `initiation` is required with `payments` (`400 PAYMENT_INITIATION_REQUIRED`), and only with it (`400 INITIATION_WITHOUT_PAYMENTS`).
- `debtorAccountId` is optional. If you set it, the payment must come from that account, which must be the customer's and in `currency`.
- An unknown creditor account is `400 CREDITOR_ACCOUNT_NOT_FOUND`.

### Make the payment

The source account must belong to the consent's customer; otherwise the call returns `404`, and no money moves.

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/pisp/domestic-payments \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "consentId": "ob-a3f8c12e9b4d5e6f70",
    "sourceAccountId": "SOURCE_ACCOUNT_ID",
    "destinationAccountId": "DESTINATION_ACCOUNT_ID",
    "amount": "50.00",
    "currency": "USD",
    "reference": "INV-1001"
  }'
```

The payment must match the consent: amount, currency, `destinationAccountId` (the creditor), the debtor account if you named one, and the reference if both give one. Anything different is `400 PAYMENT_DOES_NOT_MATCH_CONSENT`, naming the field. The source account must be in the instructed currency (`400 CURRENCY_MISMATCH`).

**One payment per consent.** After the payment, the consent is `CONSUMED`:

- Sending the **same** payment again (for example after a timeout) returns it with `200` and the same `domesticPaymentId`. No second payment is made.
- Any other payment under that consent is `403 CONSENT_CONSUMED`. Create a new consent.
- If the payment is refused (for example insufficient funds), nothing changes and the consent can still be used.

`GET /open-banking/v3.1/pisp/domestic-payments/{domesticPaymentId}` returns a payment you initiated, including its `consentId`. Any other payment is `404`.

---

## CBPII — Funds Confirmation

Use this to check that an account (or card) has at least an amount, without moving money. The consent needs `fundsconfirmation`, and a card also needs `card_read` or `card_balances_read`.

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/cbpii/funds-confirmations \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "consentId": "ob-a3f8c12e9b4d5e6f70",
    "accountId": "ACCOUNT_ID",
    "amount": "25.00",
    "currency": "USD"
  }'
```

**Response:**

```json
{
  "fundsConfirmationId": "fc-1a2b3c4d5e6f708192",
  "consentId": "ob-a3f8c12e9b4d5e6f70",
  "fundsAvailable": true,
  "accountId": "ACCOUNT_ID",
  "amount": 25.00,
  "currency": "USD",
  "creationDateTime": "2026-11-03T10:06:00Z"
}
```

---

## Consent Scopes

| Scope | API type | Grants |
| ----- | ------- | ------ |
| `accounts_read` | AISP | Bank accounts in `/aisp/accounts` |
| `balances_read` | AISP | Bank account balances |
| `transactions_read` | AISP | Bank account transactions |
| `payments` | PISP | One domestic payment, as named in the consent's `initiation` |
| `fundsconfirmation` | CBPII | Funds confirmation |
| `card_read` | Card AISP | Cards in `/aisp/accounts`; card funds confirmation |
| `card_balances_read` | Card AISP | Card balances; card funds confirmation |
| `card_transactions_read` | Card AISP | Card authorization history |

---

## Headers

| Header | Required | Description |
| ------ | -------- | ----------- |
| `Authorization` | ✓ | `ApiKey cba_…` |
| `x-consent-id` | AISP calls | The consent you're reading under |
| `x-fapi-interaction-id` | Recommended | UUID per request, for tracing |
| `Content-Type` | On POST/PUT | `application/json` |

---

## Manage Consents (Partner API)

You can also list and revoke your organisation's consents from the partner API:

```bash
# List all consents for this organisation
GET /api/v1/partners/{orgId}/consents
Authorization: Bearer PARTNER_JWT

# Revoke a consent
DELETE /api/v1/partners/{orgId}/consents/{consentId}
Authorization: Bearer PARTNER_JWT
```
