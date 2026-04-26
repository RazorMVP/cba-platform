---
id: webhooks
title: Webhooks
sidebar_position: 6
description: Real-time event delivery for payments, consents, cards, and partner lifecycle events.
---

# Webhooks

NubBank delivers events to your endpoint via HTTPS POST with HMAC-SHA256 signatures.

---

## Register a Webhook

### Partner webhooks (Open Banking events)

```bash
curl -X POST https://sandbox.nubbank.com/api/v1/partners/{orgId}/webhooks \
  -H "Authorization: Bearer PARTNER_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Production Webhook",
    "callbackUrl": "https://yourapp.com/webhooks/nubbank",
    "events": ["PAYMENT.COMPLETED", "CONSENT.AUTHORISED", "CONSENT.REVOKED"],
    "secret": "your-signing-secret-min-32-chars"
  }'
```

### Card API webhooks (card & authorization events)

```bash
curl -X POST https://sandbox.nubbank.com/card-api/v1/webhooks \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Card Events",
    "callbackUrl": "https://yourapp.com/webhooks/cards",
    "events": ["AUTHORIZATION.APPROVED", "CARD.BLOCKED"],
    "secret": "your-signing-secret-min-32-chars"
  }'
```

---

## Verify Webhook Signatures

Every request from NubBank includes:

| Header | Value |
|--------|-------|
| `X-CBA-Signature` | `sha256=HMAC_SHA256(payload, secret)` — hex encoded |
| `X-CBA-Event` | Event type (e.g. `AUTHORIZATION.APPROVED`) |
| `X-CBA-Delivery` | Unique delivery UUID — use for idempotency |
| `Content-Type` | `application/json` |

**Verification example (Node.js):**

```javascript
const crypto = require('crypto');

function verifyWebhook(payload, signature, secret) {
  const expected = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('hex');
  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(expected)
  );
}

// In your Express handler:
app.post('/webhooks/nubbank', (req, res) => {
  const sig = req.headers['x-cba-signature'];
  const raw = req.rawBody; // ensure you capture raw body
  if (!verifyWebhook(raw, sig, process.env.WEBHOOK_SECRET)) {
    return res.status(401).send('Invalid signature');
  }
  // process event...
  res.status(200).send('OK');
});
```

**Python:**
```python
import hmac, hashlib

def verify_webhook(payload: bytes, signature: str, secret: str) -> bool:
    expected = 'sha256=' + hmac.new(
        secret.encode(), payload, hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(signature, expected)
```

:::warning
Always verify the signature before processing the payload. Never process events from unverified sources.
:::

---

## Retry Policy

If your endpoint returns a non-`2xx` response (or times out), NubBank retries with exponential backoff:

| Attempt | Delay |
|---------|-------|
| 1 | Immediate |
| 2 | 15 seconds |
| 3 | 60 seconds |
| 4 | 5 minutes |
| 5 | 30 minutes |
| 6 | 2 hours |

After 6 attempts, the delivery is marked `FAILED`. Check delivery logs via:

```bash
GET /api/v1/partners/{orgId}/webhooks/{webhookId}/deliveries
```

---

## Best Practices

1. **Respond quickly** — return `200` within 5 seconds; process async if needed.
2. **Use `X-CBA-Delivery`** for idempotency — the same delivery UUID means the same event.
3. **Verify every signature** — reject requests without a valid `X-CBA-Signature`.
4. **Use HTTPS** — HTTP callback URLs are rejected.
5. **Handle retries gracefully** — your handler must be idempotent.

---

## Partner Webhook Event Catalogue

### Consent events

| Event | Trigger |
|-------|---------|
| `CONSENT.CREATED` | Customer starts the Open Banking authorisation flow |
| `CONSENT.AUTHORISED` | Customer grants consent |
| `CONSENT.REVOKED` | Consent revoked by customer or partner |
| `CONSENT.EXPIRED` | Consent passed its `ExpirationDateTime` |

### Payment events

| Event | Trigger |
|-------|---------|
| `PAYMENT.INITIATED` | Payment consent created |
| `PAYMENT.COMPLETED` | Payment processed successfully |
| `PAYMENT.FAILED` | Payment declined or error |
| `PAYMENT.REVERSED` | Payment reversed |

### Funds confirmation

| Event | Trigger |
|-------|---------|
| `FUNDS.CONFIRMED` | Funds confirmation request processed |

### Account events

| Event | Trigger |
|-------|---------|
| `ACCOUNT.ACCESS_GRANTED` | AISP consent authorised |
| `ACCOUNT.BALANCE_UPDATED` | Account balance changed (for monitoring) |

### Partner lifecycle

| Event | Trigger |
|-------|---------|
| `APPLICATION.APPROVED` | Production application approved by NubBank |
| `APPLICATION.REJECTED` | Production application rejected |
| `API_KEY.CREATED` | New API key issued |
| `API_KEY.REVOKED` | API key revoked |
| `RATE_LIMIT.WARNING` | 80% of rate limit consumed in current window |
| `RATE_LIMIT.EXCEEDED` | Rate limit exceeded — `429` being returned |

---

## Card API Webhook Event Catalogue

### Authorization events

| Event | Trigger |
|-------|---------|
| `AUTHORIZATION.APPROVED` | Card auth approved |
| `AUTHORIZATION.DECLINED` | Auth declined (funds, fraud, blocked card) |
| `AUTHORIZATION.REVERSED` | Reversal processed |

### Card lifecycle

| Event | Trigger |
|-------|---------|
| `CARD.ISSUED` | New card created |
| `CARD.ACTIVATED` | Card moved to ACTIVE |
| `CARD.BLOCKED` | Card blocked |
| `CARD.UNBLOCKED` | Card unblocked |
| `CARD.EXPIRED` | Card reached expiry date |
| `CARD.PIN_CHANGED` | PIN changed via HSM |
| `CARD.LIMIT_CHANGED` | Spending limit updated |

### Fraud events

| Event | Trigger |
|-------|---------|
| `FRAUD.RULE_TRIGGERED` | Any fraud rule fired (score > 0) |
| `FRAUD.CARD_STEP_UP` | Score 30–69 — step-up to online PIN |
| `FRAUD.CARD_DECLINED_HIGH_RISK` | Score ≥ 70 — transaction declined |

### Dispute events

| Event | Trigger |
|-------|---------|
| `DISPUTE.RAISED` | New dispute raised |
| `DISPUTE.RESOLVED` | Dispute resolved |

---

## Payload Example

```json
{
  "eventId": "evt_abc123",
  "eventType": "PAYMENT.COMPLETED",
  "orgId": "org_xyz789",
  "timestamp": "2026-04-26T14:30:00Z",
  "data": {
    "paymentId": "pmt_111",
    "amount": 5000,
    "currency": "GBP",
    "status": "COMPLETED",
    "reference": "Order-001"
  }
}
```
