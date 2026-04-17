# CBA Banking Modules Reference

## Module Catalogue

Each module below follows the same pattern:
- JPA Entity + Repository
- Service (business logic, `@Transactional`)
- REST Controller (thin, delegates to service)
- DTOs (request/response, no entity leakage)
- Flyway migration for its tables
- Unit tests (Mockito) + integration tests (Testcontainers)

---

## 1. Customer Module

**Purpose**: KYC onboarding, profile management, identity verification

### Key Operations
- `POST /api/v1/customers` — create customer, trigger KYC workflow
- `GET /api/v1/customers/{id}` — get customer profile (PII decrypted for authorized roles)
- `PUT /api/v1/customers/{id}/kyc-status` — update KYC status (ADMIN/TELLER only)
- `GET /api/v1/customers/{id}/accounts` — list all accounts for a customer

### Business Rules
- Customer must complete KYC before opening any account
- Email must be unique across all customers
- National ID / passport number encrypted and deduplicated
- Customer status: `PENDING_KYC → ACTIVE → SUSPENDED → CLOSED`

---

## 2. Account Module

**Purpose**: Manage savings, checking, and fixed deposit accounts

### Key Operations
- `POST /api/v1/accounts` — open new account (requires active customer + product)
- `GET /api/v1/accounts/{id}` — get account details with current balance
- `GET /api/v1/accounts/{id}/transactions` — paginated transaction history
- `PUT /api/v1/accounts/{id}/status` — activate, freeze, close account
- `POST /api/v1/accounts/{id}/deposit` — manual deposit (teller operation)
- `POST /api/v1/accounts/{id}/withdraw` — manual withdrawal (teller operation)

### Business Rules
- Balance can never go below `minimum_balance` (configurable per product)
- Closed accounts are read-only; balance must be zero before closing
- All debits/credits produce an immutable `Transaction` record
- Account number format: `{branch_code}-{type_code}-{sequence}` (e.g., `001-SAV-0001234`)

### Interest Calculation
```java
// Run daily via @Scheduled or Cron job
public void calculateDailyInterest(UUID accountId) {
    // Compound interest: A = P(1 + r/n)^(nt)
    // Simple interest for savings: I = P × r × t
}
```

---

## 3. Loan Module

**Purpose**: Full loan lifecycle — origination, disbursement, repayment, collections

### Key Operations
- `POST /api/v1/loans` — apply for loan
- `PUT /api/v1/loans/{id}/approve` — approve loan (TELLER/ADMIN)
- `PUT /api/v1/loans/{id}/disburse` — disburse to linked account
- `GET /api/v1/loans/{id}/repayment-schedule` — full amortization schedule
- `POST /api/v1/loans/{id}/repayment` — make a repayment
- `GET /api/v1/loans/{id}/statement` — loan statement

### Loan Status Flow
```
SUBMITTED → UNDER_REVIEW → APPROVED → DISBURSED → ACTIVE
                                                      ↓
                                              CLOSED_OBLIGATIONS_MET
                                              WRITTEN_OFF
                                              IN_ARREARS (sub-state)
```

### Repayment Schedule Generation
```java
// Annuity (equal installments) — default
public List<RepaymentSchedule> generateAnnuitySchedule(
        BigDecimal principal, BigDecimal annualRate, int termMonths, LocalDate startDate) {
    BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, HALF_UP);
    // EMI = P × r × (1+r)^n / ((1+r)^n - 1)
    BigDecimal emi = principal.multiply(monthlyRate)
        .multiply(monthlyRate.add(ONE).pow(termMonths))
        .divide(monthlyRate.add(ONE).pow(termMonths).subtract(ONE), 2, HALF_UP);
    // Generate schedule entries...
}
```

---

## 4. Payment Module

**Purpose**: Internal transfers, external payments, standing orders, bill payments

