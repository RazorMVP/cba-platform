---
id: rate-limiting
title: Rate Limiting
sidebar_position: 8
description: Per-tier rate limits, headers, and backoff strategies.
---

# Rate Limiting

NubBank enforces fixed-window rate limiting (per minute) on all API endpoints.

---

## Limits by Tier

| Tier | Requests / minute | Who gets it |
|------|-------------------|-------------|
| SANDBOX | 30 | Test/sandbox API keys; unauthenticated fallback |
| BASIC | 100 | Default for all production API keys |
| PRO | 500 | Explicitly upgraded — contact support |
| ENTERPRISE | 2,000 | Enterprise agreements |

---

## Response Headers

Every response includes:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1745678400
```

On `429 Too Many Requests`:

```
Retry-After: 60
```

---

## Recommended Backoff

```javascript
async function withRetry(fn, maxAttempts = 4) {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (err) {
      if (err.status === 429 && attempt < maxAttempts) {
        const delay = Math.pow(2, attempt) * 1000 + Math.random() * 500;
        await new Promise(r => setTimeout(r, delay));
      } else {
        throw err;
      }
    }
  }
}
```

---

## Upgrade your tier

Email [api-support@nubbank.com](mailto:api-support@nubbank.com) with:
- Your organisation ID
- Expected peak RPM
- Use case description

Tier upgrades are applied within 1 business day.
