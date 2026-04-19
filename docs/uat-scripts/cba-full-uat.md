# UAT Test Plan: Core Banking Application (CBA) — Full Platform

**Version:** v1.0  
**Date:** 2026-04-19  
**Prepared by:** Claude (UAT Script Generator)  
**Environment:** Staging / UAT (`http://localhost:4200` + `http://localhost:8080`)  
**Tester:** QA Engineer / Business Stakeholder  
**Auth bypass:** `app.auth-bypass: true` (dev profile) — all requests authenticate as ADMIN+TELLER+CUSTOMER

---

## Scope

**In scope:** All Angular UI screens and backend REST endpoints for all 11 PRD modules:
Customer Onboarding, Account Management, Loan Management, Fees & Charges, Payments, GL Accounting, Treasury, Teller/Cash, Products, Reports & BI, Audit & Internal Control, System Administration, Fraud & Risk, Open Banking, Card Management, Groups & Centers, Notifications & Messaging, CoB Scheduler.

**Out of scope:**
- Flutter mobile app (Phase 3 — not yet built)
- Kubernetes production deployment
- Real Keycloak OAuth2 flows (auth bypass active in dev)
- Real card scheme connectivity (Visa/MC/Verve live networks)
- Real SWIFT/SEPA wire transmission (stubs only)

---

## Assumptions & Gaps

| # | Assumption / Gap |
|---|-----------------|
| A1 | Backend running on `localhost:8080`; Angular on `localhost:4200`; auth bypass enabled. |
| A2 | Flyway migrations V1–V46 applied; demo data from V2/V4 seeded. |
| A3 | Demo accounts exist: admin@cba.com (ADMIN), teller@cba.com (TELLER), customer@cba.com (CUSTOMER). |
| A4 | card-service running on `localhost:8081`; fep-service on `localhost:8082` / TCP 8583 if card scenarios tested. |
| A5 | Any test that creates a customer or account should use unique identifiers (append timestamp to email). |
| A6 | Currency is USD unless otherwise specified. |
| A7 | All monetary amounts in UI are display-formatted; API uses raw NUMERIC(19,4). |
| A8 | Dashboard KPI data requires at least the demo loan/account/customer seed data to show non-zero values. |
| A9 | Open Banking 3DS challenge page returns OTP in debug log (`DEBUG com.cba.card: 3DS OTP`). |
| A10 | UAT testers should clear browser cache before each major scenario group. |

---

## Test Data Requirements

| Resource | Requirement |
|----------|-------------|
| Demo customers | At least 3: ACTIVE, PENDING_KYC, SUSPENDED — from V2 seed |
| Demo accounts | At least 2 ACTIVE savings accounts linked to different customers |
| Demo loan | At least 1 ACTIVE loan with repayment schedule (disbursed) |
| Demo loan product | At least 1 active loan product with GL linkages |
| Demo deposit product | At least 1 active savings product |
| GL accounts | ASSET, LIABILITY, INCOME, EXPENSE types seeded |
| Exchange rates | USD↔KES, USD↔GHS seeded (V4 migration) |
| Card | At least 1 ACTIVE demo card linked to a savings account |
| Teller | At least 1 teller with an ACTIVE cashier |
| Report | "ActiveLoans" seed report available |
| BIN ranges | Visa (400000–499999), Mastercard (500000–559999) seeded |

---

## Scenario Groups

---

### Scenario 1: Customer Onboarding

*Covers: Module 1 — Customer Onboarding PRD*

#### 1.1 Happy Path — Create and Activate Customer

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-1.1.1 | Create customer via UI | Backend running; Customers list page open | 1. Click **New Customer** button. 2. Fill: First Name=`John`, Last Name=`Doe`, Email=`john.doe.{ts}@test.com`, Phone=`+1555000001`, DOB=`1990-01-15`, National ID=`A123456`. 3. Click **Save**. | Customer created; status `PENDING_KYC`; redirected to detail page; customer ID visible. | ☐ | |
| TC-1.1.2 | Activate customer (KYC) | Customer in PENDING_KYC state | 1. On customer detail. 2. In KYC Status dropdown select **ACTIVE**. 3. Confirm. | Status changes to `ACTIVE`; green badge shown. | ☐ | |
| TC-1.1.3 | Edit customer profile | Customer in ACTIVE state | 1. Click **Edit** on Overview tab. 2. Change phone to `+1555000002`. 3. Click **Save**. | Phone updated; success toast; no status change. | ☐ | |
| TC-1.1.4 | Upload customer photo | Customer in ACTIVE state | 1. On Overview tab, click photo upload. 2. Select a JPEG < 5 MB. 3. Save. | Photo displayed; API `PUT /api/v1/clients/{id}/images` returns 200; image resized to max 500×500. | ☐ | |

#### 1.2 Lifecycle Commands

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-1.2.1 | Suspend customer | Customer ACTIVE | 1. KYC dropdown → **SUSPENDED**. 2. Confirm. | Status `SUSPENDED`; amber badge. | ☐ | |
| TC-1.2.2 | Reject customer | Customer PENDING_KYC | 1. Command modal → **Reject**. 2. Enter reason. | Status `REJECTED`. | ☐ | |
| TC-1.2.3 | Undo rejection | Customer REJECTED | 1. Command → **Undo Rejection**. | Status back to `PENDING_KYC`. | ☐ | |
| TC-1.2.4 | Withdraw customer | Customer PENDING_KYC | 1. Command → **Withdraw**. 2. Enter reason. | Status `WITHDRAWN`. | ☐ | |
| TC-1.2.5 | Undo withdrawal | Customer WITHDRAWN | 1. Command → **Undo Withdrawal**. | Status back to `PENDING_KYC`. | ☐ | |
| TC-1.2.6 | Assign staff | Customer ACTIVE; at least 1 staff exists | 1. Staff tab. 2. **Assign Staff** → pick staff. | Staff name appears in tab. | ☐ | |
| TC-1.2.7 | Propose inter-branch transfer | Customer ACTIVE; 2+ offices exist | 1. Transfer tab. 2. **Propose Transfer** → pick destination office. | Status `TRANSFER_IN_PROGRESS`; transfer card shows target office. | ☐ | |
| TC-1.2.8 | Accept transfer | Transfer in TRANSFER_IN_PROGRESS | 1. Transfer tab → **Accept Transfer**. | Status ACTIVE; office updated to destination. | ☐ | |
| TC-1.2.9 | Close customer | Customer ACTIVE; no open accounts/loans | 1. Command → **Close**. | Status `CLOSED`. | ☐ | |
| TC-1.2.10 | Delete pending customer | Customer PENDING_KYC | 1. Command → **Delete**. 2. Confirm. | Customer removed from list; `DELETE /customers/{id}` returns 200. | ☐ | |

#### 1.3 Negative / Error Cases

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-1.3.1 | Duplicate email | Existing customer email in DB | 1. Create customer with same email as existing. | Error toast: "Email already in use" or 409 Conflict. | ☐ | |
| TC-1.3.2 | Missing required fields | Create customer form open | 1. Submit form with blank First Name. | Validation error on required field; form not submitted. | ☐ | |
| TC-1.3.3 | Upload non-image file | Customer detail, photo section | 1. Attempt to upload a `.pdf` file as photo. | Error: "Only JPEG/PNG accepted". | ☐ | |
| TC-1.3.4 | Upload oversized image | Customer detail, photo section | 1. Attempt to upload image > 5 MB. | Error: "File exceeds 5 MB limit". | ☐ | |