### Key Operations
- `POST /api/v1/payments/transfer` — transfer between two accounts (same bank)
- `POST /api/v1/payments/external` — initiate external payment (stub for SWIFT/SEPA)
- `POST /api/v1/payments/standing-order` — create recurring payment
- `GET /api/v1/payments/{id}` — get payment status
- `GET /api/v1/accounts/{id}/payments` — payment history for an account

### Payment States
```
PENDING → PROCESSING → COMPLETED
              ↓
           FAILED → REVERSED
```

### Double-Entry Ledger
Every payment must balance — debit one account, credit another:
```java
@Transactional
public Payment processTransfer(TransferRequest request) {
    Account source = accountRepository.findByIdWithLock(request.sourceAccountId());  // SELECT FOR UPDATE
    Account destination = accountRepository.findByIdWithLock(request.destinationAccountId());

    validateSufficientBalance(source, request.amount());

    source.debit(request.amount());
    destination.credit(request.amount());

    Transaction debitTx = createTransaction(source, DEBIT, request.amount(), request.reference());
    Transaction creditTx = createTransaction(destination, CREDIT, request.amount(), request.reference());

    auditLogService.log("PAYMENT", payment.getId(), "TRANSFER_EXECUTED", null, request);
    return paymentRepository.save(payment);
}
```

---

## 5. Product Module

**Purpose**: Define loan and deposit product templates with interest rates, fees, terms

### Loan Products
- `name`, `description`, `currency`
- `min_principal`, `max_principal`
- `min_interest_rate`, `max_interest_rate`, `default_interest_rate`
- `min_term_months`, `max_term_months`
- `repayment_type`: ANNUITY, FLAT, DECLINING_BALANCE
- `fees`: origination fee, late payment fee

### Deposit Products
- `account_type`: SAVINGS, CHECKING, FIXED_DEPOSIT
- `minimum_balance`
- `interest_rate`
- `interest_compounding`: DAILY, MONTHLY, ANNUALLY

---

## 6. Open Banking Module (FAPI 2.0)

**Purpose**: Expose standardized APIs for third-party access (TPPs), mobile apps, and partners

### Endpoints (UK Open Banking v3.1 compliant)
```
GET  /open-banking/v3.1/aisp/accounts
GET  /open-banking/v3.1/aisp/accounts/{id}
GET  /open-banking/v3.1/aisp/accounts/{id}/balances
GET  /open-banking/v3.1/aisp/accounts/{id}/transactions
POST /open-banking/v3.1/pisp/domestic-payments
GET  /open-banking/v3.1/pisp/domestic-payments/{id}
POST /open-banking/v3.1/cbpii/funds-confirmation
```

### Consent Management
- Each TPP access requires a `Consent` record with scopes and expiry
- Consent flow: `AWAITING_AUTHORISATION → AUTHORISED → REVOKED`
- Customers can view and revoke consents from the web/mobile portal

---

## 7. Notification Module

**Purpose**: Event-driven notifications via email and SMS

### Events Triggered
- Account opened / closed
- Large transaction (configurable threshold)
- Loan approved / disbursed / payment due
- Failed login attempt
- Password / profile change

```java
@Component
public class NotificationEventListener {
    @EventListener
    @Async
    public void onAccountOpened(AccountOpenedEvent event) {
        // Send welcome email
    }

    @EventListener
    @Async
    public void onLargeTransaction(LargeTransactionEvent event) {
        // Send SMS alert
    }
}
```

---

## 8. Audit Module

**Purpose**: Immutable, append-only audit trail for compliance

### Rules
- NEVER update or delete audit log records
- Log every state-changing operation (CREATE, UPDATE, STATUS_CHANGE)
- Capture: entity type, entity ID, user, timestamp, old value, new value, IP address
- Retention: minimum 10 years (configurable)

### Query API
```
GET /api/v1/audit?entityType=LOAN&entityId={id}&fromDate=...&toDate=...
GET /api/v1/audit?changedBy={userId}&fromDate=...&toDate=...
```
