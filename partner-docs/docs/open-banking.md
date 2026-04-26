---
id: open-banking
title: Open Banking v3.1
sidebar_position: 4
description: AISP, PISP, and CBPII endpoints following UK Open Banking v3.1 and FAPI 2.0.
---

# Open Banking v3.1

NubBank implements the UK Open Banking Read/Write API Specification v3.1 (FAPI 2.0 profile).

**Base path:** `https://sandbox.nubbank.com/open-banking/v3.1/`

---

## Consent Flow

All Open Banking access requires a **Consent** record that the bank customer explicitly authorises.

```
1. TPP creates consent  →  POST /open-banking/v3.1/account-access-consents
2. Customer authorises  →  PUT  /consents/{id}/authorise  (via your app + Keycloak)
3. TPP accesses data    →  GET  /open-banking/v3.1/accounts  (with authorised consent)
4. TPP revokes consent  →  DELETE /consents/{id}  (or customer revokes in portal)
```

**Consent status flow:**
```
AWAITING_AUTHORISATION → AUTHORISED → REVOKED / EXPIRED
```

---

## AISP — Account Information

### Create consent

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/account-access-consents \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "Data": {
      "Permissions": ["ReadAccountsBasic", "ReadBalances", "ReadTransactionsCredits"],
      "ExpirationDateTime": "2026-12-31T00:00:00Z"
    },
    "Risk": {}
  }'
```

### List accounts

```bash
curl https://sandbox.nubbank.com/open-banking/v3.1/accounts \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: $(uuidgen)"
```

**Response includes bank accounts and card accounts** (debit, credit, prepaid):

```json
{
  "Data": {
    "Account": [
      {
        "AccountId": "acct_demo_savings",
        "AccountType": "Personal",
        "AccountSubType": "CurrentAccount",
        "Currency": "GBP",
        "Nickname": "Main Savings",
        "Account": [{"SchemeName": "UK.OBIE.SortCodeAccountNumber", "Identification": "001SAV0001234"}]
      },
      {
        "AccountId": "card_demo_debit",
        "AccountType": "Personal",
        "AccountSubType": "DebitCard",
        "Currency": "GBP",
        "Nickname": "Debit Card ****1234"
      }
    ]
  }
}
```

### Get balances

```bash
curl https://sandbox.nubbank.com/open-banking/v3.1/accounts/{accountId}/balances \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: $(uuidgen)"
```

### Get transactions

```bash
curl "https://sandbox.nubbank.com/open-banking/v3.1/accounts/{accountId}/transactions?fromBookingDateTime=2026-01-01T00:00:00Z" \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: $(uuidgen)"
```

---

## PISP — Payment Initiation

### Create domestic payment consent

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/domestic-payment-consents \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "Data": {
      "Initiation": {
        "InstructionIdentification": "INSTR001",
        "EndToEndIdentification": "E2E001",
        "InstructedAmount": {"Amount": "50.00", "Currency": "GBP"},
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

### Submit domestic payment

After the customer authorises the consent:

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/domestic-payments \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "Data": {
      "ConsentId": "consent_abc123",
      "Initiation": { ... }
    },
    "Risk": {}
  }'
```

---

## CBPII — Funds Confirmation

Check whether sufficient funds are available without moving money:

```bash
curl -X POST https://sandbox.nubbank.com/open-banking/v3.1/funds-confirmations \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "Data": {
      "ConsentId": "consent_abc123",
      "Reference": "Order-001",
      "InstructedAmount": {"Amount": "25.00", "Currency": "GBP"}
    }
  }'
```

**Response:**
```json
{
  "Data": {
    "FundsAvailableResult": {
      "FundsAvailableDateTime": "2026-04-26T14:30:00Z",
      "FundsAvailable": true
    }
  }
}
```

---

## Consent Scopes

| Scope | API type | Description |
|-------|---------|-------------|
| `ReadAccountsBasic` | AISP | Account list, identifiers |
| `ReadAccountsDetail` | AISP | Full account details |
| `ReadBalances` | AISP | Account balances |
| `ReadTransactionsCredits` | AISP | Credit transactions |
| `ReadTransactionsDebits` | AISP | Debit transactions |
| `payments` | PISP | Domestic payment initiation |
| `fundsconfirmations` | CBPII | Funds confirmation |
| `card_read` | Card AISP | Card account list |
| `card_balances_read` | Card AISP | Card balances |
| `card_transactions_read` | Card AISP | Card authorization history |

---

## Required Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | ✓ | `Bearer {fapi_access_token}` |
| `x-fapi-interaction-id` | ✓ | UUID per request — echoed in response for tracing |
| `x-fapi-customer-ip-address` | Recommended | Customer's IP address |
| `Content-Type` | On POST/PUT | `application/json` |

---

## Manage Consents (Partner API)

Partners can view and revoke their customers' consents:

```bash
# List all consents for this organisation
GET /api/v1/partners/{orgId}/consents
Authorization: Bearer PARTNER_JWT

# Revoke a consent
DELETE /api/v1/partners/{orgId}/consents/{consentId}
Authorization: Bearer PARTNER_JWT
```
