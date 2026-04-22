---
id: card
title: Card API
sidebar_position: 2
description: API key management, card issuance, controls, analytics, and webhooks — full BaaS-grade card platform.
---

# Card API

NubBank's Card API provides a full BaaS-grade card issuing platform — virtual and physical card issuance, EMV/contactless, fraud rules, spending analytics, and webhooks.

**Base URL:** `https://api.nubbank.com/card-api/v1/`  
**Auth:** API Key (M2M) or OAuth2 PKCE (customer-facing controls)

---

## Authentication Modes

| Operation | Auth | Header |
|-----------|------|--------|
| Card issuance, webhooks, analytics | API Key | `Authorization: ApiKey cba_live_...` |
| Card controls (freeze, limits, PIN) | FAPI 2.0 JWT | `Authorization: Bearer TOKEN` |

---

## API Key Management

### Issue API Key

```
POST /api-keys
Authorization: ApiKey ADMIN_KEY
```

```json
{
  "name": "Payment Processor - Production",
  "scopes": ["cards:read", "cards:write", "webhooks:write", "analytics:read"]
}
```

**Response `201`** — the `key` field is shown **once only**:

```json
{
  "data": {
    "id": "ak_01HXYZ",
    "name": "Payment Processor - Production",
    "key": "cba_live_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "scopes": ["cards:read", "cards:write"],
    "createdAt": "2026-04-15T14:00:00Z"
  }
}
```

### List API Keys

```
GET /api-keys
```

Returns hashed keys — the plaintext value is never retrievable after creation.

### Revoke API Key

```
DELETE /api-keys/{id}
```

---

## Card Issuance

### Issue a Card

```
POST /cards
Authorization: ApiKey YOUR_KEY
```

```json
{
  "customerId": "cust_01HXYZ",
  "accountId": "acct_01HXYZ",
  "productId": "prod_01HXYZ",
  "cardType": "VIRTUAL",
  "embossedName": "JANE SMITH"
}
```

**Response `201`:**

```json
{
  "data": {
    "id": "card_01HXYZ",
    "customerId": "cust_01HXYZ",
    "cardType": "VIRTUAL",
    "status": "ACTIVE",
    "pan": "****1234",
    "expiryDate": "04/29",
    "productId": "prod_01HXYZ",
    "createdAt": "2026-04-15T14:00:00Z"
  }
}
```

### List Cards

```
GET /cards?customerId=cust_01HXYZ&type=VIRTUAL&status=ACTIVE
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | UUID | Filter by customer |
| `type` | `VIRTUAL` \| `PHYSICAL` | Card type |
| `status` | `ACTIVE` \| `BLOCKED` \| `CANCELLED` | Card status |

### Get Card

```
GET /cards/{cardId}
```

---

## Card Controls

These endpoints require the customer to authorise via FAPI 2.0 consent (scope: `card_read`).

### Freeze / Unfreeze Card

```
PUT /cards/{cardId}/controls
Authorization: Bearer CUSTOMER_TOKEN
```

```json
{
  "frozen": true,
  "contactlessEnabled": true,
  "cardNotPresentEnabled": false,
  "internationalEnabled": true
}
```

### Update Spending Limits

```
PUT /cards/{cardId}/limits
```

```json
{
  "dailyPurchaseLimit": 50000,
  "dailyWithdrawalLimit": 20000,
  "perTransactionLimit": 10000,
  "monthlyLimit": 200000,
  "currencyCode": "404"
}
```

:::note Currency format
Limits use **ISO 4217 numeric codes** and **minor units** (cents/kobo/pence). `50000` for currency `"840"` = $500.00 USD.
:::

### Change PIN

```
POST /cards/{cardId}/pin/change
Authorization: Bearer CUSTOMER_TOKEN
```

```json
{
  "currentPinBlock": "ENCRYPTED_CURRENT_PIN",
  "newPinBlock": "ENCRYPTED_NEW_PIN"
}
```

PIN blocks are encrypted using ISO-0 format via your integration's HSM. Contact NubBank support for PIN block encryption setup.

---

## Transaction & Authorization History

### Authorization Log

```
GET /cards/{cardId}/authorizations?from=2026-04-01&to=2026-04-15
```

Returns all authorization attempts including declined transactions, fraud scores, and entry mode.

**Response:**

```json
{
  "data": [
    {
      "id": "auth_01HXYZ",
      "cardId": "card_01HXYZ",
      "amount": 1500,
      "currencyCode": "840",
      "merchantName": "Amazon.com",
      "merchantId": "AMZN001",
      "mcc": "5942",
      "entryMode": "CONTACTLESS",
      "responseCode": "00",
      "decision": "APPROVE",
      "fraudScore": 12,
      "createdAt": "2026-04-15T14:00:00Z"
    }
  ]
}
```

### Settled Transactions

```
GET /cards/{cardId}/transactions
```

Returns only cleared and settled transactions (response code `00`).

---

## Spending Analytics

### Spend by Category

```
GET /cards/{cardId}/analytics/by-category?from=2026-04-01&to=2026-04-30&currency=840
```

**Response:**

```json
{
  "data": {
    "period": { "from": "2026-04-01", "to": "2026-04-30" },
    "categories": [
      { "category": "Dining", "totalAmount": 45000, "transactionCount": 12 },
      { "category": "Travel", "totalAmount": 120000, "transactionCount": 3 },
      { "category": "Retail", "totalAmount": 89000, "transactionCount": 8 }
    ]
  }
}
```

### Spend by Merchant

```
GET /cards/{cardId}/analytics/by-merchant?from=2026-04-01&to=2026-04-30
```

### Monthly Summary

```
GET /analytics/summary?from=2026-01-01&to=2026-04-30
```

Returns monthly spend totals, approved vs. declined ratio, and average transaction value across all cards under the API key.

---

## Webhook Management

### Register Webhook

```
POST /webhooks
Authorization: ApiKey YOUR_KEY
```

```json
{
  "name": "Production Events",
  "callbackUrl": "https://your-app.com/webhooks/nubbank",
  "events": ["AUTHORIZATION.APPROVED", "AUTHORIZATION.DECLINED", "CARD.ISSUED"],
  "secret": "your_signing_secret_min_32_chars"
}
```

**Response:** The `secret` is stored for HMAC signature verification. Store your secret securely — it is not retrievable after registration.

### Verify Webhook Signatures

NubBank signs every delivery with HMAC-SHA256:

```
X-CBA-Signature: sha256=HEX_DIGEST
X-CBA-Event: AUTHORIZATION.APPROVED
X-CBA-Delivery: UNIQUE_DELIVERY_UUID
```

**Verification (Node.js):**

```javascript
const crypto = require('crypto');