#### 1.4 API-Level Test Cases

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-1.4.1 | Create customer API | POST | `/api/v1/customers` | `{"firstName":"Test","lastName":"User","email":"api.test@test.com","phoneNumber":"+1555999001","dateOfBirth":"1985-06-15","nationalId":"Z999001"}` | 201; body contains `id`, `status: "PENDING_KYC"` | ☐ |
| TC-1.4.2 | Activate customer API | PUT | `/api/v1/customers/{id}/kyc-status` | `{"kycStatus":"ACTIVE"}` | 200; `status: "ACTIVE"` | ☐ |
| TC-1.4.3 | Get customer API | GET | `/api/v1/customers/{id}` | — | 200; PII fields present (not null); `kycStatus` present | ☐ |
| TC-1.4.4 | Customer not found | GET | `/api/v1/customers/00000000-0000-0000-0000-000000000000` | — | 404; `errors[0].code: "CUSTOMER_NOT_FOUND"` | ☐ |
| TC-1.4.5 | Command: reject | POST | `/api/v1/customers/{id}?command=reject` | `{"reason":"Incomplete documents"}` | 200; `status: "REJECTED"` | ☐ |

---

### Scenario 2: Account Management (Savings)

*Covers: Module 2 — Customer Account Management PRD*

#### 2.1 Happy Path — Account Lifecycle

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-2.1.1 | Open savings account | ACTIVE customer; deposit product exists | 1. Accounts list → **New Account**. 2. Select customer, product. 3. Submit. | Account created; status `SUBMITTED`. | ☐ | |
| TC-2.1.2 | Approve account | Account SUBMITTED | 1. Account detail → **Approve** button. | Status `APPROVED`. | ☐ | |
| TC-2.1.3 | Activate account | Account APPROVED | 1. **Activate** button. | Status `ACTIVE`; balance = 0.00. | ☐ | |
| TC-2.1.4 | Deposit funds (teller) | Account ACTIVE | 1. **Deposit** modal → amount `1000.00`. 2. Confirm. | Balance increases by 1000.00; transaction record created. | ☐ | |
| TC-2.1.5 | Withdraw funds (teller) | Account ACTIVE; balance ≥ 200 | 1. **Withdraw** modal → amount `200.00`. 2. Confirm. | Balance decreases by 200.00; transaction record created. | ☐ | |
| TC-2.1.6 | Freeze account | Account ACTIVE | 1. **Freeze** button → confirm. | Status `FROZEN`; deposits/withdrawals blocked. | ☐ | |
| TC-2.1.7 | Unfreeze account | Account FROZEN | 1. **Unfreeze** button → confirm. | Status `ACTIVE`. | ☐ | |
| TC-2.1.8 | Close account | Account ACTIVE; balance = 0 | 1. Withdraw all funds. 2. **Close** button → confirm. | Status `CLOSED`; account read-only. | ☐ | |

#### 2.2 Advanced Features

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-2.2.1 | Place hold on funds | Account ACTIVE; balance ≥ 500 | 1. Holds tab → **Place Hold**. 2. Amount `300.00`, reason `Pending Cheque`. | Hold record created; available balance = balance − 300; total balance unchanged. | ☐ | |
| TC-2.2.2 | Release hold | Account ACTIVE; active hold exists | 1. Holds tab → **Release** on the hold. | Hold status RELEASED; available balance restored. | ☐ | |
| TC-2.2.3 | Post interest (manual) | Account ACTIVE; deposit product has interest rate > 0 | 1. Interest tab → **Calculate** → preview modal shows estimate. 2. **Post Interest**. | Transaction `INTEREST_CREDIT` created; balance increases. | ☐ | |
| TC-2.2.4 | Download statement | Account ACTIVE; ≥ 5 transactions | 1. **Statement** button → set date range. 2. Download. | PDF/CSV download initiated with transaction rows in range. | ☐ | |
| TC-2.2.5 | Overdraft indicator | Account with overdraft-enabled product | 1. View account detail header. | Overdraft limit shown in header (blue indicator). | ☐ | |
| TC-2.2.6 | Reactivate dormant account | Account DORMANT | 1. **Reactivate** button → confirm. | Status `ACTIVE`. | ☐ | |

#### 2.3 Negative Cases

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-2.3.1 | Overdraw below minimum balance | Account with min_balance = 100; balance = 100 | 1. Withdraw modal → amount `50.00`. | Error: "Insufficient balance" or minimum balance violation message. | ☐ | |
| TC-2.3.2 | Deposit to CLOSED account | Account CLOSED | 1. Attempt `POST /api/v1/accounts/{id}/deposit`. | 400; "Account is not active". | ☐ | |
| TC-2.3.3 | Hold exceeds available balance | Account balance = 100; try hold of 200 | 1. Place Hold → amount `200.00`. | Error: hold amount exceeds available balance. | ☐ | |
| TC-2.3.4 | Close account with positive balance | Account balance > 0 | 1. Close button → confirm. | Error: "Balance must be zero before closing". | ☐ | |
| TC-2.3.5 | Lock-in period withdrawal | Account activated within lock-in period | 1. Attempt withdrawal within lock-in window. | Error: "Account is within lock-in period until [date]". | ☐ | |

#### 2.4 API-Level Test Cases

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-2.4.1 | Open account | POST | `/api/v1/accounts` | `{"customerId":"{id}","productId":"{id}","currencyCode":"USD"}` | 201; `status:"SUBMITTED"` | ☐ |
| TC-2.4.2 | Approve account | POST | `/api/v1/accounts/{id}?command=approve` | `{}` | 200; `status:"APPROVED"` | ☐ |
| TC-2.4.3 | Activate account | POST | `/api/v1/accounts/{id}?command=activate` | `{}` | 200; `status:"ACTIVE"` | ☐ |
| TC-2.4.4 | Get transactions paginated | GET | `/api/v1/accounts/{id}/transactions?page=0&size=10` | — | 200; `data.content` array; `data.totalElements` ≥ 0 | ☐ |
| TC-2.4.5 | Calculate interest preview | GET | `/api/v1/accounts/{id}/interest/calculate` | — | 200; `projectedInterest` numeric field | ☐ |

---

### Scenario 3: Loan Management

*Covers: Module 3 — Loan Management PRD*

#### 3.1 Happy Path — Full Loan Lifecycle

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-3.1.1 | Create loan application | ACTIVE customer; loan product exists | 1. Loans list → **New Loan**. 2. Select customer, product, amount `5000.00`, 12 months. 3. Submit. | Loan created; status `SUBMITTED`. | ☐ | |
| TC-3.1.2 | Approve loan | Loan SUBMITTED | 1. Loan detail → **Approve**. 2. Set approval date. | Status `APPROVED`. | ☐ | |
| TC-3.1.3 | Disburse loan | Loan APPROVED; ACTIVE account linked | 1. **Disburse** → pick disbursement account. 2. Confirm. | Status `ACTIVE`; account balance increases by principal. | ☐ | |
| TC-3.1.4 | View repayment schedule | Loan ACTIVE (disbursed) | 1. Click **Schedule** tab. | Installments listed with due dates, principal, interest, total columns. | ☐ | |
| TC-3.1.5 | Record repayment | Loan ACTIVE | 1. **Record Repayment** → amount = one installment amount. 2. Confirm. | Outstanding balance decreases; installment marked PAID. | ☐ | |
| TC-3.1.6 | Full repayment → close | Loan ACTIVE; all installments repaid | 1. Final repayment covering all outstanding. | Status `CLOSED_OBLIGATIONS_MET`; balance = 0. | ☐ | |

