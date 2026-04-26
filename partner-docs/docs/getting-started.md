---
id: getting-started
title: Getting Started
sidebar_position: 1
description: Create your sandbox account and make your first API call in under 5 minutes.
---

# Getting Started

Get your sandbox credentials and make your first NubBank API call in under 5 minutes.

---

## 1. Register your organisation

Go to [partners.nubbank.com](https://partners.nubbank.com) and click **Register**.

- Enter your organisation name and your work email.
- You receive **sandbox access immediately** — no approval required.
- Production access requires a separate application review.

Your new organisation starts in **SANDBOX** environment with a **BASIC** tier (100 requests/min).

---

## 2. Log in to the Partner Portal

```bash
curl -X POST https://sandbox.nubbank.com/api/v1/partners/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "you@yourcompany.com",
    "password": "your-password"
  }'
```

**Response:**

```json
{
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 86400,
    "organisation": {
      "id": "org_abc123",
      "name": "Your Organisation",
      "status": "SANDBOX",
      "tier": "BASIC",
      "environment": "SANDBOX"
    }
  }
}
```

Save the `token` — it's your **Partner JWT**, valid for 24 hours.

---

## 3. Issue an API key

API keys are used for M2M integrations (Card API, webhooks, analytics).

```bash
curl -X POST https://sandbox.nubbank.com/api/v1/partners/org_abc123/api-keys \
  -H "Authorization: Bearer YOUR_PARTNER_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My First Key",
    "scopes": ["card:read", "card:write", "webhook:write"]
  }'
```

**Response:**

```json
{
  "data": {
    "id": "key_xyz789",
    "name": "My First Key",
    "key": "cba_QWERTYuiop...",
    "scopes": ["card:read", "card:write", "webhook:write"],
    "createdAt": "2026-04-26T10:00:00Z"
  }
}
```

:::warning
The `key` value is shown **exactly once**. Copy it now — it cannot be retrieved again.
:::

---

## 4. Make your first API call

With your API key, list sandbox card products:

```bash
curl https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey cba_QWERTYuiop..."
```

Or use the Partner JWT to check your organisation's usage:

```bash
curl https://sandbox.nubbank.com/api/v1/partners/org_abc123/usage \
  -H "Authorization: Bearer YOUR_PARTNER_JWT"
```

---

## Sandbox Test Data

The sandbox is pre-seeded with demo data:

| Resource | ID | Details |
|----------|-----|---------|
| Customer | `cust_demo_001` | Jane Smith, KYC ACTIVE |
| Savings account | `acct_demo_savings` | GBP, balance £5,000 |
| Checking account | `acct_demo_checking` | GBP, balance £12,500 |
| Active loan | `loan_demo_001` | £25,000 personal loan |
| Debit card | `card_demo_debit` | linked to savings account |
| Credit card | `card_demo_credit` | £5,000 credit limit |

---

## Base URLs

| Environment | Base URL |
|-------------|----------|
| Sandbox | `https://sandbox.nubbank.com` |
| Production | `https://api.nubbank.com` |

---

## Next Steps

- [Authentication →](./authentication) — understand all auth methods
- [Open Banking v3.1 →](./open-banking) — access accounts, payments, funds confirmation
- [Card API →](./card-api) — issue cards and manage limits
- [Webhooks →](./webhooks) — receive real-time events
