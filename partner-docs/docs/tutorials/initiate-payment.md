---
id: initiate-payment
title: Initiate a Payment
sidebar_position: 2
description: Full PISP flow — create a domestic payment consent, authorise it, and submit the payment.
---

# Tutorial: Initiate a Payment (PISP)

This tutorial walks through the full Payment Initiation Service Provider (PISP) flow: creating a payment consent, getting it authorised by the customer, and submitting the payment.

**Time:** ~15 minutes  
**Requirements:** FAPI 2.0 client registration (contact api-support@nubbank.com for sandbox credentials)

---

## Overview

```
Your app                   Keycloak (auth server)        NubBank API
    │                            │                            │
    │── POST /domestic-payment-consents ──────────────────────│
    │                            │                            │
    │ Redirect customer to authorise ────────────────────────▶│
    │                            │◀── Customer grants consent │
    │◀── Callback with auth code │                            │
    │── Exchange code for token ▶│                            │
    │◀── FAPI access token ───────│                            │
    │                            │                            │
    │── POST /domestic-payments ───────────────────────────── │
    │◀── Payment initiated ──────────────────────────────────│
```

---

## Step 1 — Create a payment consent

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/domestic-payment-consents \
  -H "Authorization: Bearer CLIENT_CREDENTIALS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-fapi-interaction-id: $(uuidgen)" \
  -d '{
    "Data": {
      "Initiation": {
        "InstructionIdentification": "INSTR-001",
        "EndToEndIdentification": "E2E-001",
        "InstructedAmount": {
          "Amount": "25.00",
          "Currency": "GBP"
        },
        "CreditorAccount": {
          "SchemeName": "UK.OBIE.SortCodeAccountNumber",
          "Identification": "20000319570701",
          "Name": "Jane Smith"
        },
        "RemittanceInformation": {
          "Reference": "Invoice-42"
        }
      }
    },
    "Risk": {}
  }'
```

**Response:**

```json
{
  "Data": {
    "ConsentId": "consent_abc123",
    "Status": "AwaitingAuthorisation",
    "CreationDateTime": "2026-04-26T10:00:00Z",
    "StatusUpdateDateTime": "2026-04-26T10:00:00Z",
    "ExpirationDateTime": "2026-04-27T10:00:00Z"
  }
}
```

---

## Step 2 — Redirect the customer to authorise

Build the authorisation URL:

```
https://auth.nubbank.com/realms/cba/protocol/openid-connect/auth
  ?client_id=YOUR_CLIENT_ID
  &response_type=code
  &scope=openid payments
  &redirect_uri=https://yourapp.com/callback
  &code_challenge=BASE64URL(SHA256(code_verifier))
  &code_challenge_method=S256
  &state=RANDOM_CSRF_TOKEN
  &request=SIGNED_REQUEST_OBJECT   (FAPI 2.0 PAR recommended)
```

The customer logs in to their NubBank account and approves the payment.

:::tip Sandbox shortcut
In the sandbox, use `PUT /api/v1/consents/consent_abc123/authorise` with admin credentials to skip the browser redirect during development.

```bash
curl -X PUT https://sandbox.nubbank.com/api/v1/consents/consent_abc123/authorise \
  -H "Authorization: Bearer ADMIN_JWT"
```
:::

---

## Step 3 — Exchange the code for a token

```bash
curl -X POST https://auth.nubbank.com/realms/cba/protocol/openid-connect/token \
  -d "grant_type=authorization_code" \
  -d "code=AUTHORIZATION_CODE" \
  -d "redirect_uri=https://yourapp.com/callback" \
  -d "client_id=YOUR_CLIENT_ID" \
  -d "code_verifier=YOUR_CODE_VERIFIER"
```

---

## Step 4 — Submit the payment

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/domestic-payments \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-fapi-interaction-id: $(uuidgen)" \
  -d '{
    "Data": {
      "ConsentId": "consent_abc123",
      "Initiation": {
        "InstructionIdentification": "INSTR-001",
        "EndToEndIdentification": "E2E-001",
        "InstructedAmount": {
          "Amount": "25.00",
          "Currency": "GBP"
        },
        "CreditorAccount": {
          "SchemeName": "UK.OBIE.SortCodeAccountNumber",
          "Identification": "20000319570701",
          "Name": "Jane Smith"
        }
      }
    },
    "Risk": {}
  }'
```

**Response:**

```json
{
  "Data": {
    "DomesticPaymentId": "pmt_xyz789",
    "ConsentId": "consent_abc123",
    "Status": "AcceptedSettlementInProcess",
    "CreationDateTime": "2026-04-26T10:01:00Z"
  }
}
```

---

## Step 5 — Listen for the webhook

If you have a webhook registered with `PAYMENT.COMPLETED`, you'll receive:

```json
{
  "eventType": "PAYMENT.COMPLETED",
  "data": {
    "paymentId": "pmt_xyz789",
    "amount": 2500,
    "currency": "GBP",
    "status": "COMPLETED",
    "reference": "Invoice-42"
  }
}
```

---

## What's next?

- [Set up webhooks](../webhooks) for real-time payment event delivery
- [Funds confirmation (CBPII)](../open-banking#cbpii--funds-confirmation) to pre-check balance before initiating
- [Error reference](../error-reference) for payment-specific error codes
