---
id: issue-card
title: Issue a Card
sidebar_position: 2
description: Step-by-step guide to issuing a virtual card for a customer using the Card API.
---

# Issue a Card

This tutorial walks you through issuing a virtual debit card for a customer using the NubBank Card API.

**Time:** ~10 minutes  
**Prerequisites:** A Card API key with `cards:write` scope.

---

## Step 1 — Ensure the Customer Has an Account

The card must be linked to an existing savings or checking account:

```bash
curl https://sandbox.nubbank.com/api/v1/accounts?customerId=cust_01HXYZ \
  -H "Authorization: ApiKey YOUR_KEY"
```

Note the `accountId` of the account to link the card to.

---

## Step 2 — Choose a Card Product

```bash
curl https://sandbox.nubbank.com/card-api/v1/card-products \
  -H "Authorization: ApiKey YOUR_KEY"
```

**Response:**

```json
{
  "data": [
    {
      "id": "prod_DEBIT_STANDARD",
      "name": "Standard Debit",
      "cardType": "DEBIT",
      "binRangeStart": "44444400",
      "binRangeEnd": "44444499",
      "defaultDailyLimit": 50000
    }
  ]
}
```

---

## Step 3 — Issue the Card

```bash
curl -X POST https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust_01HXYZ",
    "accountId": "acct_01HXYZ",
    "productId": "prod_DEBIT_STANDARD",
    "cardType": "VIRTUAL",
    "embossedName": "JANE SMITH"
  }'
```

**Response `201`:**

```json
{
  "data": {
    "id": "card_01HXYZ",
    "customerId": "cust_01HXYZ",
    "accountId": "acct_01HXYZ",
    "productId": "prod_DEBIT_STANDARD",
    "cardType": "VIRTUAL",
    "status": "ACTIVE",
    "pan": "****1234",
    "expiryDate": "04/29",
    "embossedName": "JANE SMITH",
    "createdAt": "2026-04-15T14:00:00Z"
  }
}
```

The card is immediately **ACTIVE** for virtual cards. Physical cards follow the `ORDERED → PRODUCED → DISPATCHED → ACTIVATION_PENDING → ACTIVE` flow.

---

## Step 4 — Verify the Card

```bash
curl https://sandbox.nubbank.com/card-api/v1/cards/card_01HXYZ \
  -H "Authorization: ApiKey YOUR_KEY"
```

---

## Step 5 — Test the Card (Sandbox)

Use the terminal simulator to fire a test transaction:

```bash
curl -X POST https://sandbox.nubbank.com/api/v1/simulate/purchase \
  -H "Authorization: ApiKey YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "cardNumber": "4444440000001234",
    "expiryDate": "0429",
    "amount": 1000,
    "currency": "840",
    "terminalId": "TERM0001",
    "merchantId": "MERCHANT001",
    "merchantName": "Test Coffee Shop",
    "entryMode": "CONTACTLESS"
  }'
```

**Response:**

```json
{
  "responseCode": "00",
  "description": "Approved",
  "authCode": "ABC123",
  "availableBalance": 49000,
  "stan": "000001",
  "rrn": "419012345678"
}
```

---

## Step 6 — Set Spending Limits (Optional)

```bash
curl -X PUT https://sandbox.nubbank.com/card-api/v1/cards/card_01HXYZ/limits \
  -H "Authorization: ApiKey YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "dailyPurchaseLimit": 50000,
    "dailyWithdrawalLimit": 20000,
    "perTransactionLimit": 10000,
    "monthlyLimit": 200000,
    "currencyCode": "840"
  }'
```

---

## Physical Cards

To issue a physical card, change `"cardType": "PHYSICAL"` in Step 3. The card enters the bureau personalisation workflow:

| Status | Trigger |
|--------|---------|
| `ORDERED` | Card created |
| `PRODUCED` | Bureau confirms chip personalisation |
| `DISPATCHED` | Card dispatched to customer address |
| `ACTIVATION_PENDING` | Awaiting customer activation |
| `ACTIVE` | Customer activated the card |

Physical card activation:

```bash
curl -X POST https://sandbox.nubbank.com/card-api/v1/cards/card_01HXYZ?command=activate \
  -H "Authorization: Bearer CUSTOMER_TOKEN"
```

---

## Next Steps

- [Check Available Funds](/docs/tutorials/check-available-funds) — CBPII tutorial
- [Webhook Guide](/docs/webhooks) — subscribe to card lifecycle events
- [Card API Reference](/docs/api/card) — full endpoint documentation