#### 3.2 Advanced Loan Operations

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-3.2.1 | Reject loan | Loan SUBMITTED | 1. **Reject** button → reason. | Status `REJECTED`. | ☐ | |
| TC-3.2.2 | Write off loan | Loan ACTIVE; in arrears | 1. **Write Off** → reason. 2. Confirm. | Status `WRITTEN_OFF`. | ☐ | |
| TC-3.2.3 | Undo write-off | Loan WRITTEN_OFF | 1. **Undo Write-Off**. | Status `ACTIVE`. | ☐ | |
| TC-3.2.4 | Waive interest | Loan ACTIVE | 1. **Waive Interest** → amount. | Interest waived; amount deducted from outstanding interest. | ☐ | |
| TC-3.2.5 | Foreclose loan | Loan ACTIVE | 1. **Foreclose** → effective date. | Status `CLOSED_OBLIGATIONS_MET`; GL entries posted. | ☐ | |
| TC-3.2.6 | Add loan charge | Loan ACTIVE | 1. Charges tab → **Add Charge** → pick charge def. | Charge record created; outstanding amount shown. | ☐ | |
| TC-3.2.7 | Pay loan charge | Charge outstanding > 0 | 1. **Pay** on charge row → amount. | `amountPaid` increases; `outstanding` decreases. | ☐ | |
| TC-3.2.8 | Waive loan charge | Charge outstanding > 0 | 1. **Waive** → confirm. | Charge marked waived; amount zeroed out. | ☐ | |
| TC-3.2.9 | Add guarantor (existing customer) | Loan ACTIVE | 1. Guarantors tab → **Add** → type EXISTING_CUSTOMER → pick. | Guarantor record linked. | ☐ | |
| TC-3.2.10 | Add collateral | Loan ACTIVE | 1. Collateral section → **Add** → type, value `10000.00`, description. | Collateral record created. | ☐ | |
| TC-3.2.11 | Add loan document | Loan ACTIVE | 1. Documents tab → **Upload** → select file → description. | Document record created; file metadata visible. | ☐ | |
| TC-3.2.12 | Add loan note | Loan ACTIVE | 1. Notes tab → **Add Note** → text. | Note record created; timestamp and author shown. | ☐ | |
| TC-3.2.13 | Reschedule loan | Loan ACTIVE | 1. Reschedule tab → **Request** → new rate `8%`, extra terms `2`. 2. Submit. | Reschedule request PENDING; approve it → schedule regenerates. | ☐ | |
| TC-3.2.14 | In-arrears indicator | Loan with overdue installment | 1. View loan detail. | Arrears banner visible; overdue days + amount shown. | ☐ | |

#### 3.3 Negative Cases

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-3.3.1 | Disburse without approval | Loan SUBMITTED | 1. Attempt `POST /api/v1/loans/{id}?command=disburse`. | 400; "Loan must be approved before disbursement". | ☐ | |
| TC-3.3.2 | Repayment exceeds outstanding | Loan ACTIVE; outstanding = 500 | 1. Repayment amount `9999.00`. | Error: overpayment rejected or capped at outstanding. | ☐ | |
| TC-3.3.3 | Zero-amount disbursement | Loan APPROVED | 1. Disburse with amount `0.00`. | 400 validation error. | ☐ | |
| TC-3.3.4 | Loan amount below product minimum | Product min principal = 1000 | 1. Create loan with amount `100.00`. | 400; "Amount below minimum". | ☐ | |

#### 3.4 API-Level Test Cases

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-3.4.1 | Create loan | POST | `/api/v1/loans` | `{"customerId":"{id}","productId":"{id}","principalAmount":5000,"loanTermFrequency":12,"loanTermFrequencyType":"MONTHS"}` | 201; `status:"SUBMITTED"` | ☐ |
| TC-3.4.2 | Approve loan | POST | `/api/v1/loans/{id}?command=approve` | `{"approvedOnDate":"2026-04-19"}` | 200; `status:"APPROVED"` | ☐ |
| TC-3.4.3 | Get repayment schedule | GET | `/api/v1/loans/{id}/repaymentschedule` | — | 200; `installments` array ≥ 1 | ☐ |
| TC-3.4.4 | Waive interest | POST | `/api/v1/loans/{id}/waive-interest` | `{"amount":50.00,"note":"Goodwill waiver"}` | 200; outstanding interest reduced | ☐ |
| TC-3.4.5 | Loan not found | GET | `/api/v1/loans/00000000-0000-0000-0000-000000000000` | — | 404 | ☐ |

---

### Scenario 4: Fees & Charges

*Covers: Module 4 — Fees & Charges PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-4.1 | Create charge definition | Products → Charges page | 1. **New Charge** → name, FLAT type, amount `25.00`, applies to LOAN. 2. Save. | Charge definition created; visible in list. | ☐ | |
| TC-4.2 | Edit charge definition | Charge exists | 1. Edit → change amount to `30.00`. 2. Save. | Amount updated. | ☐ | |
| TC-4.3 | Deactivate charge | Charge active | 1. Toggle active → confirm. | Charge inactive; not available for selection on loans. | ☐ | |
| TC-4.4 | Apply PERCENT charge to loan | Loan ACTIVE; PERCENT_OF_AMOUNT charge exists | 1. Add charge to loan. 2. Amount auto-calculated as % of principal. | Charge appears on Charges tab; outstanding = % × principal. | ☐ | |
| TC-4.5 | Charge appears on repayment schedule | Loan with INSTALLMENT_FEE charge | 1. View Schedule tab. | Installment fee column shows per-installment charge. | ☐ | |

#### 4.1 API-Level Test Cases

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-4.1A | List charges | GET | `/api/v1/charges?page=0&size=20` | — | 200; paginated list | ☐ |
| TC-4.1B | Pay charge | POST | `/api/v1/loans/{loanId}/charges/{chargeId}?command=pay` | `{"amount":25.00}` | 200; `amountPaid` updated | ☐ |
| TC-4.1C | Waive charge | POST | `/api/v1/loans/{loanId}/charges/{chargeId}?command=waive` | `{}` | 200; `waived:true` | ☐ |

---

### Scenario 5: Payments

*Covers: Module 4 — Payment Module PRD*

#### 5.1 Internal Transfers

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-5.1.1 | Internal transfer (same currency) | Two ACTIVE accounts with balances | 1. Payments → **New Transfer**. 2. Pick source, destination accounts. 3. Amount `200.00`. 4. Submit. | Payment record COMPLETED; source debited, destination credited; double-entry ledger entries created. | ☐ | |
| TC-5.1.2 | Cross-currency transfer | USD account → KES account; exchange rate seeded | 1. Same steps; source USD, destination KES. | `isCrossCurrency: true`; `exchangeRateUsed` shown; converted amount credited. | ☐ | |
| TC-5.1.3 | Reverse a payment | Payment COMPLETED | 1. Payment detail → **Reverse** → reason. | Payment status `REVERSED`; accounts re-credited/debited. | ☐ | |

