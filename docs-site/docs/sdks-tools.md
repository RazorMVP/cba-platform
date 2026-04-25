---
id: sdks-tools
title: SDKs & Tools
sidebar_position: 8
description: Postman collection download, OpenAPI spec download, and development tooling.
---

# SDKs & Tools

Resources to accelerate your NubBank integration.

---

## Postman Collection

The NubBank Postman Collection contains all implemented endpoints with pre-filled request bodies, headers, and multi-language code samples.

**Download:** [NubBank API Collection v2 (JSON)](pathname:///postman/cba-postman-collection-v2.json)

**API Reference:** [Full API Reference](pathname:///api-reference.html) · [Card API Reference](pathname:///card-api-reference.html) · [Partner Portal API Reference](pathname:///partner-api-reference.html)

### Import into Postman

1. Open Postman → **Import** → **File**
2. Select the downloaded JSON file
3. The collection appears in your workspace

### Environment Setup

Create a Postman Environment with these variables:

| Variable | Example | Description |
|----------|---------|-------------|
| `base_url` | `https://sandbox.nubbank.com` | Sandbox or production base URL |
| `access_token` | `eyJ...` | OAuth2 Bearer token |
| `api_key` | `sk_test_...` | Card API key |
| `client_id` | `your_sandbox_client_id` | OAuth2 client ID |

### Collection Structure

```
NubBank API v2
├── 🔐 Authentication
│   ├── Get Access Token (PKCE)
│   └── Refresh Token
├── 🏦 Open Banking v3.1
│   ├── Account Access Consents
│   ├── Accounts (AISP)
│   ├── Balances
│   ├── Transactions
│   ├── Domestic Payment Consents
│   ├── Domestic Payments (PISP)
│   └── Funds Confirmations (CBPII)
├── 💳 Card API
│   ├── API Key Management
│   ├── Card Issuance
│   ├── Card Controls
│   ├── Authorization History
│   ├── Spending Analytics
│   ├── Webhook Management
│   └── Disputes
├── ⚙️ Internal API
│   ├── Customers
│   ├── Accounts
│   ├── Loans
│   ├── Payments
│   ├── Products
│   ├── Accounting
│   ├── Reports
│   └── Administration
└── 🧪 Terminal Simulator
    ├── Simulate Purchase
    ├── Simulate Withdrawal
    └── Simulate Balance Enquiry
```

---

## OpenAPI Specification

Download the machine-readable OpenAPI 3.1 spec:

| Service | Download |
|---------|----------|
| Backend (Internal + Open Banking API) | [openapi.yaml](https://sandbox.nubbank.com/api-docs.yaml) |
| Card Service | [card-openapi.yaml](https://sandbox.nubbank.com/card/v3/api-docs.yaml) |

### Generate a Client SDK

Use [OpenAPI Generator](https://openapi-generator.tech/) to generate a typed client in your language:

```bash
# Install
npm install @openapitools/openapi-generator-cli -g

# Generate TypeScript client
openapi-generator-cli generate \
  -i https://sandbox.nubbank.com/api-docs.yaml \
  -g typescript-fetch \
  -o ./src/generated/nubbank-client

# Generate Python client
openapi-generator-cli generate \
  -i https://sandbox.nubbank.com/api-docs.yaml \
  -g python \
  -o ./nubbank_client

# Generate Java client
openapi-generator-cli generate \
  -i https://sandbox.nubbank.com/api-docs.yaml \
  -g java \
  --library okhttp-gson \
  -o ./nubbank-java-client
```

---

## Interactive API Explorer

Browse and test all endpoints directly in your browser:

- **Backend API:** [sandbox.nubbank.com/swagger-ui.html](https://sandbox.nubbank.com/swagger-ui.html)
- **Card API:** [sandbox.nubbank.com/card/swagger-ui.html](https://sandbox.nubbank.com/card/swagger-ui.html)

The Swagger UI requires a valid access token — use the "Authorize" button and paste your Bearer token.

---

## cURL Examples

Every endpoint in the Postman collection includes cURL examples. You can also find them in the API reference pages:

```bash
# List accounts
curl https://sandbox.nubbank.com/open-banking/v3.1/accounts \
  -H "Authorization: Bearer TOKEN" \
  -H "x-fapi-interaction-id: $(uuidgen)"

# Issue a card
curl -X POST https://sandbox.nubbank.com/card-api/v1/cards \
  -H "Authorization: ApiKey sk_test_your_key" \
  -H "Content-Type: application/json" \
  -d '{"customerId": "...", "accountId": "...", "productId": "...", "cardType": "VIRTUAL"}'
```

---

## Language Support

The Postman collection includes code samples in:

| Language | Framework/Library |
|----------|------------------|
| cURL | — |
| JavaScript | `fetch` |
| Python | `requests` |
| Java | OkHttp |
| Go | `net/http` |
| Ruby | `net/http` |
| C# | `HttpClient` |

---

## Sandbox Test Data

The sandbox is pre-seeded with demo data for testing:

| Resource | Details |
|---------|---------|
| Customer | `cust_demo_001` — Jane Smith, KYC ACTIVE |
| Savings account | `acct_demo_savings` — GBP, balance £5,000 |
| Checking account | `acct_demo_checking` — GBP, balance £12,500 |
| Active loan | `loan_demo_001` — £25,000 personal loan |
| Debit card | `card_demo_debit` — linked to savings account |
| Credit card | `card_demo_credit` — £5,000 credit limit |

---

## Support

- **Documentation issues:** Open an issue on [GitHub](https://github.com/RazorMVP/cba-platform/issues)
- **API support:** [api-support@nubbank.com](mailto:api-support@nubbank.com)
- **Partner Portal:** [partners.nubbank.com](https://partners.nubbank.com)
