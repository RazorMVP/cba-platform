---
id: issue-first-card
title: Issue Your First Card
sidebar_position: 1
description: Step-by-step tutorial to issue a virtual debit card and simulate a purchase in the sandbox.
---

# Tutorial: Issue Your First Card

In this tutorial you'll issue a virtual debit card linked to a sandbox account, then simulate a purchase to see it approved.

**Time:** ~10 minutes  
**Requirements:** Sandbox API key with `card:read` and `card:write` scopes

---

## Step 1 — Find a card product

```bash
curl https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey cba_YOUR_KEY"
```

The sandbox has pre-seeded demo cards. To list card products available for issuance, call:

```bash
curl https://sandbox.nubbank.com/api/v1/cards/products \
  -H "Authorization: Bearer YOUR_ADMIN_JWT"
```

Note the product ID of a **DEBIT** type product — you'll need it in the next step.

---

## Step 2 — Issue a virtual card

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

Save the `id` from the response — this is your `CARD_ID`.

---

## Step 3 — Check the card status

```bash
curl https://sandbox.nubbank.com/card-api/v1/cards/CARD_ID \
  -H "Authorization: ApiKey cba_YOUR_KEY"
```

The card should be in `ACTIVE` status immediately for virtual cards.

---

## Step 4 — Simulate a purchase

```bash
curl -X POST https://sandbox.nubbank.com/api/v1/simulate/purchase \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "cardNumber": "CARD_PAN_FROM_PORTAL",
    "expiryDate": "04/29",
    "amount": 1500,
    "currency": "840",
    "terminalId": "TERM0001",
    "merchantId": "MERCH001",
    "merchantName": "Coffee Shop London",
    "entryMode": "CHIP"
  }'
```

**Expected response (approved):**

```json
{
  "data": {
    "responseCode": "00",
    "responseDescription": "Approved",
    "authCode": "123456",
    "availableBalance": 498500,
    "stan": "000001",
    "rrn": "261234567890"
  }
}
```

Response code `00` = approved. ✓

---

## Step 5 — View the authorization log

```bash
curl https://sandbox.nubbank.com/card-api/v1/cards/CARD_ID/authorizations \
  -H "Authorization: ApiKey cba_YOUR_KEY"
```

You'll see the authorization with its fraud score, entry mode, and response code.

---

## What's next?

- [Set up a webhook](../webhooks) to receive `AUTHORIZATION.APPROVED` events in real time
- [Manage card limits](../card-api#spending-limits) to control daily spend
- [Block and unblock](../card-api#commands) the card to test lifecycle transitions