#### 5.2 Standing Orders

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-5.2.1 | Create standing order | Two ACTIVE accounts | 1. Standing order modal → source, destination, amount `100.00`, MONTHLY, start today. | Standing order ACTIVE; `nextExecutionDate` = today+1 month. | ☐ | |
| TC-5.2.2 | Cancel standing order | Standing order ACTIVE | 1. Cancel button → confirm. | Status `CANCELLED`. | ☐ | |

#### 5.3 External Payments (SWIFT/SEPA)

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-5.3.1 | Initiate SWIFT external payment | ACTIVE source account | 1. "Send Abroad" modal → network SWIFT, beneficiary name, IBAN, BIC, amount. 2. Submit. | Payment record PENDING/PROCESSING; `paymentType: EXTERNAL_PAYMENT`. | ☐ | |
| TC-5.3.2 | Initiate SEPA external payment | ACTIVE source account | 1. Same flow with SEPA network. | Same; `network: SEPA` in record. | ☐ | |
| TC-5.3.3 | Transfer to self blocked | Same account for source and destination | 1. Pick same account for source and destination. | Error: "Source and destination cannot be the same". | ☐ | |

#### 5.4 Negative Cases

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-5.4.1 | Transfer exceeds balance | Source balance = 100; transfer 500 | 1. Submit transfer amount `500.00`. | 400; "Insufficient balance". | ☐ | |
| TC-5.4.2 | Transfer from FROZEN account | Source FROZEN | 1. Attempt transfer. | 400; "Account is not active". | ☐ | |
| TC-5.4.3 | Negative amount | Transfer form | 1. Enter amount `-100.00`. | Validation error on form; negative not accepted. | ☐ | |

#### 5.5 API-Level Test Cases

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-5.5.1 | Internal transfer | POST | `/api/v1/payments/transfer` | `{"sourceAccountId":"{id}","destinationAccountId":"{id}","amount":100.00,"description":"Test"}` | 200; `status:"COMPLETED"` | ☐ |
| TC-5.5.2 | Reverse payment | POST | `/api/v1/payments/{id}/reverse` | `{"reason":"Duplicate"}` | 200; `status:"REVERSED"` | ☐ |
| TC-5.5.3 | Get payment history | GET | `/api/v1/payments/accounts/{accountId}?page=0&size=10` | — | 200; paginated payment list | ☐ |
| TC-5.5.4 | External payment | POST | `/api/v1/payments/external` | `{"sourceAccountId":"{id}","amount":500,"currencyCode":"USD","network":"SWIFT","beneficiaryName":"ACME Corp","beneficiaryIban":"GB29NWBK60161331926819","beneficiaryBic":"NWBKGB2L"}` | 200; `status:"PENDING"` | ☐ |

---

### Scenario 6: GL Accounting

*Covers: Module 5 — GL Accounting PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-6.1 | View chart of accounts | Accounting → GL Accounts | 1. Open GL Accounts page. 2. Filter by type ASSET. | Accounts filtered; type badges shown. | ☐ | |
| TC-6.2 | Create GL account | GL Accounts page open | 1. **New Account** → name, glCode unique, type ASSET. 2. Save. | Account created; visible in list. | ☐ | |
| TC-6.3 | Disable GL account | GL account exists | 1. Toggle disabled → confirm. | Account disabled; cannot be picked in journal entries. | ☐ | |
| TC-6.4 | Post manual journal entry | 1 ASSET + 1 LIABILITY account | 1. Journal Entries → **New Entry**. 2. Add debit `500.00` to ASSET, credit `500.00` to LIABILITY. 3. Submit. | Entry posted; debit = credit; balanced. | ☐ | |
| TC-6.5 | Reject unbalanced journal | Journal entry form | 1. Add debit `500.00` only (no credit). 2. Submit. | Error: "Debits must equal credits". | ☐ | |
| TC-6.6 | Reverse journal entry | Journal entry POSTED | 1. **Reverse** → reason. | Reversal journal created with opposite sign; original entry linked to reversal. | ☐ | |
| TC-6.7 | Create GL closure | Accounting → GL Closures; no open transactions on closing date | 1. **Create Closure** → pick office, date. 2. Submit. | Closure created; journal entries blocked for closed date. | ☐ | |
| TC-6.8 | Provisioning criteria CRUD | Accounting → Provisioning | 1. Create criteria with 5 age bands. 2. View in list. 3. Edit → change STANDARD percentage. 4. Delete criteria. | Full CRUD cycle. | ☐ | |
| TC-6.9 | Financial Activity Account mapping | Accounting → Financial Activity | 1. Create mapping: ASSET_LOAN_PORTFOLIO → pick GL account. | Mapping saved; GL code shown in table. | ☐ | |
| TC-6.10 | Accounting Rule CRUD | Accounting → Accounting Rules | 1. Create rule → debit GL, credit GL, name. | Rule saved. | ☐ | |

---

### Scenario 7: Treasury

*Covers: Module 6 — Treasury PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-7.1 | Create placement | Treasury → Placements | 1. **New Placement** → type TERM_DEPOSIT, amount `50000.00`, rate `5.5%`, 90 days. | Placement created; status PENDING. | ☐ | |
| TC-7.2 | Activate placement | Placement PENDING | 1. **Activate** → confirm. | Status ACTIVE; start date set. | ☐ | |
| TC-7.3 | Mature placement | Placement ACTIVE; past maturity date | 1. **Mature** → confirm. | Status MATURED; interest accrued. | ☐ | |
| TC-7.4 | Cancel placement | Placement PENDING | 1. **Cancel** → reason. | Status CANCELLED. | ☐ | |
| TC-7.5 | Interbank lending position | Treasury → Interbank | 1. **New Position** → LENDING, counterparty, amount `100000.00`, rate `4.5%`. | Position created ACTIVE. | ☐ | |
| TC-7.6 | Settle interbank position | Position ACTIVE | 1. **Settle** → confirm. | Status SETTLED. | ☐ | |
| TC-7.7 | View liquidity position | Treasury → Liquidity → Position tab | 1. Open position tab. | Cash position, total assets, liquidity ratio displayed. | ☐ | |
| TC-7.8 | Cash flow forecast | Liquidity → Cash Flow tab | 1. View 7-day/30-day forecast. | Inflows + outflows projected rows. | ☐ | |
| TC-7.9 | Reserve requirement CRUD | Liquidity → Reserve Requirements | 1. Create requirement → type, amount/percentage. 2. View in list. | Requirement saved; compliance status shown. | ☐ | |
| TC-7.10 | Interbank SWIFT payment | Payments → Send Abroad | 1. Initiate external SWIFT from ACTIVE account. | Payment record created with SWIFT network. | ☐ | (covered in Scenario 5.3) |

---

### Scenario 8: Teller & Cash Management

