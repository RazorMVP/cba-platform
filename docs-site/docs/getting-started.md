---
id: getting-started
title: Getting Started
sidebar_position: 1
description: Create your account, get sandbox credentials, and make your first NubBank API call end-to-end.
---

# Getting Started

This guide takes you from zero to your first successful API call in under 10 minutes.

## Overview

NubBank provides three API families:

| API | Base URL | Auth Method |
|-----|----------|-------------|
| Open Banking v3.1 | `/open-banking/v3.1/` | FAPI 2.0 (OAuth2 + PKCE) |
| Card API | `/card-api/v1/` | API Key or FAPI 2.0 |
| Internal API | `/api/v1/` | JWT Bearer (Keycloak) |

All APIs are available in **sandbox** (no real money) and **production** environments.

## Step 1 — Create an Account

Register on the [Partner Portal](https://partners.nubbank.com):

1. Go to **Sign Up** → enter your company name, email, and password
2. Your account is immediately activated for **sandbox** access
3. Production access requires a separate approval — see [Apply for Production](#apply-for-production)

## Step 2 — Get Your Sandbox Credentials

After registration, navigate to **API Keys** in the Partner Portal:

```bash
# Sandbox API keys are prefixed with sk_test_
Authorization: ApiKey sk_test_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

For Open Banking (OAuth2 PKCE), copy your **Client ID** from the portal:

```
client_id: your_sandbox_client_id
```

## Step 3 — Make Your First Call

### Option A — API Key (Card API)

```bash
curl -X GET https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey sk_test_your_key_here" \
  -H "Content-Type: application/json"
```

**Expected response:**

```json
{
  "data": [],
  "meta": { "page": 0, "size": 20, "total": 0 },
  "errors": []
}
```

### Option B — OAuth2 PKCE (Open Banking)

**1. Get an access token:**

```bash
curl -X POST https://sandbox.nubbank.com/auth/realms/cba/protocol/openid-connect/token \
  -d "grant_type=authorization_code" \
  -d "client_id=your_sandbox_client_id" \
  -d "code=AUTH_CODE_FROM_REDIRECT" \
  -d "code_verifier=YOUR_PKCE_VERIFIER" \
  -d "redirect_uri=https://your-app.com/callback"
```

**2. Call the Accounts endpoint:**

```bash
curl -X GET https://sandbox.nubbank.com/open-banking/v3.1/accounts \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: $(uuidgen)"
```

## Step 4 — Explore the Sandbox

The sandbox is pre-seeded with test data:

| Resource | Details |
|---------|---------|
| Test customers | 5 demo customers with KYC status ACTIVE |
| Test accounts | Multiple savings + checking accounts |
| Test loans | Active loans in various states |
| Test cards | Debit + credit cards with authorisation history |

Use our [Postman Collection](/docs/sdks-tools) to explore all endpoints with pre-filled examples.

## Apply for Production

When you are ready to go live:

1. In the Partner Portal, go to **Apply for Production**
2. Complete the integration checklist and submit your application
3. NubBank reviews your application within 2–5 business days
4. On approval, production API keys are issued from the portal

:::tip Sandbox first
Always test your full integration in sandbox before applying for production access. Sandbox credentials cannot be used against the production environment.
:::

## Next Steps

- [Authentication](/docs/authentication) — understand OAuth2 PKCE, token refresh, and FAPI 2.0
- [Core Concepts](/docs/core-concepts) — consent model, idempotency keys, pagination
- [Open Banking API](/docs/api/open-banking) — AISP, PISP, CBPII reference
- [Tutorials](/docs/tutorials/initiate-payment) — step-by-step walkthroughs
