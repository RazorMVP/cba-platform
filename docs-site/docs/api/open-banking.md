---
id: open-banking
title: Open Banking API
sidebar_position: 1
description: AISP, PISP, CBPII — UK Open Banking v3.1 compliant endpoints auto-generated from OpenAPI spec.
---

# Open Banking API

NubBank implements [UK Open Banking v3.1](https://standards.openbanking.org.uk/) with FAPI 2.0 security profile.

**Base URL:** `https://api.nubbank.com/open-banking/v3.1/`  
**Auth:** OAuth2 PKCE — see [Authentication](/docs/authentication)

---

## Account Information (AISP)

Account Information Service Providers (AISPs) can read account data on behalf of customers who have granted consent.

### Required Scopes

`accounts` — request this scope during PKCE authorisation.

### Endpoints

#### Create Account Access Consent

```
POST /account-access-consents
```

Creates a consent resource before redirecting the user.

**Request body:**

```json
{
  "Data": {
    "Permissions": [
      "ReadAccountsDetail",
      "ReadBalances",
      "ReadTransactionsDetail",
      "ReadTransactionsCreditDebit"
    ],
    "ExpirationDateTime": "2026-12-31T00:00:00Z",
    "TransactionFromDateTime": "2026-01-01T00:00:00Z",
    "TransactionToDateTime": "2026-12-31T00:00:00Z"
  },
  "Risk": {}
}
```

**Response `201`:**

```json
{
  "Data": {
    "ConsentId": "urn:nubbank:consent:58923",
    "CreationDateTime": "2026-04-15T14:00:00Z",
    "Status": "AwaitingAuthorisation",
    "Permissions": ["ReadAccountsDetail", "ReadBalances", "ReadTransactionsDetail"]
  },
  "Risk": {}
}
```

---

#### Get Account Access Consent

```
GET /account-access-consents/{ConsentId}
```

---

#### Revoke Account Access Consent

```
DELETE /account-access-consents/{ConsentId}
```

---

#### Get Accounts

```
GET /accounts
```

Returns all accounts covered by the authorised consent.

**Response `200`:**

```json
{
  "Data": {
    "Account": [
      {
        "AccountId": "22289",
        "Status": "Enabled",
        "StatusUpdateDateTime": "2026-01-01T00:00:00Z",
        "Currency": "GBP",
        "AccountType": "Personal",
        "AccountSubType": "CurrentAccount",
        "Nickname": "Current Account",
        "Account": [
          {
            "SchemeName": "UK.OBIE.SortCodeAccountNumber",
            "Identification": "40400412345678",
            "Name": "Jane Smith"
          }
        ]
      }
    ]
  },
  "Links": { "Self": "https://api.nubbank.com/open-banking/v3.1/accounts" },
  "Meta": { "TotalPages": 1 }
}
```

---

#### Get Single Account

```
GET /accounts/{AccountId}
```

---

#### Get Account Balances

```
GET /accounts/{AccountId}/balances
```

**Response `200`:**

```json
{
  "Data": {
    "Balance": [
      {
        "AccountId": "22289",
        "Amount": { "Amount": "1230.00", "Currency": "GBP" },
        "CreditDebitIndicator": "Credit",
        "Type": "InterimAvailable",
        "DateTime": "2026-04-15T14:00:00Z"
      }
    ]
  }
}
```

---

#### Get Account Transactions

```
GET /accounts/{AccountId}/transactions
```

**Query parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `fromBookingDateTime` | ISO 8601 | Filter from this date |
| `toBookingDateTime` | ISO 8601 | Filter to this date |

---

### Card Accounts (Extension)

Card accounts (debit, credit, prepaid) are surfaced alongside bank accounts when the consent includes card scopes.

| Extra Scope | Access |
|-------------|--------|
| `card_read` | Card account data |
| `card_balances_read` | Card available balance |
| `card_transactions_read` | Card authorisation history |

Card accounts appear in `GET /accounts` with `AccountType: "Card"` and `AccountSubType: "DebitCard"` / `"CreditCard"` / `"PrepaidCard"`.

---

## Payment Initiation (PISP)

Payment Initiation Service Providers (PISPs) can initiate domestic payments on behalf of customers.

### Required Scopes

`payments`

### Domestic Payment Flow

```
1. POST /domestic-payment-consents          → ConsentId
2. User authorises → redirect back with code
3. POST /domestic-payments                  → PaymentId
4. GET  /domestic-payments/{PaymentId}      → status polling
```

#### Create Domestic Payment Consent

```
POST /domestic-payment-consents
```

```json
{
  "Data": {
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
    "PaymentContextCode": "EcommerceGoods",
    "MerchantCategoryCode": "5967"
  }
}
```

#### Submit Domestic Payment

```
POST /domestic-payments
Authorization: Bearer ACCESS_TOKEN_WITH_PAYMENT_CONSENT
x-idempotency-key: UNIQUE-UUID
```

```json
{
  "Data": {
    "ConsentId": "urn:nubbank:consent:58923",
    "Initiation": { ... }
  },
  "Risk": { ... }
}
```

Payment status flow: `Pending → AcceptedSettlementInProcess → AcceptedSettlementCompleted`

---

## Funds Confirmation (CBPII)

Card-Based Payment Instrument Issuers (CBPIIs) can check whether sufficient funds are available.

### Required Scopes

`fundsconfirmation`

#### Create Funds Confirmation Consent

```
POST /funds-confirmation-consents
```

#### Check Funds

```
POST /funds-confirmations
```

```json
{
  "Data": {
    "ConsentId": "urn:nubbank:consent:58923",
    "Reference": "Purchase01",
    "InstructedAmount": { "Amount": "20.00", "Currency": "GBP" }
  }
}
```

**Response:**

```json
{
  "Data": {
    "FundsConfirmationId": "58923",
    "ConsentId": "urn:nubbank:consent:58923",
    "CreationDateTime": "2026-04-15T14:00:00Z",
    "Reference": "Purchase01",
    "FundsAvailable": true,
    "InstructedAmount": { "Amount": "20.00", "Currency": "GBP" }
  }
}
```

---

## FAPI 2.0 Required Headers

Every Open Banking request must include:

| Header | Required | Example |
|--------|----------|---------|
| `Authorization` | Yes | `Bearer eyJ...` |
| `x-fapi-interaction-id` | Yes | UUID v4 |
| `x-fapi-customer-ip-address` | Production | `104.10.10.10` |

---

## Permissions Reference

| Permission | Description |
|------------|-------------|
| `ReadAccountsBasic` | Account identifiers only |
| `ReadAccountsDetail` | Full account details including sort code |
| `ReadBalances` | Account balance |
| `ReadTransactionsBasic` | Transaction list without amounts |
| `ReadTransactionsDetail` | Full transaction details |
| `ReadTransactionsCreditDebit` | Credit/debit indicator on transactions |
| `ReadProducts` | Account product information |
| `ReadStandingOrdersDetail` | Standing orders |
| `ReadDirectDebits` | Direct debits |