*Covers: Module 9 — Teller Module PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-8.1 | Create teller | Operations → Teller list | 1. **New Teller** → name, office. 2. Save. | Teller created; status INACTIVE. | ☐ | |
| TC-8.2 | Activate teller | Teller INACTIVE | 1. **Activate** → confirm. | Status ACTIVE. | ☐ | |
| TC-8.3 | Add cashier to teller | Teller ACTIVE | 1. Cashiers tab → **Add Cashier** → pick staff. | Cashier created with teller assignment. | ☐ | |
| TC-8.4 | Open cashier session | Cashier exists | 1. Sessions tab → **Open Session** → opening float `500.00`. | Session OPEN; opening balance = 500.00. | ☐ | |
| TC-8.5 | Cash in transaction | Session OPEN | 1. **Cash In** → amount `200.00`, linked to customer account. | Transaction recorded; session running total updated. | ☐ | |
| TC-8.6 | Cash out transaction | Session OPEN | 1. **Cash Out** → amount `100.00`. | Transaction recorded. | ☐ | |
| TC-8.7 | Settle and close session | Session OPEN | 1. **Settle** → actual cash `600.00`. 2. Confirm. | Session CLOSED; difference = `600 − (500+200−100) = 0`. | ☐ | |
| TC-8.8 | Duplicate session blocked | Session already OPEN for cashier today | 1. Attempt second **Open Session** for same cashier. | Error: "Session already open for today". | ☐ | |

---

### Scenario 9: Products

*Covers: Loan Products, Deposit Products, Fixed Deposits, Recurring Deposits, Share Products*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-9.1 | Create loan product | Products → Loan Products | 1. **New** → name, shortName (4 chars), principal range, interest rate. 2. Link GL accounts. 3. Save. | Product created; visible in list. | ☐ | |
| TC-9.2 | Edit loan product | Product exists | 1. **Edit** → change max interest rate. 2. Save. | Updated; existing loans unaffected. | ☐ | |
| TC-9.3 | Create deposit product | Products → Deposit Products | 1. **New** → name, shortName (4 chars), account type SAVINGS, min balance `100.00`. | Product created. | ☐ | |
| TC-9.4 | Create fixed deposit product | Products → Fixed Deposits | 1. **New** → name, rate `6.5%`, min deposit `5000.00`, min term 90 days. | Product created. | ☐ | |
| TC-9.5 | Create recurring deposit product | Products → Recurring Deposits | 1. **New** → name, monthly frequency, min installment `200.00`. | Product created. | ☐ | |
| TC-9.6 | Create share product | Products → Shares | 1. **New** → name, unit price `10.00`, min shares `10`, max shares `1000`. | Product created. | ☐ | |
| TC-9.7 | Duplicate shortName blocked | Product shortName already taken | 1. Create product with existing shortName. | 409 or validation error: "shortName already in use". | ☐ | |

---

### Scenario 10: Reports & Business Intelligence

*Covers: Module 11 — Business Intelligence, Module 15 — Reports Engine*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-10.1 | View dashboard KPIs | At least seed data loaded | 1. Navigate to Dashboard. | 4 KPI cards: Total Customers, Deposit Balance, Active Loans, KYC Pending — all show non-null values. | ☐ | |
| TC-10.2 | Dashboard loan portfolio chart | Seed loan data | 1. Scroll to loan portfolio section. | 4 aging buckets (Current/1–30d/31–90d/90d+) with percentage bars. | ☐ | |
| TC-10.3 | Dashboard deposit analytics | Seed account data | 1. Scroll to analytics grid. | Deposit type breakdown (savings/checking/fixed) shown with balances. | ☐ | |
| TC-10.4 | Dashboard repayment analytics | Seed active loans | 1. Repayment section. | Collection rate % and overdue amount shown. | ☐ | |
| TC-10.5 | Run ActiveLoans report | Reports → list | 1. Click **ActiveLoans** → **Run**. | Results table populated with active loan rows. | ☐ | |
| TC-10.6 | Run report with params | Report with required params | 1. Click report → fill param form → **Run**. | Param applied to result set (e.g., date filter narrows rows). | ☐ | |
| TC-10.7 | Export CSV | Report with results | 1. **Export** → CSV. | CSV file downloaded with headers and data rows. | ☐ | |
| TC-10.8 | Export XLSX | Report with results | 1. **Export** → XLSX. | Excel file downloaded; rows match CSV. | ☐ | |
| TC-10.9 | Export PDF | Report with results | 1. **Export** → PDF. | PDF file downloaded; report title and table visible. | ☐ | |
| TC-10.10 | CoB job — run now | Reports → CoB Scheduler | 1. Click **Run Now** on `interestAccrualJob`. | Job triggered; history row appears; status COMPLETED/FAILED. | ☐ | |
| TC-10.11 | CoB job history | Any job | 1. Expand history panel on job card. | Last N executions shown with duration and status. | ☐ | |
| TC-10.12 | Report Mailing Job create | Reports → Mailing Jobs | 1. **New** → name, report, RRULE, email recipients, output CSV. 2. Save. | Mailing job created; schedule shown. | ☐ | |
| TC-10.13 | SQL injection blocked | Reports with param | 1. Run report with param value containing `'; DROP TABLE--`. | Error: "Invalid character in parameter" or sanitized result — not a DB error. | ☐ | |

