---
id: manage-consents
title: Manage Open Banking Consents
sidebar_position: 3
description: Create, view, and revoke consents via the Partner API.
---

# Tutorial: Manage Open Banking Consents

As a partner, you can view all Open Banking consents your customers have granted to your organisation, and revoke them on their behalf.

**Time:** ~5 minutes  
**Requirements:** Partner JWT with your organisation ID

---

## List your active consents

```bash
curl https://sandbox.nubbank.com/api/v1/partners/ORG_ID/consents \
  -H "Authorization: Bearer YOUR_PARTNER_JWT"
```

**Response:**

```json
{
  "data": [
    {
      "id": "consent_abc123",
      "status": "AUTHORISED",
      "createdAt": "2026-04-26T10:00:00Z",
      "expiresAt": "2026-12-31T00:00:00Z",
      "permissions": ["ReadAccountsBasic", "ReadBalances"],
      "customerId": "cust_demo_001"
    }
  ]
}
```

---

## Revoke a consent

```bash
curl -X DELETE https://sandbox.nubbank.com/api/v1/partners/ORG_ID/consents/consent_abc123 \
  -H "Authorization: Bearer YOUR_PARTNER_JWT"
```

Returns `204 No Content` on success.

After revocation:
- The consent status becomes `REVOKED`
- Any subsequent AISP/PISP calls using this consent return `403 CONSENT_REVOKED`
- A `CONSENT.REVOKED` webhook event is fired to your registered endpoints

---

## Consent status flow

```
AWAITING_AUTHORISATION  →  Customer must authorise via your app
        ↓
    AUTHORISED          →  Active; AISP/PISP calls succeed
        ↓          ↓
    REVOKED         EXPIRED   →  Both are terminal states
```

---

## Best practices

- **Display active consents** in your user-facing dashboard so customers know what they've authorised.
- **Revoke unused consents** promptly — expired scopes left open represent unnecessary access.
- **Subscribe to `CONSENT.REVOKED` and `CONSENT.EXPIRED`** webhooks to keep your local state in sync without polling.
- **Never cache consent status** beyond a few minutes — always check via the API before initiating payments.

---

## What's next?

- [Initiate a Payment →](./initiate-payment) — use an AUTHORISED consent to submit a payment
- [Webhooks →](../webhooks) — subscribe to consent lifecycle events
