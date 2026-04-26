---
id: authentication
title: Authentication
sidebar_position: 2
description: Partner JWT, API Key, and FAPI 2.0 OAuth2 authentication methods.
---

# Authentication

NubBank uses three authentication methods depending on who is calling and what they need access to.

---

## Three-Principal Model

| Principal | Auth Method | Use Case |
|-----------|------------|----------|
| **Partner system** | Partner JWT (HMAC-SHA256) | Partner Portal login, org management, usage stats |
| **Partner M2M** | API Key (`ApiKey` scheme) | Card API, webhooks, analytics — server-to-server |
| **Cardholder / bank customer** | FAPI 2.0 OAuth2 (PKCE) | Open Banking consents, card controls on behalf of a user |
| **Bank admin** | Keycloak JWT (Bearer) | Internal/admin operations — not issued to partners |

---

## Partner JWT

Used after logging in via `POST /api/v1/partners/auth/login`.

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Token claims:**

| Claim | Value |
|-------|-------|
| `sub` | Partner user ID |
| `email` | Partner user email |
| `role` | `DEVELOPER` or `ADMIN` |
| `orgId` | Organisation UUID |
| `orgName` | Organisation display name |
| `status` | `SANDBOX` / `PENDING_REVIEW` / `PRODUCTION` / `SUSPENDED` |
| `tier` | `BASIC` / `PRO` / `ENTERPRISE` |
| `environment` | `SANDBOX` or `PRODUCTION` |

**Expiry:** 24 hours. Refresh by logging in again.

**Rate limits** (Partner JWT):

| Tier | Requests / minute |
|------|-------------------|
| BASIC | 100 |
| PRO | 500 |
| ENTERPRISE | 2,000 |

---

## API Key

API keys are issued per organisation for M2M integrations. Use the `ApiKey` auth scheme:

```
Authorization: ApiKey cba_QWERTYuiopASDFGHJK...
```

**Key format:** `cba_` prefix + 43 Base64URL characters (256 bits of entropy).

**Key scopes:**

| Scope | Access |
|-------|--------|
| `card:read` | List and view cards |
| `card:write` | Issue, block, update cards |
| `webhook:read` | View webhooks and delivery logs |
| `webhook:write` | Register and deregister webhooks |
| `analytics:read` | Spending analytics |

:::info
The raw key value is shown **exactly once** at creation. Store it in a secrets manager immediately.
:::

**Rotate a key** by revoking the old one (`DELETE /api/v1/partners/{orgId}/api-keys/{id}`) and issuing a new one. Revocation is immediate — any request using the old key receives `401` within seconds.

---

## FAPI 2.0 OAuth2 (Open Banking)

For Open Banking endpoints that act on behalf of a bank customer, NubBank uses FAPI 2.0:

- **Flow:** Authorization Code with PKCE
- **Authorization server:** Keycloak at `https://auth.nubbank.com/realms/cba`
- **Scope examples:** `accounts:read`, `payments:write`, `fundsconfirmations`
- **Token lifetime:** 300 seconds (access token), 30 days (refresh token)

**Discovery endpoint:**
```
https://auth.nubbank.com/realms/cba/.well-known/openid-configuration
```

**Authorization request (sandbox):**
```
GET https://auth.nubbank.com/realms/cba/protocol/openid-connect/auth
  ?client_id=your-client-id
  &response_type=code
  &scope=openid accounts:read
  &redirect_uri=https://yourapp.com/callback
  &code_challenge=BASE64URL(SHA256(verifier))
  &code_challenge_method=S256
  &state=RANDOM_STATE
```

**Token exchange:**
```bash
curl -X POST https://auth.nubbank.com/realms/cba/protocol/openid-connect/token \
  -d grant_type=authorization_code \
  -d code=AUTHORIZATION_CODE \
  -d redirect_uri=https://yourapp.com/callback \
  -d client_id=your-client-id \
  -d code_verifier=YOUR_VERIFIER
```

Consents must be created and authorised before calling AISP/PISP/CBPII endpoints. See [Open Banking →](./open-banking).

---

## Error Responses

| HTTP Status | Code | Meaning |
|-------------|------|---------|
| `401` | `UNAUTHORIZED` | Missing or invalid credentials |
| `403` | `FORBIDDEN` | Valid credentials but insufficient scope/role |
| `429` | `RATE_LIMIT_EXCEEDED` | Too many requests — check `Retry-After` header |

**Example 401 response:**
```json
{
  "data": null,
  "meta": {},
  "errors": [
    {
      "code": "UNAUTHORIZED",
      "message": "Invalid or expired API key",
      "field": null
    }
  ]
}
```

---

## Rate Limit Headers

Every response includes:

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Requests allowed per minute for your tier |
| `X-RateLimit-Remaining` | Requests remaining in the current window |
| `X-RateLimit-Reset` | Unix timestamp when the window resets |
| `Retry-After` | Seconds to wait (only on `429` responses) |