function verifySignature(payload, signature, secret) {
  const expected = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(payload, 'utf8')
    .digest('hex');
  return crypto.timingSafeEqual(
    Buffer.from(expected),
    Buffer.from(signature)
  );
}
```

### Webhook Events

| Category | Event | Trigger |
|----------|-------|---------|
| Authorization | `AUTHORIZATION.APPROVED` | Auth approved |
| Authorization | `AUTHORIZATION.DECLINED` | Auth declined |
| Authorization | `AUTHORIZATION.REVERSED` | Reversal processed |
| Card Lifecycle | `CARD.ISSUED` | New card created |
| Card Lifecycle | `CARD.ACTIVATED` | Card activated |
| Card Lifecycle | `CARD.BLOCKED` | Card blocked |
| Card Lifecycle | `CARD.UNBLOCKED` | Card unblocked |
| Card Lifecycle | `CARD.EXPIRED` | Card expired (nightly job) |
| Card Lifecycle | `CARD.PIN_CHANGED` | PIN changed via HSM |
| Card Lifecycle | `CARD.LIMIT_CHANGED` | Spending limit updated |
| Fraud | `FRAUD.RULE_TRIGGERED` | Any fraud rule fired |
| Fraud | `FRAUD.CARD_STEP_UP` | Score 30–69: online PIN required |
| Fraud | `FRAUD.CARD_DECLINED_HIGH_RISK` | Score ≥ 70: declined |
| Dispute | `DISPUTE.RAISED` | Customer raised dispute |
| Dispute | `DISPUTE.RESOLVED` | Dispute resolved |

### List Webhooks

```
GET /webhooks
```

### Deregister Webhook

```
DELETE /webhooks/{webhookId}
```

### Delivery Log

```
GET /webhooks/{webhookId}/deliveries
```

Returns the last 100 delivery attempts with HTTP status, payload, and retry history.

---

## Disputes

### Raise a Dispute

```
POST /disputes
```

```json
{
  "cardId": "card_01HXYZ",
  "transactionRef": "RRN_FROM_AUTHORIZATION",
  "disputeReason": "UNAUTHORIZED",
  "originalAmount": "165.88"
}
```

### Dispute Status Flow

```
RAISED → RETRIEVAL_REQUESTED → CHARGEBACK_INITIATED
       → REPRESENTMENT → PRE_ARBITRATION → RESOLVED
```

### Get Dispute

```
GET /disputes/{disputeId}
```

---

## Response Codes

| Code | Meaning |
|------|---------|
| `00` | Approved |
| `05` | Do not honor (general decline) |
| `51` | Insufficient funds |
| `54` | Expired card |
| `57` | Transaction not permitted to cardholder |
| `62` | Restricted card (fraud engine block) |
| `91` | Issuer or switch inoperative |