#### 10.1 API-Level Test Cases

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-10.1A | Dashboard KPIs | GET | `/api/v1/dashboard` | — | 200; all 7 KPI fields present and numeric | ☐ |
| TC-10.1B | Loan analytics | GET | `/api/v1/dashboard/analytics/loans` | — | 200; `buckets` array of 4 items | ☐ |
| TC-10.1C | Run report | GET | `/api/v1/runreports/ActiveLoans` | — | 200; `data` array of rows | ☐ |
| TC-10.1D | Export XLSX | GET | `/api/v1/runreports/ActiveLoans/export?format=xlsx` | — | 200; `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | ☐ |

---

### Scenario 11: Audit & Internal Control

*Covers: Module 7 — Audit & Internal Control PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-11.1 | View audit log | Admin → Audit Log | 1. Open page. | Paginated log with entity type, action, changed_by, timestamp. | ☐ | |
| TC-11.2 | Filter audit by entity type | Audit log page | 1. Filter: entity type = LOAN. | Only LOAN audit entries shown. | ☐ | |
| TC-11.3 | Filter audit by date range | Audit log page | 1. Set from/to date. | Entries outside range not shown. | ☐ | |
| TC-11.4 | Audit detail panel | Audit log entry | 1. Click audit row. | Slide-in panel shows old_values + new_values JSON. | ☐ | |
| TC-11.5 | Maker-Checker — submit | Admin → Maker-Checker | 1. Any state-changing action that creates a maker-checker entry. | Entry appears with status PENDING. | ☐ | |
| TC-11.6 | Maker-Checker — approve | Entry PENDING | 1. **Approve** → checker user. | Status APPROVED; action replayed. | ☐ | |
| TC-11.7 | Maker-Checker — reject | Entry PENDING | 1. **Reject** → reason. | Status REJECTED; action NOT replayed. | ☐ | |
| TC-11.8 | Login history | Admin → Login History | 1. Open page. | Paginated list with status filter (SUCCESS/FAILURE/LOCKED). | ☐ | |
| TC-11.9 | Login history KPI cards | Login History page | 1. View summary section. | 4 KPI cards: total successes, failures, lockouts, unique users. | ☐ | |
| TC-11.10 | Compliance report — Audit Summary | Admin → Compliance | 1. Audit Summary tab → 30-day range → **Run**. | Table of actions and counts; **Export CSV** downloads file. | ☐ | |
| TC-11.11 | Compliance report — Failed Logins | Compliance page | 1. Failed Logins tab → **Run**. | Failed login events shown by user/timestamp. | ☐ | |
| TC-11.12 | Audit append-only check | Any audit entry | 1. Attempt `DELETE /api/v1/audits/{id}` via API. | 405 Method Not Allowed or 403 Forbidden. | ☐ | |

---

### Scenario 12: System Administration

*Covers: Module 8 — System Administrator PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-12.1 | Create user | Admin → Users | 1. **New User** → username, email, role TELLER. | User created in DB and Keycloak. | ☐ | |
| TC-12.2 | Disable user | User active | 1. Toggle → **Disable**. | User disabled; cannot log in. | ☐ | |
| TC-12.3 | Create role | Admin → Roles | 1. **New Role** → name, pick permissions. | Role created; permissions count badge shown. | ☐ | |
| TC-12.4 | Edit role permissions | Role exists | 1. **Edit Permissions** → add/remove. 2. Save. | Permissions updated; matrix reflects changes. | ☐ | |
| TC-12.5 | Create office (branch) | Admin → Offices | 1. **New Office** → name, parent office. | Office created; hierarchy column shows path. | ☐ | |
| TC-12.6 | Create staff | Admin → Staff | 1. **New Staff** → name, office, loan officer ✓. | Staff created; loan-officer chip shown. | ☐ | |
| TC-12.7 | Global config edit | System → Global Config | 1. Search key `max-loan-amount`. 2. Click row → inline edit → new value. 3. Save. | Value updated; key not deleted. | ☐ | |
| TC-12.8 | Create code value | System → Codes | 1. Expand a code. 2. **Add Value** → label, description. | Value appears in expanded list. | ☐ | |
| TC-12.9 | Delete system-defined code blocked | System-defined code | 1. Attempt delete on system code. | Delete button disabled or 400: "System-defined code cannot be deleted". | ☐ | |
| TC-12.10 | Bulk import customers | Admin → Bulk Import | 1. Download customer CSV template. 2. Fill in 2 rows. 3. Upload. | Import job created; success count = 2; 0 errors. | ☐ | |
| TC-12.11 | Bulk import with errors | Admin → Bulk Import | 1. Upload CSV with one row missing required email. | Job shows 1 success, 1 failure; error row shows field and message. | ☐ | |
| TC-12.12 | Security policy edit | Admin → Security Policy | 1. Edit → max login attempts `3`. 2. Save. | Policy saved; warning banner shown. | ☐ | |
| TC-12.13 | Account number format CRUD | System → Acct No. Formats | 1. **New Format** → account type LOAN, prefix type ACCOUNT_TYPE. | Format created. | ☐ | |
| TC-12.14 | Payment types — system-defined protection | System → Payment Types | 1. Attempt delete on a system-defined payment type. | Delete blocked; error message shown. | ☐ | |
| TC-12.15 | Holidays CRUD | System → Holidays | 1. **New Holiday** → name, date, repayment rule NEXT_WORKING_DAY. 2. Activate. | Holiday ACTIVE; shown in list. | ☐ | |
| TC-12.16 | DataTable CRUD | System → DataTables | 1. **New DataTable** → name, application table `clients`, add 2 columns. 2. Save. | DataTable created; columns visible in accordion. | ☐ | |
| TC-12.17 | Survey CRUD | System → Surveys | 1. **New Survey** → key, add 2 questions with responses. 2. Save. | Survey created; questions listed in accordion. | ☐ | |
| TC-12.18 | Floating rates CRUD | System → Floating Rates | 1. **New Rate** → add periods. 2. Save. | Rate created; periods shown. | ☐ | |

---

### Scenario 13: Fraud & Risk Management

*Covers: Module 10 — Fraud & Risk PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-13.1 | View fraud rules | Admin → Fraud Rules | 1. Open page. | Rules listed with score legend (0–29 approve, 30–69 step-up, 70–100 decline). | ☐ | |
| TC-13.2 | Enable/disable fraud rule | Any rule | 1. Toggle enabled. | Rule active/inactive immediately (no page reload needed). | ☐ | |
| TC-13.3 | Edit fraud rule weight | Any rule | 1. **Edit** → change weight. 2. Save. | Weight updated; displayed in list. | ☐ | |
| TC-13.4 | View fraud alerts | Admin → Fraud Alerts | 1. Open page. | Paginated list; filter by severity/status. | ☐ | |
| TC-13.5 | Review fraud alert | Alert with OPEN status | 1. Slide-in panel → **Review**. | Status changes to UNDER_REVIEW. | ☐ | |
| TC-13.6 | Close fraud alert | Alert UNDER_REVIEW | 1. **Close** → resolution. | Status CLOSED. | ☐ | |
| TC-13.7 | Create fraud case from alert | Alert open | 1. **Create Case** → fill fields. | New fraud case created; linked to alert. | ☐ | |
| TC-13.8 | View blacklist | Admin → Blacklist | 1. Open page. Filter by entity type CUSTOMER. | Blacklisted customers shown. | ☐ | |
| TC-13.9 | Add to blacklist | Blacklist page | 1. **Add** → type CUSTOMER, value (customer ID), reason. | Entry added; status ACTIVE. | ☐ | |
| TC-13.10 | Blacklist check blocks transfer | Customer blacklisted | 1. Attempt `POST /api/v1/payments/transfer` where source customer is blacklisted. | 400; "Customer is blacklisted". | ☐ | |

---

### Scenario 14: Open Banking & Consents

*Covers: Module 6 — Open Banking FAPI 2.0 PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-14.1 | View consents list | Admin → Consents | 1. Open Open Banking → Consents list. | Consents paginated; type badges (AISP/PISP/CBPII); status badges. | ☐ | |
| TC-14.2 | Filter consents by type | Consents list | 1. Filter tab → PISP. | Only PISP consents shown. | ☐ | |
| TC-14.3 | Consent detail | Consent exists | 1. Click consent row. | Detail: TPP info, scopes, expiry, status badge, Authorise/Revoke buttons. | ☐ | |
| TC-14.4 | Authorise consent | Consent AWAITING_AUTHORISATION | 1. **Authorise** → confirm. | Status `AUTHORISED`. | ☐ | |
| TC-14.5 | Revoke consent | Consent AUTHORISED | 1. **Revoke** → confirm. | Status `REVOKED`. | ☐ | |
| TC-14.6 | TPP Management CRUD | Admin → Open Banking | 1. **Register TPP** → clientId, country, scopes. 2. View. 3. Activate. | TPP registered and ACTIVE. | ☐ | |
| TC-14.7 | AISP account list | Consent AUTHORISED with `accounts_read` scope | API: `GET /open-banking/v3.1/accounts` with consent bearer | 200; accounts array includes both bank and card accounts. | ☐ | |
| TC-14.8 | CBPII funds confirmation | Consent AUTHORISED with `fundsconfirmation` scope; account balance ≥ 100 | API: `POST /open-banking/v3.1/funds-confirmations` body `{"amount":50}` | 200; `fundsAvailable: true` | ☐ | |

---

### Scenario 15: Card Management Platform

*Covers: Card Management Service (card-service) + Angular CardsModule*

#### 15.1 Card Lifecycle

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-15.1.1 | View card list | Cards → Card List | 1. Open page. | Cards shown; search by PAN suffix works. | ☐ | |
| TC-15.1.2 | Issue virtual card via UI | Card product exists; ACTIVE account | 1. **Issue Card** → pick product, customer, account, virtual. | Card issued; status ACTIVE. | ☐ | |
| TC-15.1.3 | Block card | Card ACTIVE | 1. Card detail → **Block** → reason. | Status `BLOCKED`. | ☐ | |
| TC-15.1.4 | Unblock card | Card BLOCKED | 1. **Unblock**. | Status `ACTIVE`. | ☐ | |
| TC-15.1.5 | Cancel card | Card ACTIVE/BLOCKED | 1. **Cancel** → confirm. | Status `CANCELLED`; cannot be unblocked. | ☐ | |
| TC-15.1.6 | View authorization log | Card ACTIVE; transactions exist | 1. Authorizations tab. | Auth entries with STAN, RRN, response code, fraud score shown. | ☐ | |
| TC-15.1.7 | Update card limits | Card ACTIVE | 1. Limits tab → **Edit Limits** → daily purchase `2000`. | Limit updated. | ☐ | |

#### 15.2 Terminal Simulator

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-15.2.1 | Simulate purchase | ACTIVE card with balance | 1. Cards → Terminal Simulator. 2. Pick card, type PURCHASE, amount `100.00`, CHIP mode. 3. **Send**. | Response code `00`; green APPROVED banner. | ☐ | |
| TC-15.2.2 | Simulate declined purchase | Card with daily limit = 1; amount > limit | 1. Amount exceeds limit. | Response code `05`/`51`; red DECLINED banner. | ☐ | |
| TC-15.2.3 | Simulate balance enquiry | ACTIVE card | 1. Type BALANCE. 2. Send. | `availableBalance` returned in response. | ☐ | |
| TC-15.2.4 | Simulate reversal | After a purchase | 1. Type REVERSAL. 2. Same STAN as prior txn. | Response code `00`; balance restored. | ☐ | |
| TC-15.2.5 | View ISO 8583 hex dump | After any simulation | 1. Expand hex dump panel. | Raw hex bytes of request and response shown. | ☐ | |

#### 15.3 Fraud Rules & BIN Management

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-15.3.1 | View fraud rules | Cards → Fraud Rules | 1. Open page. | Score legend + rule table with weights. | ☐ | |
| TC-15.3.2 | Edit fraud rule weight | Any rule | 1. **Edit** → weight `45`. 2. Save. | Updated in list. | ☐ | |
| TC-15.3.3 | View BIN ranges | Cards → BIN Management | 1. Open page. | BIN ranges with scheme colour badges (Visa=blue, Mastercard=red, Verve=green). | ☐ | |
| TC-15.3.4 | Create BIN range | BIN Management | 1. **New** → binStart `41000000`, binEnd `41999999`, scheme VISA. | BIN range saved. | ☐ | |
| TC-15.3.5 | BIN lookup | BIN Management | API: `GET /api/v1/bins/lookup?pan=41234567` | 200; `scheme: "VISA"` | ☐ | |

#### 15.4 Settlement & Disputes

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-15.4.1 | View settlement batches | Cards → Settlement | 1. Open page. | Current batch shown; scheme tabs for export status. | ☐ | |
| TC-15.4.2 | Close settlement batch | Batch OPEN | 1. **Close Batch** → confirm. | Batch status CLOSED. | ☐ | |
| TC-15.4.3 | Raise dispute | Cards → Disputes | 1. **Raise Dispute** → card, RRN, reason UNAUTHORIZED. | Dispute RAISED status. | ☐ | |
| TC-15.4.4 | Initiate chargeback | Dispute RAISED | 1. **Initiate Chargeback** → pick scheme reason code. | Status CHARGEBACK_INITIATED. | ☐ | |
| TC-15.4.5 | Resolve dispute | Dispute CHARGEBACK_INITIATED | 1. **Resolve** → favor ISSUER, notes. | Status RESOLVED. | ☐ | |

#### 15.5 API Keys & Webhooks

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-15.5.1 | Issue API key | Cards → API Keys | 1. **Issue Key** → name, scopes. | Raw key shown once in reveal dialog; copy button works. | ☐ | |
| TC-15.5.2 | Revoke API key | Key exists | 1. **Revoke** → confirm. | Key inactive; cannot authenticate. | ☐ | |
| TC-15.5.3 | Register webhook | Cards → Webhooks | 1. **Register** → URL, events `AUTHORIZATION.APPROVED`, secret. | Webhook created; secret shown once. | ☐ | |
| TC-15.5.4 | View delivery log | Webhook + at least 1 delivery attempt | 1. Delivery log side panel. | Delivery attempts listed with HTTP status, timestamp, attempt count. | ☐ | |

#### 15.6 API-Level Test Cases (Card Service)

| # | Test Case | HTTP | Endpoint | Request Body | Expected Response | Pass/Fail |
|---|-----------|------|----------|-------------|-------------------|-----------|
| TC-15.6.1 | Card available balance | GET | `/api/v1/cards/{id}/balance` | — | 200; `availableBalance` numeric | ☐ |
| TC-15.6.2 | Authorize transaction (internal) | POST | `/api/v1/cards/authorize` | `{"cardId":"{id}","amount":100,"currencyCode":"840","processingCode":"000000","entryMode":"CHIP"}` | 200; `responseCode:"00"` if approved | ☐ |
| TC-15.6.3 | Issue API key | POST | `/card-api/v1/api-keys` | `{"name":"Test Key","scopes":["cards:read"]}` | 201; `rawKey` field present (one-time) | ☐ |
| TC-15.6.4 | Spending by category | GET | `/card-api/v1/cards/{id}/analytics/by-category?from=2026-01-01&to=2026-04-30` | — | 200; array of `{category, totalAmount, transactionCount}` | ☐ |

---

### Scenario 16: Groups & Centers

*Covers: Module 10 — Group & Center Module (Microfinance)*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-16.1 | Create group | Groups & Centers → Groups | 1. **New Group** → name, office, staff. | Group PENDING. | ☐ | |
| TC-16.2 | Activate group | Group PENDING | 1. **Activate** → confirm. | Group ACTIVE. | ☐ | |
| TC-16.3 | Add member to group | Group ACTIVE; ACTIVE customer | 1. Members tab → **Add Member** → pick customer. | Customer linked to group. | ☐ | |
| TC-16.4 | Remove member from group | Group ACTIVE; member exists | 1. Members tab → **Remove** → confirm. | Member unlinked. | ☐ | |
| TC-16.5 | Create center | Groups → Centers | 1. **New Center** → name, office. | Center created. | ☐ | |
| TC-16.6 | Collection sheet generation | Group ACTIVE with members and loans | 1. Collection Sheet tab → **Generate**. | Sheet rows for each member with due amounts. | ☐ | |

---

### Scenario 17: Notifications & Messaging

*Covers: Module 9 — Notifications PRD*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-17.1 | Notification bell count | Admin → any action that triggers notification | 1. View topbar notification bell. | Unread count badge visible. | ☐ | |
| TC-17.2 | View in-app inbox | Click bell icon | 1. Click bell. | Dropdown shows recent notifications with timestamp. | ☐ | |
| TC-17.3 | Mark all as read | Unread notifications exist | 1. **Mark All Read** button. | Unread count resets to 0. | ☐ | |
| TC-17.4 | SMS campaign create | Admin → SMS Campaigns | 1. **New Campaign** → name, type ALL, trigger DIRECT, RRULE monthly. 2. Save. | Campaign created PENDING. | ☐ | |
| TC-17.5 | Activate SMS campaign | Campaign WAITING_FOR_ACTIVATION | 1. **Activate** → confirm. | Status ACTIVE. | ☐ | |
| TC-17.6 | View SMS messages | Campaign ACTIVE | 1. Slide-in messages panel. | Per-recipient delivery status shown (PENDING/SENT/FAILED). | ☐ | |
| TC-17.7 | Create webhook hook | Admin → Hooks | 1. **New Hook** → WEB type, events [account.created, loan.approved]. | Hook created; event chips displayed. | ☐ | |
| TC-17.8 | Notification template test send | Admin → Notifications | 1. Test Send modal → email address. 2. Send. | Email appears in MailHog at `localhost:8025`. | ☐ | |

---

### Scenario 18: CoB Scheduler

*Covers: Module 16 — CoB Scheduler*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-18.1 | View CoB job list | Reports → CoB Scheduler | 1. Open page. | 3 job cards: standingOrderExecutionJob, interestAccrualJob, arrearsClassificationJob. | ☐ | |
| TC-18.2 | Run standing order job | Reports → CoB | 1. **Run Now** on standingOrderExecutionJob. | Job STARTED; history row appears; no exception. | ☐ | |
| TC-18.3 | Run arrears classification | Reports → CoB | 1. **Run Now** on arrearsClassificationJob. | Job completes; loans past due → `inArrears: true`. | ☐ | |
| TC-18.4 | Job history detail | Any job with completed run | 1. Expand history panel. | Duration, start time, end time, status shown per run. | ☐ | |
| TC-18.5 | Standing orders execute correctly | Active standing order due today | 1. Run standingOrderExecutionJob. | Order executed; payment record created; `nextExecutionDate` updated. | ☐ | |

---

### Scenario 19: Batch API

*Covers: Module 17 — Batch API*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-19.1 | Batch: create customer then account | Backend running | API: `POST /api/v1/batches` body = 2-step batch (create customer, open account referencing `$.id`) | Both requests succeed; customer and account IDs returned. | ☐ | |
| TC-19.2 | Batch with transaction rollback | 2-step batch where step 2 fails | API: `POST /api/v1/batches?enclosingTransaction=true` with step 2 causing 400 | Both steps rolled back; neither customer nor account persisted. | ☐ | |

---

### Scenario 20: Permission / Role-Based Access Control

*Covers: Security Architecture — RBAC*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-20.1 | CUSTOMER role — self service accounts | Auth as CUSTOMER role | API: `GET /api/v1/self/accounts` | 200; only own accounts returned. | ☐ | |
| TC-20.2 | CUSTOMER cannot access admin endpoints | Auth as CUSTOMER | API: `GET /api/v1/users` | 403 Forbidden. | ☐ | |
| TC-20.3 | TELLER cannot delete users | Auth as TELLER | API: `DELETE /api/v1/users/{id}` | 403 Forbidden. | ☐ | |
| TC-20.4 | Self-service enumeration prevention | CUSTOMER viewing another customer | API: `GET /api/v1/self/accounts` with another customer's token | 404 (not 403) — prevents enumeration. | ☐ | |
| TC-20.5 | ADMIN full access | Auth as ADMIN | API: `GET /api/v1/users` | 200; user list returned. | ☐ | |

---

### Scenario 21: Multi-Currency Operations

*Covers: Multi-Currency Architecture*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-21.1 | Cross-currency transfer USD→KES | USD account + KES account; USD/KES rate seeded | API: transfer with different currencies | 200; `crossCurrency:true`; `exchangeRateUsed` non-null; source debited USD, destination credited KES. | ☐ | |
| TC-21.2 | Exchange rate update | Admin → Exchange Rates | 1. Edit USD/KES rate → `136.00`. 2. Save. | Rate updated; inverse KES/USD auto-generated as `1/136.00`. | ☐ | |
| TC-21.3 | Transfer fails when rate missing | Two currencies; no rate configured | API: cross-currency transfer | 400; "EXCHANGE_RATE_NOT_CONFIGURED". | ☐ | |
| TC-21.4 | Deactivate exchange rate | Rate active | 1. **Delete** rate. | Rate deactivated; cross-currency transfers between those currencies fail. | ☐ | |

---

### Scenario 22: Global Search

*Covers: Module 36 — Global Search*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-22.1 | Search by customer name | Customer with known name exists | API: `GET /api/v1/search?query=John` | Results include CLIENTS with matching name. | ☐ | |
| TC-22.2 | Search by loan account number | Loan with known number | API: `GET /api/v1/search?query=001-LOAN` | LOANS entity in results. | ☐ | |
| TC-22.3 | Filter by resource type | Multiple entity types match query | API: `GET /api/v1/search?query=John&resource=CLIENTS` | Only CLIENTS in results. | ☐ | |
| TC-22.4 | Invalid resource type | — | API: `GET /api/v1/search?query=John&resource=INVALID` | 400; "INVALID_RESOURCE". | ☐ | |

---

### Scenario 23: Two-Factor Authentication

*Covers: Module 37 — 2FA*

| # | Test Case | Preconditions | Steps | Expected Result | Pass/Fail | Notes |
|---|-----------|--------------|-------|-----------------|-----------|-------|
| TC-23.1 | Generate OTP | User exists | API: `POST /api/v1/twofactor/generate` body `{"userId":"{id}","deliveryMethod":"EMAIL"}` | 200; OTP sent; MailHog shows email. | ☐ | |
| TC-23.2 | Verify valid OTP | OTP generated | API: `POST /api/v1/twofactor/verify` body `{"token":"{otp}"}` | 200; `verified:true`. | ☐ | |
| TC-23.3 | Expired OTP rejected | OTP generated > 10 min ago | API: verify with expired OTP | 400; "TOKEN_EXPIRED". | ☐ | |
| TC-23.4 | Used OTP rejected | OTP already verified | API: verify same OTP twice | 400; "TOKEN_ALREADY_USED". | ☐ | |

---

## Defect Log

| Defect ID | Test Case | Description | Severity | Status | Assigned To | Resolution |
|-----------|-----------|-------------|----------|--------|-------------|------------|
| DEF-001 | | | | | | |
| DEF-002 | | | | | | |
| DEF-003 | | | | | | |
| DEF-004 | | | | | | |
| DEF-005 | | | | | | |

**Severity Guide:**
- **Critical** — system crash, data loss, double-debit/credit, security breach
- **High** — core banking workflow broken (loan disbursement, payment, account opening), no workaround
- **Medium** — feature impaired but workaround exists (e.g. use API directly instead of UI)
- **Low** — cosmetic issue, minor label mismatch, non-blocking UX

---

## Sign-off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Business Owner | | | |
| QA Lead | | | |
| Development Lead | | | |
| Product Manager | | | |

---

*Generated by Claude UAT Script Generator — CBA Platform v1.0 — Session 103 — 2026-04-19*
