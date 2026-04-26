---
id: sdks-tools
title: SDKs & Tools
sidebar_position: 9
description: Postman collection, OpenAPI specs, SDK generation, and interactive API explorer.
---

# SDKs & Tools

Resources to accelerate your NubBank integration.

---

## Postman Collection

The NubBank Postman Collection covers all partner-accessible endpoints with pre-filled bodies, headers, and multi-language code samples.

**Download:** [NubBank Partner Collection v2 (JSON)](pathname:///postman/cba-postman-collection-v2.json)

**API References:**
- [Partner Portal API Reference](pathname:///partner-api-reference.html)
- [Card API Reference](pathname:///card-api-reference.html)

### Import into Postman

1. Open Postman → **Import** → **File**
2. Select the downloaded JSON file
3. The collection appears in your workspace

### Environment Setup

| Variable | Example | Description |
|----------|---------|-------------|
| `base_url` | `https://sandbox.nubbank.com` | Sandbox or production base URL |
| `partner_jwt` | `eyJ...` | Partner JWT from login |
| `api_key` | `cba_...` | Card API key |
| `org_id` | `org_abc123` | Your organisation UUID |
| `access_token` | `eyJ...` | FAPI 2.0 Bearer token |

---

## OpenAPI Specification

Download the machine-readable OpenAPI 3.1 specs:

| Service | Download |
|---------|----------|
| Backend (Partner + Open Banking APIs) | [openapi.yaml](https://sandbox.nubbank.com/api-docs.yaml) |
| Card Service | [card-openapi.yaml](https://sandbox.nubbank.com/card/v3/api-docs.yaml) |

### Generate a typed client

```bash
npm install @openapitools/openapi-generator-cli -g

# TypeScript client
openapi-generator-cli generate \
  -i https://sandbox.nubbank.com/api-docs.yaml \
  -g typescript-fetch \
  -o ./src/generated/nubbank-client

# Python client
openapi-generator-cli generate \
  -i https://sandbox.nubbank.com/api-docs.yaml \
  -g python \
  -o ./nubbank_client

# Java client
openapi-generator-cli generate \
  -i https://sandbox.nubbank.com/api-docs.yaml \
  -g java \
  --library okhttp-gson \
  -o ./nubbank-java-client
```

---

## Interactive API Explorer

Browse and test endpoints in your browser:

- **Partner + Backend API:** [sandbox.nubbank.com/swagger-ui.html](https://sandbox.nubbank.com/swagger-ui.html)
- **Card API:** [sandbox.nubbank.com/card/swagger-ui.html](https://sandbox.nubbank.com/card/swagger-ui.html)

Use the **Authorize** button with your Partner JWT or API key.

---

## cURL Quick Reference

```bash
# List your API keys
curl https://sandbox.nubbank.com/api/v1/partners/ORG_ID/api-keys \
  -H "Authorization: Bearer YOUR_PARTNER_JWT"

# List cards
curl https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey cba_YOUR_KEY"

# List Open Banking accounts
curl https://sandbox.nubbank.com/open-banking/v3.1/accounts \
  -H "Authorization: Bearer FAPI_ACCESS_TOKEN" \
  -H "x-fapi-interaction-id: $(uuidgen)"

# Simulate a card purchase (sandbox)
curl -X POST https://sandbox.nubbank.com/api/v1/simulate/purchase \
  -H "Authorization: ApiKey cba_YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"4000000000001234","expiryDate":"04/29","amount":2500,"currency":"840","terminalId":"TERM0001","merchantId":"MERCH001","merchantName":"Test Shop","entryMode":"CHIP"}'
```

---

## Language Support

The Postman collection includes code samples in:

| Language | Library |
|----------|---------|
| cURL | — |
| JavaScript | `fetch` |
| Python | `requests` |
| Java | OkHttp |
| Go | `net/http` |
| Ruby | `net/http` |
| C# | `HttpClient` |

---

## Sandbox Test Data

| Resource | ID | Details |
|----------|-----|---------|
| Customer | `cust_demo_001` | Jane Smith, KYC ACTIVE |
| Savings account | `acct_demo_savings` | GBP, balance £5,000 |
| Checking account | `acct_demo_checking` | GBP, balance £12,500 |
| Active loan | `loan_demo_001` | £25,000 personal loan |
| Debit card | `card_demo_debit` | linked to savings account |
| Credit card | `card_demo_credit` | £5,000 credit limit |

---

## Support

- **Documentation issues:** [GitHub Issues](https://github.com/RazorMVP/cba-platform/issues)
- **API support:** [api-support@nubbank.com](mailto:api-support@nubbank.com)
- **Partner Portal:** [partners.nubbank.com](https://partners.nubbank.com)
