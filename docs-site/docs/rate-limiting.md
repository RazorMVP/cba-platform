---
id: rate-limiting
title: Rate Limiting
sidebar_position: 7
description: Tier table, response headers, 429 handling, and exponential backoff strategy.
---

# Rate Limiting

NubBank enforces rate limits per API key and per user to ensure fair use and platform stability.

---

## Tier Limits

Rate limits are per **60-second rolling window**:

| Tier | Requests/min | Who Gets It |
|------|-------------|-------------|
| **SANDBOX** | 30 | All sandbox keys; unauthenticated IP fallback |
| **BASIC** | 100 | Default for all production API keys and JWT tokens |
| **PRO** | 500 | Explicitly assigned to your API key by NubBank |
| **ENTERPRISE** | 2,000 | Explicitly assigned; requires NubBank contract |

Contact [api-support@nubbank.com](mailto:api-support@nubbank.com) to request a tier upgrade.

---

## Rate Limit Headers

Every API response includes these headers:

| Header | Description | Example |
|--------|-------------|---------|
| `X-RateLimit-Limit` | Your tier's ceiling (requests/min) | `100` |
| `X-RateLimit-Remaining` | Requests remaining in current window | `73` |
| `X-RateLimit-Reset` | Unix timestamp when window resets | `1745678400` |

On `429` responses, an additional header is included:

| Header | Description | Example |
|--------|-------------|---------|
| `Retry-After` | Seconds until you can retry | `60` |

---

## Handling 429 Too Many Requests

### Response Body

```json
{
  "data": null,
  "meta": {},
  "errors": [
    {
      "code": "RATE_LIMIT_EXCEEDED",
      "message": "Rate limit exceeded. Retry after 60 seconds.",
      "field": null
    }
  ]
}
```

### Exponential Backoff

Never retry immediately on a `429`. Use exponential backoff with jitter:

```javascript
async function fetchWithBackoff(url, options, maxRetries = 5) {
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const response = await fetch(url, options);

    if (response.status !== 429) return response;

    if (attempt === maxRetries) throw new Error('Max retries exceeded');

    const retryAfter = parseInt(response.headers.get('Retry-After') || '60', 10);
    const jitter = Math.random() * 1000; // up to 1 second of jitter
    const delay = (retryAfter * 1000) + jitter;

    console.log(`Rate limited. Retrying in ${Math.ceil(delay/1000)}s...`);
    await new Promise(r => setTimeout(r, delay));
  }
}
```

```python
import time
import random
import requests

def fetch_with_backoff(url, headers, max_retries=5):
    for attempt in range(max_retries + 1):
        response = requests.get(url, headers=headers)

        if response.status_code != 429:
            return response

        if attempt == max_retries:
            raise Exception("Max retries exceeded")

        retry_after = int(response.headers.get('Retry-After', 60))
        jitter = random.uniform(0, 1)
        delay = retry_after + jitter

        print(f"Rate limited. Retrying in {delay:.1f}s...")
        time.sleep(delay)
```

---

## Best Practices

### Batch Requests

Use the Batch API (`POST /api/v1/batches`) to execute multiple sub-requests in a single HTTP call:

```bash
POST /api/v1/batches
```

```json
{
  "requestItems": [
    {"requestId": 1, "relativeUrl": "/api/v1/accounts/22289", "method": "GET"},
    {"requestId": 2, "relativeUrl": "/api/v1/accounts/22289/transactions?page=0&size=10", "method": "GET"}
  ]
}
```

### Cache Responses

- Account lists rarely change — cache for 30–60 seconds
- Balance data changes frequently — cache for 5–15 seconds maximum
- Product/configuration data can be cached for hours

### Request Coalescing

If multiple parts of your application need the same data simultaneously, coalesce the requests into one:

```javascript
// Use a shared promise cache to avoid duplicate in-flight requests
const cache = new Map();

async function getAccount(id) {
  if (!cache.has(id)) {
    cache.set(id, fetch(`/api/v1/accounts/${id}`).finally(() => {
      setTimeout(() => cache.delete(id), 10_000); // expire after 10s
    }));
  }
  return cache.get(id);
}
```

---

## Rate Limits by API

| API | Identity Used | Notes |
|-----|--------------|-------|
| Open Banking v3.1 | JWT `sub` claim | Per user, per TPP |
| Card API (API Key auth) | First 16 chars of key hash | Per API key |
| Card API (JWT auth) | JWT `sub` claim | Per user |
| Internal API | JWT `sub` claim | Per staff user |
| Unauthenticated requests | IP address | SANDBOX tier only |

---

## Monitoring Your Usage

Check your remaining quota at any time by inspecting the response headers on any successful request, or by reading the `X-RateLimit-Remaining` header proactively before bulk operations.

For programmatic monitoring, contact NubBank to enable a `/usage` analytics endpoint for your account.
