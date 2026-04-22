---
id: webhooks
title: Webhook Guide
sidebar_position: 6
description: Setup, HMAC verification, retry behaviour, and the complete event catalogue.
---

# Webhook Guide

Webhooks let NubBank push real-time event notifications to your server instead of polling.

---

## How Webhooks Work

```
NubBank Event → Webhook Delivery → Your Endpoint (HTTPS POST)
                       ↓
               X-CBA-Signature (HMAC-SHA256)
               X-CBA-Event
               X-CBA-Delivery (UUID)
```

1. An event occurs on the NubBank platform (e.g. a card authorisation is approved)
2. NubBank sends an HTTPS `POST` to your registered `callbackUrl`
3. Your server verifies the HMAC-SHA256 signature and responds with `2xx`
4. If your server returns anything other than `2xx`, NubBank retries with exponential backoff

---

## Register a Webhook

```bash
POST /card-api/v1/webhooks
Authorization: ApiKey YOUR_KEY
```

```json
{
  "name": "Production Card Events",
  "callbackUrl": "https://your-app.com/webhooks/nubbank",
  "events": [
    "AUTHORIZATION.APPROVED",
    "AUTHORIZATION.DECLINED",
    "CARD.ISSUED",
    "CARD.BLOCKED"
  ],
  "secret": "your_random_signing_secret_at_least_32_chars"
}
```

:::tip Choose a strong secret
Use `openssl rand -hex 32` to generate a cryptographically random 64-character secret.
:::

---

## Verifying Signatures

Every delivery includes the `X-CBA-Signature` header:

```
X-CBA-Signature: sha256=a4bde12...
X-CBA-Event: AUTHORIZATION.APPROVED
X-CBA-Delivery: 550e8400-e29b-41d4-a716-446655440000
```

**Always verify the signature before processing the payload.**

### Verification Examples

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs groupId="lang">
<TabItem value="node" label="Node.js">

```javascript
const crypto = require('crypto');
const express = require('express');
const app = express();

app.post('/webhooks/nubbank', express.raw({type: 'application/json'}), (req, res) => {
  const signature = req.headers['x-cba-signature'];
  const expected = 'sha256=' + crypto
    .createHmac('sha256', process.env.WEBHOOK_SECRET)
    .update(req.body)
    .digest('hex');

  if (!crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature))) {
    return res.status(401).send('Invalid signature');
  }

  const event = JSON.parse(req.body);
  console.log('Received event:', event.eventType);
  res.status(200).send('OK');
});
```

</TabItem>
<TabItem value="python" label="Python">

```python
import hmac
import hashlib
from flask import Flask, request

app = Flask(__name__)
WEBHOOK_SECRET = os.environ['WEBHOOK_SECRET']

@app.route('/webhooks/nubbank', methods=['POST'])
def handle_webhook():
    signature = request.headers.get('X-CBA-Signature', '')
    expected = 'sha256=' + hmac.new(
        WEBHOOK_SECRET.encode(),
        request.data,
        hashlib.sha256
    ).hexdigest()

    if not hmac.compare_digest(expected, signature):
        return 'Invalid signature', 401

    event = request.json
    print(f"Received event: {event['eventType']}")
    return 'OK', 200
```

</TabItem>
<TabItem value="java" label="Java">

```java
@PostMapping("/webhooks/nubbank")
public ResponseEntity<String> handleWebhook(
    @RequestBody byte[] body,
    @RequestHeader("X-CBA-Signature") String signature) {

  String expected = "sha256=" + computeHmac(body, webhookSecret);
  if (!MessageDigest.isEqual(expected.getBytes(), signature.getBytes())) {
    return ResponseEntity.status(401).body("Invalid signature");
  }

  WebhookEvent event = objectMapper.readValue(body, WebhookEvent.class);
  log.info("Received event: {}", event.getEventType());
  return ResponseEntity.ok("OK");
}
```

</TabItem>
</Tabs>

---

## Retry Behaviour

If your endpoint returns a non-`2xx` response or times out (30s), NubBank retries:

| Attempt | Delay |
|---------|-------|
| 1st retry | 15 seconds |
| 2nd retry | 60 seconds |
| 3rd retry | 5 minutes |
| 4th retry | 30 minutes |
| 5th retry | 2 hours |

After 5 failed attempts, the delivery is marked `FAILED` and no further retries occur.

:::warning Idempotency
Your endpoint may receive the same event more than once (retry after transient failure). Use `X-CBA-Delivery` as an idempotency key to avoid duplicate processing.
:::

---

## Delivery Log

Inspect past deliveries for a webhook:

```
GET /card-api/v1/webhooks/{webhookId}/deliveries
```

Each entry shows HTTP status, response body, attempt count, and next retry time.

---

## Event Catalogue

### Authorization Events

| Event | Payload Fields |
|-------|---------------|
| `AUTHORIZATION.APPROVED` | `cardId`, `amount`, `currency`, `merchantName`, `responseCode: "00"`, `fraudScore` |
| `AUTHORIZATION.DECLINED` | `cardId`, `amount`, `currency`, `merchantName`, `responseCode`, `fraudScore`, `declineReason` |
| `AUTHORIZATION.REVERSED` | `cardId`, `originalStan`, `amount`, `currency`, `reversalReason` |

### Card Lifecycle Events

| Event | Payload Fields |
|-------|---------------|
| `CARD.ISSUED` | `cardId`, `customerId`, `cardType`, `productId` |
| `CARD.ACTIVATED` | `cardId`, `activatedAt` |
| `CARD.BLOCKED` | `cardId`, `blockedAt`, `blockedBy` |
| `CARD.UNBLOCKED` | `cardId`, `unblockedAt` |
| `CARD.EXPIRED` | `cardId`, `expiredAt` |
| `CARD.PIN_CHANGED` | `cardId`, `changedAt` |
| `CARD.LIMIT_CHANGED` | `cardId`, `newLimits`, `changedAt` |

### Fraud Events

| Event | Payload Fields |
|-------|---------------|
| `FRAUD.RULE_TRIGGERED` | `cardId`, `ruleId`, `ruleWeight`, `fraudScore`, `transactionRef` |
| `FRAUD.CARD_STEP_UP` | `cardId`, `fraudScore`, `challengeRequired: "ONLINE_PIN"` |
| `FRAUD.CARD_DECLINED_HIGH_RISK` | `cardId`, `fraudScore`, `triggeredRules` |

### Dispute Events

| Event | Payload Fields |
|-------|---------------|
| `DISPUTE.RAISED` | `disputeId`, `cardId`, `transactionRef`, `reason`, `amount` |
| `DISPUTE.RESOLVED` | `disputeId`, `resolution`, `resolvedBy`, `resolvedAt` |

---

## Sample Payload

```json
{
  "deliveryId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "AUTHORIZATION.APPROVED",
  "timestamp": "2026-04-15T14:30:00.000Z",
  "data": {
    "cardId": "card_01HXYZ",
    "amount": 1500,
    "currencyCode": "840",
    "merchantName": "Starbucks",
    "merchantId": "SBUX001",
    "mcc": "5812",
    "entryMode": "CONTACTLESS",
    "responseCode": "00",
    "decision": "APPROVE",
    "fraudScore": 8,
    "stan": "123456",
    "rrn": "419012345678"
  }
}
```

---

## Security Best Practices

- Use HTTPS only — HTTP `callbackUrl` values are rejected
- Verify `X-CBA-Signature` before any processing
- Respond within **30 seconds** — use a queue if your handler is slow
- Use `X-CBA-Delivery` to deduplicate retries
- Rotate your webhook secret periodically by deregistering and re-registering
