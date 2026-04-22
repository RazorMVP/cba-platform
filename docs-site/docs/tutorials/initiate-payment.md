---
id: initiate-payment
title: Initiate a Payment in 3 Steps
sidebar_position: 1
description: Step-by-step guide to initiating a domestic payment via the NubBank PISP API.
---

# Initiate a Payment in 3 Steps

This tutorial walks you through the complete PISP (Payment Initiation Service Provider) flow — from creating a consent to receiving a payment confirmation.

**Time:** ~15 minutes  
**Prerequisites:** A registered sandbox account and a valid PKCE access token with `payments` scope.

---

## Overview

```
Step 1: Create payment consent
Step 2: User authorises (redirect)
Step 3: Submit the payment
```

---

## Step 1 — Create Payment Consent

Before redirecting the user, create a payment consent with the intended payment details:

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/domestic-payment-consents \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-fapi-interaction-id: $(uuidgen)" \
  -H "x-idempotency-key: $(uuidgen)" \
  -d '{
    "Data": {
      "Initiation": {
        "InstructionIdentification": "ACME412",
        "EndToEndIdentification": "FRESCO.21302.GFX.20",
        "InstructedAmount": {
          "Amount": "165.88",
          "Currency": "GBP"
        },
        "CreditorAccount": {
          "SchemeName": "UK.OBIE.SortCodeAccountNumber",
          "Identification": "08080021325698",
          "Name": "Bob Clements"
        },
        "RemittanceInformation": {
          "Reference": "FRESCO-101",
          "Unstructured": "Internal ops code 5120101"
        }
      }
    },
    "Risk": {
      "PaymentContextCode": "EcommerceGoods",
      "MerchantCategoryCode": "5967",
      "MerchantCustomerIdentification": "053598653254"
    }
  }'
```

**Save the `ConsentId` from the response:**

```json
{
  "Data": {
    "ConsentId": "urn:nubbank:consent:58923",
    "Status": "AwaitingAuthorisation",
    "CreationDateTime": "2026-04-15T14:00:00Z"
  }
}
```

---

## Step 2 — Redirect User to Authorise

Build the authorisation URL using the `ConsentId` as the `intent_id`:

```
https://sandbox.nubbank.com/auth/realms/cba/protocol/openid-connect/auth
  ?response_type=code
  &client_id=YOUR_CLIENT_ID
  &redirect_uri=https://your-app.com/callback
  &scope=openid payments
  &state=RANDOM_CSRF_TOKEN
  &code_challenge=BASE64URL_CHALLENGE
  &code_challenge_method=S256
  &claims={"id_token":{"intent_id":{"value":"urn:nubbank:consent:58923","essential":true}}}
```

The user sees NubBank's payment confirmation screen showing:
- Payee name and account
- Amount
- Your application name

After the user confirms, they are redirected to your `redirect_uri` with an authorization code.

---

## Step 3 — Submit the Payment

Exchange the code for a token, then submit the payment:

```bash
# Exchange code for token
curl -X POST https://sandbox.nubbank.com/auth/realms/cba/protocol/openid-connect/token \
  -d "grant_type=authorization_code" \
  -d "client_id=YOUR_CLIENT_ID" \
  -d "code=AUTH_CODE" \
  -d "code_verifier=YOUR_VERIFIER" \
  -d "redirect_uri=https://your-app.com/callback"
```

```bash
# Submit the payment
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/domestic-payments \
  -H "Authorization: Bearer PAYMENT_TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-fapi-interaction-id: $(uuidgen)" \
  -H "x-idempotency-key: $(uuidgen)" \
  -d '{
    "Data": {
      "ConsentId": "urn:nubbank:consent:58923",
      "Initiation": {
        "InstructionIdentification": "ACME412",
        "EndToEndIdentification": "FRESCO.21302.GFX.20",
        "InstructedAmount": { "Amount": "165.88", "Currency": "GBP" },
        "CreditorAccount": {
          "SchemeName": "UK.OBIE.SortCodeAccountNumber",
          "Identification": "08080021325698",
          "Name": "Bob Clements"
        },
        "RemittanceInformation": { "Reference": "FRESCO-101" }
      }
    },
    "Risk": {
      "PaymentContextCode": "EcommerceGoods"
    }
  }'
```

**Response `201`:**

```json
{
  "Data": {
    "DomesticPaymentId": "58923-001",
    "ConsentId": "urn:nubbank:consent:58923",
    "Status": "AcceptedSettlementInProcess",
    "CreationDateTime": "2026-04-15T14:30:00Z",
    "Initiation": { ... }
  }
}
```

---

## Step 4 — Poll for Completion (Optional)

```bash
curl https://sandbox.nubbank.com/open-banking/v3.1/domestic-payments/58923-001 \
  -H "Authorization: Bearer TOKEN"
```

| Status | Meaning |
|--------|---------|
| `Pending` | Queued for processing |
| `AcceptedSettlementInProcess` | Accepted — settlement in progress |
| `AcceptedSettlementCompleted` | Funds debited and transferred |
| `Rejected` | Payment rejected — check `MultiAuthorisation` or `Charges` |

:::tip Webhook alternative
Instead of polling, register a webhook for `AUTHORIZATION.APPROVED` events to get notified instantly when the payment settles.
:::

---

## Common Errors

| Error Code | Cause | Fix |
|------------|-------|-----|
| `OB_CONSENT_INVALID` | ConsentId not found or expired | Re-create consent |
| `OB_CONSENT_NOT_AUTHORISED` | User has not yet authorised | Complete the redirect flow |
| `INSUFFICIENT_BALANCE` | Payer account has insufficient funds | Notify the user |
| `DUPLICATE_SUBMISSION` | Same idempotency key with different data | Generate a new key |

---

## Next Steps

- [Issue a Card](/docs/tutorials/issue-card) — issue a virtual card for a customer
- [Check Available Funds](/docs/tutorials/check-available-funds) — CBPII flow
- [Webhook Guide](/docs/webhooks) — receive real-time payment notifications
