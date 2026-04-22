---
id: internal
title: Internal API
sidebar_position: 3
description: Full /api/v1/ reference for internal bank staff applications — customers, accounts, loans, payments, and more.
---

# Internal API

The Internal API provides full access to NubBank's core banking operations. It is intended for **bank staff applications** (backoffice portals, teller systems, admin tools).

**Base URL:** `https://api.nubbank.com/api/v1/`  
**Auth:** JWT Bearer via Keycloak (`Authorization: Bearer TOKEN`)  
**Required roles:** `ADMIN`, `TELLER` (specific endpoints vary)

---

## Customers

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/customers` | ADMIN/TELLER | Create customer |
| `GET` | `/customers` | ADMIN/TELLER | List customers (paginated) |
| `GET` | `/customers/{id}` | ADMIN/TELLER | Get customer detail |
| `PUT` | `/customers/{id}` | ADMIN/TELLER | Update customer profile |
| `DELETE` | `/customers/{id}` | ADMIN | Delete pending customer |
| `POST` | `/customers/{id}?command=activate` | ADMIN/TELLER | Activate KYC |
| `POST` | `/customers/{id}?command=reject` | ADMIN | Reject customer |
| `POST` | `/customers/{id}?command=withdraw` | ADMIN | Withdraw application |
| `POST` | `/customers/{id}?command=reactivate` | ADMIN | Reactivate suspended customer |
| `POST` | `/customers/{id}?command=assignStaff` | ADMIN | Assign loan officer |
| `POST` | `/customers/{id}?command=proposeTransfer` | ADMIN | Propose inter-branch transfer |
| `GET` | `/clients/{id}/identifiers` | ADMIN/TELLER | List customer IDs |
| `GET` | `/clients/{id}/addresses` | ADMIN/TELLER | List addresses |
| `GET` | `/clients/{id}/images` | ADMIN/TELLER | Profile image metadata |
| `PUT` | `/clients/{id}/images` | ADMIN/TELLER | Upload profile image (multipart) |

---

## Accounts

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/accounts` | ADMIN/TELLER | Open account |
| `GET` | `/accounts` | ADMIN/TELLER | List accounts |
| `GET` | `/accounts/{id}` | ADMIN/TELLER | Account detail |
| `POST` | `/accounts/{id}?command=approve` | ADMIN | Approve account |
| `POST` | `/accounts/{id}?command=activate` | ADMIN/TELLER | Activate account |
| `POST` | `/accounts/{id}?command=freeze` | ADMIN/TELLER | Freeze account |
| `POST` | `/accounts/{id}?command=unfreeze` | ADMIN/TELLER | Unfreeze account |
| `POST` | `/accounts/{id}?command=close` | ADMIN | Close account |
| `GET` | `/accounts/{id}/transactions` | ADMIN/TELLER | Transaction history |
| `GET` | `/accounts/{id}/interest/calculate` | ADMIN/TELLER | Preview accrued interest |
| `POST` | `/accounts/{id}?command=postInterest` | ADMIN/TELLER | Post accrued interest |

---

## Loans

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/loans` | ADMIN/TELLER | Create loan application |
| `GET` | `/loans` | ADMIN/TELLER | List loans |
| `GET` | `/loans/{id}` | ADMIN/TELLER | Loan detail |
| `POST` | `/loans/{id}?command=approve` | ADMIN | Approve loan |
| `POST` | `/loans/{id}?command=disburse` | ADMIN | Disburse loan |
| `POST` | `/loans/{id}?command=reject` | ADMIN | Reject loan |
| `POST` | `/loans/{id}/repayments` | ADMIN/TELLER | Record repayment |
| `POST` | `/loans/{id}/write-off` | ADMIN | Write off loan |
| `POST` | `/loans/{id}/undo-write-off` | ADMIN | Undo write-off |
| `POST` | `/loans/{id}/waive-interest` | ADMIN | Waive outstanding interest |
| `POST` | `/loans/{id}/foreclose` | ADMIN | Foreclose loan |
| `GET` | `/loans/{id}/schedule` | ADMIN/TELLER | Repayment schedule |
| `GET` | `/loans/{id}/charges` | ADMIN/TELLER | Applied charges |
| `POST` | `/loans/{id}/charges` | ADMIN/TELLER | Apply charge |
| `POST` | `/loans/{id}/charges/{cId}?command=pay` | ADMIN/TELLER | Pay charge |
| `GET` | `/loans/{id}/guarantors` | ADMIN/TELLER | List guarantors |
| `POST` | `/loans/{id}/guarantors` | ADMIN/TELLER | Add guarantor |
| `GET` | `/loans/{id}/collaterals` | ADMIN/TELLER | List collateral |

---

## Payments

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/payments/transfer` | ADMIN/TELLER | Internal account transfer |
| `GET` | `/payments` | ADMIN/TELLER | Payment history (paginated) |
| `GET` | `/payments/{id}` | ADMIN/TELLER | Payment detail |
| `POST` | `/payments/{id}/reverse` | ADMIN | Reverse a payment |
| `POST` | `/payments/external` | ADMIN | External payment (SWIFT/SEPA) |
| `GET` | `/payments/external` | ADMIN | External payment history |

