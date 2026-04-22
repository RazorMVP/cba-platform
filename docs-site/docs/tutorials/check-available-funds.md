---
id: check-available-funds
title: Check Available Funds
sidebar_position: 3
description: Step-by-step CBPII guide to confirm whether sufficient funds are available in a customer account.
---

# Check Available Funds

This tutorial shows how to use the NubBank CBPII (Card-Based Payment Instrument Issuer) API to confirm whether a customer has sufficient funds before processing a payment.

**Time:** ~10 minutes  
**Prerequisites:** A registered sandbox account with `fundsconfirmation` scope.

---

## Overview

The CBPII flow does not move any money. It simply returns a `true` / `false` answer to the question: *"Does this account have at least £X available?"*

```
Step 1: Create funds confirmation consent
Step 2: User authorises
Step 3: POST /funds-confirmations
```

---

## Step 1 — Create Funds Confirmation Consent

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/funds-confirmation-consents \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-fapi-interaction-id: $(uuidgen)" \
  -d '{
    "Data": {
      "DebtorAccount": {
        "SchemeName": "UK.OBIE.SortCodeAccountNumber",
        "Identification": "40400412345678",
        "Name": "Jane Smith"
      },
      "ExpirationDateTime": "2026-12-31T00:00:00Z"
    }
  }'
```

**Response:**

```json
{
  "Data": {
    "ConsentId": "urn:nubbank:cbpii:58924",
    "Status": "AwaitingAuthorisation",
    "CreationDateTime": "2026-04-15T14:00:00Z",
    "ExpirationDateTime": "2026-12-31T00:00:00Z"
  }
}
```

---

## Step 2 — User Authorises

Redirect the user to the NubBank consent screen — same PKCE flow as payment initiation, but with scope `fundsconfirmation`.

After authorisation, exchange the code for an access token as described in [Authentication](/docs/authentication).

---

## Step 3 — Check Available Funds

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/funds-confirmations \
  -H "Authorization: Bearer FUNDS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-fapi-interaction-id: $(uuidgen)" \
  -d '{
    "Data": {
      "ConsentId": "urn:nubbank:cbpii:58924",
      "Reference": "Purchase001",
      "InstructedAmount": {
        "Amount": "20.00",
        "Currency": "GBP"
      }
    }
  }'
```

**Response — funds available:**

```json
{
  "Data": {
    "FundsConfirmationId": "58924-001",
    "ConsentId": "urn:nubbank:cbpii:58924",
    "CreationDateTime": "2026-04-15T14:30:00Z",
    "Reference": "Purchase001",
    "FundsAvailable": true,
    "InstructedAmount": { "Amount": "20.00", "Currency": "GBP" }
  }
}
```

**Response — insufficient funds:**

```json
{
  "Data": {
    "FundsConfirmationId": "58924-002",
    "FundsAvailable": false,
    ...
  }
}
```

---

## Card Account Funds Confirmation

CBPII also works against card accounts (debit, credit, prepaid) when the consent includes `card_balances_read` scope.

For **debit cards**: checks against the linked account's available balance minus any holds.  
For **credit cards**: checks against `creditLimit - outstandingBalance`.  
For **prepaid cards**: checks against the prepaid wallet balance.

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/funds-confirmations \
  -H "Authorization: Bearer CARD_SCOPED_TOKEN" \
  -d '{
    "Data": {
      "ConsentId": "urn:nubbank:cbpii:card_58925",
      "Reference": "OnlineCheckout",
      "InstructedAmount": { "Amount": "49.99", "Currency": "GBP" }
    }
  }'
```

---

## Important Notes

:::info No money is moved
`POST /funds-confirmations` is a read-only operation. It checks the balance snapshot at that moment — no hold is placed, no reservation is made, and the account balance does not change.
:::

:::warning Balance can change
A `FundsAvailable: true` response does not guarantee the funds will still be available when the actual payment is processed. Always initiate the payment immediately after a successful confirmation.
:::

---

## Next Steps

- [Initiate a Payment](/docs/tutorials/initiate-payment) — PISP tutorial
- [Core Concepts](/docs/core-concepts) — consent model details
- [Open Banking API](/docs/api/open-banking) — full CBPII reference
