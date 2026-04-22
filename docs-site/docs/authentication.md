---
id: authentication
title: Authentication
sidebar_position: 2
description: OAuth2 PKCE flow, API key setup, token refresh, and FAPI 2.0 explanation.
---

# Authentication

NubBank supports two authentication methods depending on the API you are using.

## Authentication Methods

| API | Method | When to Use |
|-----|--------|-------------|
| Open Banking v3.1 | OAuth2 PKCE (FAPI 2.0) | Customer-facing flows requiring consent |
| Card API (customer controls) | OAuth2 PKCE (FAPI 2.0) | Card controls on behalf of a cardholder |
| Card API (platform/M2M) | API Key | Server-to-server card issuance, webhooks, analytics |
| Internal API | JWT Bearer (Keycloak) | Bank staff applications |

---

## OAuth2 PKCE (FAPI 2.0)

NubBank implements [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-security-profile-2_0-final.html) for all customer-facing flows.

### What is PKCE?

PKCE (Proof Key for Code Exchange) prevents authorisation code interception attacks. Instead of a client secret, the client generates a one-time cryptographic pair:

- **code_verifier** — random string (43–128 characters)
- **code_challenge** — SHA-256 hash of the verifier, base64url-encoded

### Step 1 — Generate PKCE Pair

```javascript
const crypto = require('crypto');

function generateCodeVerifier() {
  return crypto.randomBytes(32).toString('base64url');
}

function generateCodeChallenge(verifier) {
  return crypto.createHash('sha256')
    .update(verifier)
    .digest('base64url');
}

const codeVerifier = generateCodeVerifier();
const codeChallenge = generateCodeChallenge(codeVerifier);
```

### Step 2 — Redirect User to Authorisation

```
GET https://sandbox.nubbank.com/auth/realms/cba/protocol/openid-connect/auth
  ?response_type=code
  &client_id=YOUR_CLIENT_ID
  &redirect_uri=https://your-app.com/callback
  &scope=openid accounts payments
  &code_challenge=BASE64URL_ENCODED_CHALLENGE
  &code_challenge_method=S256
  &state=RANDOM_CSRF_TOKEN
```

NubBank's consent screen presents the requested scopes to the user.

### Step 3 — Exchange Code for Token

After the user consents, your redirect URI receives `?code=AUTH_CODE`.

```bash
curl -X POST https://sandbox.nubbank.com/auth/realms/cba/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "client_id=YOUR_CLIENT_ID" \
  -d "code=AUTH_CODE" \
  -d "code_verifier=YOUR_CODE_VERIFIER" \
  -d "redirect_uri=https://your-app.com/callback"
```

**Response:**

```json
{
  "access_token": "eyJhbGci...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "eyJhbGci...",
  "scope": "openid accounts"
}
```

### Step 4 — Use the Access Token

```bash
curl -X GET https://sandbox.nubbank.com/open-banking/v3.1/accounts \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: UNIQUE-UUID-PER-REQUEST"
```

### Step 5 — Refresh Token

Access tokens expire in **300 seconds**. Use the refresh token to get a new one without user interaction:

```bash
curl -X POST https://sandbox.nubbank.com/auth/realms/cba/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "client_id=YOUR_CLIENT_ID" \
  -d "refresh_token=YOUR_REFRESH_TOKEN"
```

:::warning Token expiry
Refresh tokens expire after **30 days** of inactivity. After expiry, the user must re-authorise. Always check for `401 Unauthorized` responses and initiate a fresh PKCE flow when the refresh token is invalid.
:::

---

## API Keys

API keys are used for server-to-server (M2M) calls — card issuance, webhook registration, spending analytics.

### Format

```
Authorization: ApiKey sk_test_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Sandbox keys are prefixed with `sk_test_`. Production keys use `sk_live_`.

### Issue an API Key

In the Partner Portal → **API Keys** → **Issue Key**:

1. Enter a descriptive name (e.g. "Payment Processor - Production")
2. Select the required **scopes** (only grant what you need)
3. The key value is displayed **once** — copy it immediately

:::danger One-time display
The full API key value is shown only at creation. It cannot be retrieved later. Store it securely in a secrets manager (AWS Secrets Manager, HashiCorp Vault, etc.).
:::

### Available Scopes

| Scope | Access |
|-------|--------|
| `cards:read` | List and view card details |
| `cards:write` | Issue, block, and update cards |
| `webhooks:read` | List webhook registrations |
| `webhooks:write` | Register and deregister webhooks |
| `analytics:read` | Spending analytics and summaries |

### Rotate an API Key

Keys cannot be updated — to rotate:
1. Issue a new key with the same scopes
2. Update your application to use the new key
3. Revoke the old key from the portal

---

## FAPI 2.0 Requirements

For production Open Banking integrations, NubBank enforces the full FAPI 2.0 security profile:

| Requirement | Detail |
|-------------|--------|
| `x-fapi-interaction-id` | UUID per request — used for end-to-end tracing |
| `x-fapi-customer-ip-address` | Customer's IP for PSU presence detection |
| TLS 1.2+ | All connections must use TLS 1.2 or higher |
| PKCE with S256 | Plain challenge method is rejected |
| Token binding | DPoP (Demonstrating Proof of Possession) — required for production |

### Required Headers

```bash
curl https://sandbox.nubbank.com/open-banking/v3.1/accounts \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: 93bac548-d2de-4546-b106-880a5018460d" \
  -H "x-fapi-customer-ip-address: 104.10.10.10"
```

---

## Token Scopes Reference

| Scope | Description |
|-------|-------------|
| `openid` | Required — enables OIDC |
| `accounts` | AISP: read account data |
| `payments` | PISP: initiate domestic payments |
| `fundsconfirmation` | CBPII: check available balance |
| `card_read` | Read card accounts via Open Banking |
| `card_balances_read` | Read card balances |
| `card_transactions_read` | Read card transaction history |

---

## Next Steps

- [Core Concepts](/docs/core-concepts) — consent model, idempotency
- [Getting Started](/docs/getting-started) — make your first API call
- [Open Banking API](/docs/api/open-banking) — full endpoint reference