---

## Products

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/loan-products` | All | List loan products |
| `POST` | `/loan-products` | ADMIN | Create loan product |
| `PUT` | `/loan-products/{id}` | ADMIN | Update loan product |
| `GET` | `/deposit-products` | All | List deposit products |
| `POST` | `/deposit-products` | ADMIN | Create deposit product |
| `GET` | `/charges` | All | List charge definitions |
| `POST` | `/charges` | ADMIN | Create charge definition |

---

## Accounting

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/glaccounts` | ADMIN | Chart of accounts |
| `POST` | `/glaccounts` | ADMIN | Create GL account |
| `GET` | `/journalentries` | ADMIN | Journal entries |
| `POST` | `/journalentries` | ADMIN | Post manual journal |
| `POST` | `/journalentries/{id}/reverse` | ADMIN | Reverse journal entry |
| `GET` | `/glclosures` | ADMIN | GL closures |
| `POST` | `/glclosures` | ADMIN | Create GL closure |
| `GET` | `/financialactivityaccounts` | ADMIN | Activity account mappings |

---

## Reports

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET` | `/reports` | ADMIN | List available reports |
| `GET` | `/runreports/{name}` | ADMIN/TELLER | Run report with parameters |
| `GET` | `/runreports/{name}/export` | ADMIN | Export report (`?format=csv\|xlsx\|pdf`) |

**Seeded reports:** `ActiveLoans`, `LoansInArrears`, `SavingsBalance`, `TellerCashPosition`, `CustomerAcquisition`, `TrialBalance`, `LoanProductSummary`

---

## Tellers

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `POST` | `/tellers` | ADMIN | Create teller |
| `POST` | `/tellers/{id}/activate` | ADMIN | Activate teller |
| `POST` | `/tellers/{id}/cashiers` | ADMIN | Assign cashier |
| `POST` | `/tellers/{id}/sessions` | TELLER | Open cash session |
| `POST` | `/tellers/{id}/sessions/{sId}/transactions` | TELLER | Cash-in / cash-out |
| `POST` | `/tellers/{id}/sessions/{sId}/settle` | TELLER | Settle and close session |

---

## Administration

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| `GET/POST` | `/users` | ADMIN | Platform user management |
| `GET/POST` | `/roles` | ADMIN | Role management |
| `GET/PUT` | `/roles/{id}/permissions` | ADMIN | Permissions matrix |
| `GET/POST` | `/offices` | ADMIN | Office hierarchy |
| `GET/POST` | `/staff` | ADMIN | Staff management |
| `GET/POST` | `/codes` | ADMIN | System code tables |
| `GET/PUT` | `/configurations` | ADMIN | Global configuration |
| `GET/POST` | `/hooks` | ADMIN | Webhook hooks |
| `GET/POST` | `/makercheckers` | ADMIN | Maker-checker approvals |

---

## Pagination

All list endpoints accept:

```
?page=0&size=20&sort=createdAt,desc
```

All responses use the standard envelope:

```json
{
  "data": [...],
  "meta": { "page": 0, "size": 20, "total": 150 },
  "errors": []
}
```

---

## Dev Auth Bypass (Sandbox Only)

In the sandbox environment without a Keycloak instance, pass the bypass header:

```bash
curl https://sandbox.nubbank.com/api/v1/customers \
  -H "X-Auth-Bypass: true"
```

This is disabled in production.
