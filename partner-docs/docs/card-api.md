---
id: card-api
title: Card API
sidebar_position: 5
description: Issue cards, manage limits and controls, view authorization history, and access spending analytics.
---

# Card API

The Card API is a full BaaS-grade card issuance and management API.

**Base path:** `https://sandbox.nubbank.com/card-api/v1/`

**Auth:** `Authorization: ApiKey cba_YOUR_KEY` for M2M operations.

---

## Card Types

| Type | Links to | Auth check |
|------|----------|------------|
| **Debit** | Bank account (savings/checking) | Real-time balance |
| **Prepaid** | Prepaid wallet | Wallet balance |
| **Credit** | Revolving loan line | `creditLimit - outstanding` |

---

## Issue a Card

```bash
curl -X POST https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_demo_001",
    "accountId": "acct_demo_savings",
    "productId": "prod_debit_standard",
    "cardType": "VIRTUAL"
  }'
```

**Response:**

```json
{
  "data": {
    "id": "card_abc123",
    "panMasked": "****1234",
    "expiryDate": "04/29",
    "cardType": "DEBIT",
    "virtualFlag": true,
    "status": "ACTIVE",
    "customerId": "cust_demo_001",
    "createdAt": "2026-04-26T10:00:00Z"
  }
}
```

---

## Card Lifecycle

**Virtual card:**
```
ISSUED → ACTIVE → BLOCKED ↔ ACTIVE → EXPIRED
                  ↓
               CANCELLED
```

**Physical card:**
```
ORDERED → PRODUCED → DISPATCHED → ACTIVATION_PENDING → ACTIVE → BLOCKED ↔ ACTIVE → EXPIRED
```

### Commands

```bash
# Block
curl -X POST "https://sandbox.nubbank.com/card-api/v1/cards/{id}?command=block" \
  -H "Authorization: ApiKey cba_YOUR_KEY"

# Unblock
curl -X POST "https://sandbox.nubbank.com/card-api/v1/cards/{id}?command=unblock" \
  -H "Authorization: ApiKey cba_YOUR_KEY"

# Cancel
curl -X POST "https://sandbox.nubbank.com/card-api/v1/cards/{id}?command=cancel" \
  -H "Authorization: ApiKey cba_YOUR_KEY"
```

---

## Card Controls

Update card controls (freeze, contactless, CNP, international):

```bash
curl -X PUT https://sandbox.nubbank.com/card-api/v1/cards/{id}/controls \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "frozen": false,
    "contactlessEnabled": true,
    "cnpEnabled": false,
    "internationalEnabled": false
  }'
```

---

## Spending Limits

```bash
curl -X PUT https://sandbox.nubbank.com/card-api/v1/cards/{id}/limits \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "dailyPurchaseLimit": 50000,
    "dailyWithdrawalLimit": 20000,
    "perTransactionLimit": 10000,
    "monthlyLimit": 200000
  }'
```

:::info
All amounts are in minor units (e.g. `50000` = £500.00 for GBP cards).
:::

---

## Authorization History

```bash
curl "https://sandbox.nubbank.com/card-api/v1/cards/{id}/authorizations?page=0&size=20" \
  -H "Authorization: ApiKey cba_YOUR_KEY"
```

**Response fields:**

| Field | Description |
|-------|-------------|
| `stan` | Systems Trace Audit Number (ISO 8583 DE11) |
| `rrn` | Retrieval Reference Number (DE37) |
| `amount` | Transaction amount in minor units |
| `responseCode` | `00`=approved, `51`=insufficient funds, `05`=declined |
| `entryMode` | `CHIP`, `CONTACTLESS`, `SWIPE`, `CNP` |
| `fraudScore` | 0–100 risk score |
| `decision` | `APPROVE`, `STEP_UP`, or `DECLINE` |

---

## Spending Analytics

```bash
# Spend by MCC category
GET /card-api/v1/cards/{id}/analytics/by-category?from=2026-01-01&to=2026-04-30

# Top merchants
GET /card-api/v1/cards/{id}/analytics/by-merchant?from=2026-01-01&to=2026-04-30

# Monthly summary (all cards in org)
GET /card-api/v1/analytics/summary?from=2026-01-01&to=2026-04-30
```

---

## API Key Management

```bash
# Issue a new API key
POST /card-api/v1/api-keys

# List active keys
GET /card-api/v1/api-keys

# Revoke a key
DELETE /card-api/v1/api-keys/{id}
```

---

## Webhook Management

Register webhooks to receive card events in real time:

```bash
curl -X POST https://sandbox.nubbank.com/card-api/v1/webhooks \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Card Events",
    "callbackUrl": "https://yourapp.com/webhooks/cards",
    "events": ["AUTHORIZATION.APPROVED", "CARD.BLOCKED", "FRAUD.CARD_DECLINED_HIGH_RISK"],
    "secret": "your-webhook-secret"
  }'
```

See [Webhooks →](./webhooks) for the full event catalogue and signature verification.

---

## Terminal Simulator (Sandbox Only)

Simulate card transactions in the sandbox:

```bash
# Simulate a purchase
curl -X POST https://sandbox.nubbank.com/api/v1/simulate/purchase \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "cardNumber": "4000000000001234",
    "expiryDate": "04/29",
    "amount": 2500,
    "currency": "840",
    "terminalId": "TERM0001",
    "merchantId": "MERCH001",
    "merchantName": "Test Merchant London",
    "entryMode": "CHIP"
  }'
```

**Response:**
```json
{
  "data": {
    "responseCode": "00",
    "responseDescription": "Approved",
    "authCode": "123456",
    "availableBalance": 250000,
    "stan": "000001",
    "rrn": "261234567890"
  }
}
```
