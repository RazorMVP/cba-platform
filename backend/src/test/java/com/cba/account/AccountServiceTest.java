package com.cba.account;

import com.cba.account.algorithm.AccountNumberAlgorithmService;
import com.cba.account.dto.*;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.customer.KycStatus;
import com.cba.product.DepositProduct;
import com.cba.product.LockInFrequencyType;
import com.cba.system.GlobalConfiguration;
import com.cba.system.GlobalConfigurationRepository;
import com.cba.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService — unit tests")
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock CustomerRepository customerRepository;
    @Mock com.cba.product.DepositProductRepository depositProductRepository;
    @Mock AccountHoldRepository accountHoldRepository;
    @Mock AccountNumberAlgorithmService accountNumberAlgorithmService;
    @Mock AuditLogService auditLogService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock TenantService tenantService;
    @Mock GlobalConfigurationRepository globalConfigRepository;

    @InjectMocks AccountService accountService;

    private UUID customerId;
    private UUID productId;
    private UUID accountId;
    private Customer activeCustomer;
    private DepositProduct product;
    private Account activeAccount;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        productId  = UUID.randomUUID();
        accountId  = UUID.randomUUID();

        activeCustomer = new Customer();
        activeCustomer.setId(customerId);
        activeCustomer.setFirstName("Jane");
        activeCustomer.setLastName("Doe");
        activeCustomer.setEmail("jane@example.com");
        activeCustomer.setExternalId("CUST-001");
        activeCustomer.setKycStatus(KycStatus.ACTIVE);

        product = new DepositProduct();
        product.setId(productId);
        product.setName("Standard Savings");
        product.setShortName("SAVI");
        product.setMinimumBalance(BigDecimal.ZERO);
        product.setInterestRate(new BigDecimal("5.00"));
        product.setAllowOverdraft(false);

        activeAccount = new Account();
        activeAccount.setId(accountId);
        activeAccount.setAccountNumber("001-SAV-0001234");
        activeAccount.setCustomer(activeCustomer);
        activeAccount.setProduct(product);
        activeAccount.setAccountType(AccountType.SAVINGS);
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setBalance(new BigDecimal("1000.00"));
        activeAccount.setCurrencyCode("USD");
        activeAccount.setOpenedDate(LocalDate.now().minusMonths(6));
    }

    // ── Helper to stub accountHoldRepository (no active holds) ────────────────

    private void stubNoHolds(UUID id) {
        when(accountHoldRepository.sumActiveHoldsByAccount(id)).thenReturn(BigDecimal.ZERO);
    }

    // ── Helper: build a full AccountResponse-compatible account ───────────────

    private Account savedAccount(AccountStatus status) {
        Account a = new Account();
        a.setId(accountId);
        a.setAccountNumber("001-SAV-0001234");
        a.setCustomer(activeCustomer);
        a.setProduct(product);
        a.setAccountType(AccountType.SAVINGS);
        a.setStatus(status);
        a.setBalance(BigDecimal.ZERO);
        a.setCurrencyCode("USD");
        a.setOpenedDate(LocalDate.now());
        return a;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // openAccount
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("openAccount")
    class OpenAccount {

        @Test
        @DisplayName("saves account and returns response when customer KYC is ACTIVE")
        void openAccount_happyPath() {
            OpenAccountRequest req = new OpenAccountRequest(customerId, productId, AccountType.SAVINGS, "USD");

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(depositProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(accountNumberAlgorithmService.generate(any(), any())).thenReturn("001-SAV-0001234");

            Account saved = savedAccount(AccountStatus.SUBMITTED);
            when(accountRepository.save(any(Account.class))).thenReturn(saved);
            stubNoHolds(accountId);

            AccountResponse resp = accountService.openAccount(req);

            assertThat(resp).isNotNull();
            assertThat(resp.accountNumber()).isEqualTo("001-SAV-0001234");
            verify(auditLogService).log(eq("ACCOUNT"), any(), eq("OPENED"), isNull(), any());
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("rejects account opening when customer KYC is not ACTIVE")
        void openAccount_kycNotActive_throws() {
            activeCustomer.setKycStatus(KycStatus.PENDING_KYC);
            OpenAccountRequest req = new OpenAccountRequest(customerId, productId, AccountType.SAVINGS, null);

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> accountService.openAccount(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("KYC");
        }

        @Test
        @DisplayName("throws 404 when customer not found")
        void openAccount_customerNotFound_throws() {
            OpenAccountRequest req = new OpenAccountRequest(customerId, productId, AccountType.SAVINGS, null);
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.openAccount(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("throws 404 when product not found")
        void openAccount_productNotFound_throws() {
            OpenAccountRequest req = new OpenAccountRequest(customerId, productId, AccountType.SAVINGS, null);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(depositProductRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.openAccount(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("uses tenant base currency when request currency is null")
        void openAccount_nullCurrency_usesTenantCurrency() {
            OpenAccountRequest req = new OpenAccountRequest(customerId, productId, AccountType.SAVINGS, null);

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(activeCustomer));
            when(depositProductRepository.findById(productId)).thenReturn(Optional.of(product));
            when(accountNumberAlgorithmService.generate(any(), any())).thenReturn("001-SAV-0001234");
            when(tenantService.getBaseCurrency(any())).thenReturn("KES");

            Account saved = savedAccount(AccountStatus.SUBMITTED);
            when(accountRepository.save(any(Account.class))).thenReturn(saved);
            stubNoHolds(accountId);

            accountService.openAccount(req);

            verify(tenantService).getBaseCurrency(any());
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // lifecycle transitions
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("lifecycle transitions")
    class Lifecycle {

        @Test
        @DisplayName("approveAccount transitions SUBMITTED → APPROVED")
        void approveAccount_happyPath() {
            Account submitted = savedAccount(AccountStatus.SUBMITTED);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(submitted));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubNoHolds(accountId);

            AccountResponse resp = accountService.approveAccount(accountId);

            assertThat(resp.status()).isEqualTo(AccountStatus.APPROVED);
            verify(auditLogService).log(eq("ACCOUNT"), any(), eq("APPROVED"), any(), any());
        }

        @Test
        @DisplayName("approveAccount throws when account is not SUBMITTED")
        void approveAccount_notSubmitted_throws() {
            Account active = savedAccount(AccountStatus.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> accountService.approveAccount(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("SUBMITTED");
        }

        @Test
        @DisplayName("activateAccount transitions APPROVED → ACTIVE")
        void activateAccount_happyPath() {
            Account approved = savedAccount(AccountStatus.APPROVED);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(approved));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(globalConfigRepository.findByName("enforce-min-required-opening-balance"))
                .thenReturn(Optional.empty());
            stubNoHolds(accountId);

            AccountResponse resp = accountService.activateAccount(accountId);

            assertThat(resp.status()).isEqualTo(AccountStatus.ACTIVE);
            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("activateAccount blocks when below min opening balance and config enabled")
        void activateAccount_belowMinOpeningBalance_throws() {
            Account approved = savedAccount(AccountStatus.APPROVED);
            product.setMinRequiredOpeningBalance(new BigDecimal("500.00"));
            approved.setProduct(product);

            GlobalConfiguration config = new GlobalConfiguration();
            config.setEnabled(true);
            config.setBooleanValue(true);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(approved));
            when(globalConfigRepository.findByName("enforce-min-required-opening-balance"))
                .thenReturn(Optional.of(config));

            assertThatThrownBy(() -> accountService.activateAccount(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("minimum required opening balance");
        }

        @Test
        @DisplayName("activateAccount throws when account is not APPROVED")
        void activateAccount_notApproved_throws() {
            Account submitted = savedAccount(AccountStatus.SUBMITTED);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(submitted));

            assertThatThrownBy(() -> accountService.activateAccount(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("rejectAccount transitions SUBMITTED → REJECTED")
        void rejectAccount_happyPath() {
            Account submitted = savedAccount(AccountStatus.SUBMITTED);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(submitted));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubNoHolds(accountId);

            AccountResponse resp = accountService.rejectAccount(accountId);

            assertThat(resp.status()).isEqualTo(AccountStatus.REJECTED);
            verify(auditLogService).log(eq("ACCOUNT"), any(), eq("REJECTED"), any(), any());
        }

        @Test
        @DisplayName("rejectAccount throws when account is not SUBMITTED")
        void rejectAccount_notSubmitted_throws() {
            Account active = savedAccount(AccountStatus.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> accountService.rejectAccount(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("SUBMITTED");
        }

        @Test
        @DisplayName("reactivateAccount transitions DORMANT → ACTIVE")
        void reactivateAccount_happyPath() {
            Account dormant = savedAccount(AccountStatus.DORMANT);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(dormant));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubNoHolds(accountId);

            AccountResponse resp = accountService.reactivateAccount(accountId);

            assertThat(resp.status()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("reactivateAccount throws when account is not DORMANT")
        void reactivateAccount_notDormant_throws() {
            Account active = savedAccount(AccountStatus.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> accountService.reactivateAccount(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("DORMANT");
        }

        @Test
        @DisplayName("updateStatus to CLOSED requires zero balance")
        void updateStatus_close_nonZeroBalance_throws() {
            Account account = savedAccount(AccountStatus.ACTIVE);
            account.setBalance(new BigDecimal("100.00"));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.updateStatus(accountId, AccountStatus.CLOSED))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("zero");
        }

        @Test
        @DisplayName("updateStatus to CLOSED succeeds when balance is zero and no holds")
        void updateStatus_close_zeroBalance_succeeds() {
            Account account = savedAccount(AccountStatus.ACTIVE);
            account.setBalance(BigDecimal.ZERO);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountHoldRepository.sumActiveHoldsByAccount(accountId)).thenReturn(BigDecimal.ZERO);
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubNoHolds(accountId);

            AccountResponse resp = accountService.updateStatus(accountId, AccountStatus.CLOSED);
            assertThat(resp.status()).isEqualTo(AccountStatus.CLOSED);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // deposit
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deposit")
    class Deposit {

        @Test
        @DisplayName("credits balance on ACTIVE account")
        void deposit_activeAccount_credits() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            Transaction tx = Transaction.of(activeAccount, TransactionType.DEPOSIT,
                new BigDecimal("200.00"), new BigDecimal("1200.00"), "test", "TXN-001", "teller1");
            when(transactionRepository.save(any())).thenReturn(tx);

            TransactionResponse resp = accountService.deposit(accountId, new BigDecimal("200.00"), "test", "teller1");

            assertThat(resp).isNotNull();
            assertThat(activeAccount.getBalance()).isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("credits balance on DORMANT account (credits always allowed)")
        void deposit_dormantAccount_credits() {
            activeAccount.setStatus(AccountStatus.DORMANT);
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            Transaction tx = Transaction.of(activeAccount, TransactionType.DEPOSIT,
                new BigDecimal("50.00"), new BigDecimal("1050.00"), "test", "TXN-002", "sys");
            when(transactionRepository.save(any())).thenReturn(tx);

            assertThatNoException().isThrownBy(
                () -> accountService.deposit(accountId, new BigDecimal("50.00"), "test", "sys"));
        }

        @Test
        @DisplayName("throws when account is CLOSED")
        void deposit_closedAccount_throws() {
            activeAccount.setStatus(AccountStatus.CLOSED);
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("100.00"), "test", "sys"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("CLOSED");
        }

        @Test
        @DisplayName("throws 404 when account not found")
        void deposit_notFound_throws() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.deposit(accountId, BigDecimal.TEN, "test", "sys"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // withdraw
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @BeforeEach
        void noLockinConfig() {
            lenient().when(globalConfigRepository.findByName("enforce-lockin-period-withdrawal"))
                .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("debits balance when funds are sufficient")
        void withdraw_sufficientFunds_debits() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.sumActiveHoldsByAccount(accountId)).thenReturn(BigDecimal.ZERO);
            when(accountRepository.save(any())).thenReturn(activeAccount);

            Transaction tx = Transaction.of(activeAccount, TransactionType.WITHDRAWAL,
                new BigDecimal("300.00"), new BigDecimal("700.00"), "atm", "TXN-003", "system");
            when(transactionRepository.save(any())).thenReturn(tx);

            TransactionResponse resp = accountService.withdraw(accountId, new BigDecimal("300.00"), "atm", "system");
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("throws when effective available balance is insufficient")
        void withdraw_insufficientBalance_throws() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.sumActiveHoldsByAccount(accountId)).thenReturn(BigDecimal.ZERO);

            assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("5000.00"), "over", "sys"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("balance");
        }

        @Test
        @DisplayName("throws when account is not ACTIVE")
        void withdraw_notActiveAccount_throws() {
            activeAccount.setStatus(AccountStatus.DORMANT);
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.withdraw(accountId, BigDecimal.TEN, "test", "sys"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("DORMANT");
        }

        @Test
        @DisplayName("throws when still within lock-in period")
        void withdraw_withinLockinPeriod_throws() {
            product.setLockinPeriodFrequency(12);
            product.setLockinPeriodFrequencyType(LockInFrequencyType.MONTHS);
            activeAccount.setOpenedDate(LocalDate.now().minusMonths(3)); // opened 3 months ago, lock-in is 12 months

            GlobalConfiguration lockinConfig = new GlobalConfiguration();
            lockinConfig.setEnabled(true);
            lockinConfig.setBooleanValue(true);

            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(globalConfigRepository.findByName("enforce-lockin-period-withdrawal"))
                .thenReturn(Optional.of(lockinConfig));

            assertThatThrownBy(() -> accountService.withdraw(accountId, BigDecimal.TEN, "test", "sys"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("lock-in");
        }

        @Test
        @DisplayName("succeeds after lock-in period has passed")
        void withdraw_afterLockinPeriod_succeeds() {
            product.setLockinPeriodFrequency(1);
            product.setLockinPeriodFrequencyType(LockInFrequencyType.MONTHS);
            activeAccount.setOpenedDate(LocalDate.now().minusMonths(3)); // lock-in expired

            GlobalConfiguration lockinConfig = new GlobalConfiguration();
            lockinConfig.setEnabled(true);
            lockinConfig.setBooleanValue(true);

            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(globalConfigRepository.findByName("enforce-lockin-period-withdrawal"))
                .thenReturn(Optional.of(lockinConfig));
            when(accountHoldRepository.sumActiveHoldsByAccount(accountId)).thenReturn(BigDecimal.ZERO);
            when(accountRepository.save(any())).thenReturn(activeAccount);

            Transaction tx = Transaction.of(activeAccount, TransactionType.WITHDRAWAL,
                BigDecimal.TEN, new BigDecimal("990.00"), "test", "TXN-004", "sys");
            when(transactionRepository.save(any())).thenReturn(tx);

            assertThatNoException().isThrownBy(
                () -> accountService.withdraw(accountId, BigDecimal.TEN, "test", "sys"));
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // placeHold / releaseHold
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hold management")
    class HoldManagement {

        @Test
        @DisplayName("placeHold places hold when funds are available")
        void placeHold_sufficientBalance_placesHold() {
            AccountHoldRequest req = new AccountHoldRequest(new BigDecimal("100.00"), "Fraud review", null);
            AccountHold savedHold = new AccountHold();
            savedHold.setId(UUID.randomUUID());
            savedHold.setAccount(activeAccount);
            savedHold.setAmount(req.amount());
            savedHold.setReason(req.reason());
            savedHold.setReferenceNumber("HLD-12345");
            savedHold.setStatus(AccountHoldStatus.ACTIVE);

            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.sumActiveHoldsByAccount(accountId)).thenReturn(BigDecimal.ZERO);
            when(accountHoldRepository.save(any())).thenReturn(savedHold);

            AccountHoldResponse resp = accountService.placeHold(accountId, req, "ops");
            assertThat(resp.amount()).isEqualByComparingTo("100.00");
            verify(auditLogService).log(eq("ACCOUNT_HOLD"), any(), eq("HOLD_PLACED"), isNull(), any());
        }

        @Test
        @DisplayName("placeHold throws when effective available balance is insufficient")
        void placeHold_insufficientBalance_throws() {
            AccountHoldRequest req = new AccountHoldRequest(new BigDecimal("9999.00"), "Block", null);

            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.sumActiveHoldsByAccount(accountId)).thenReturn(BigDecimal.ZERO);

            assertThatThrownBy(() -> accountService.placeHold(accountId, req, "ops"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("available balance");
        }

        @Test
        @DisplayName("placeHold throws when account is not ACTIVE")
        void placeHold_notActive_throws() {
            activeAccount.setStatus(AccountStatus.DORMANT);
            AccountHoldRequest req = new AccountHoldRequest(new BigDecimal("100.00"), "test", null);

            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.placeHold(accountId, req, "ops"))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("releaseHold transitions ACTIVE hold to RELEASED")
        void releaseHold_activeHold_releases() {
            UUID holdId = UUID.randomUUID();
            AccountHold hold = new AccountHold();
            hold.setId(holdId);
            hold.setAccount(activeAccount);
            hold.setAmount(new BigDecimal("100.00"));
            hold.setReason("Fraud review");
            hold.setStatus(AccountHoldStatus.ACTIVE);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));
            when(accountHoldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AccountHoldResponse resp = accountService.releaseHold(accountId, holdId, "ops");
            assertThat(resp.status()).isEqualTo(AccountHoldStatus.RELEASED);
        }

        @Test
        @DisplayName("releaseHold throws when hold is already RELEASED")
        void releaseHold_alreadyReleased_throws() {
            UUID holdId = UUID.randomUUID();
            AccountHold hold = new AccountHold();
            hold.setId(holdId);
            hold.setAccount(activeAccount);
            hold.setStatus(AccountHoldStatus.RELEASED);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

            assertThatThrownBy(() -> accountService.releaseHold(accountId, holdId, "ops"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already");
        }

        @Test
        @DisplayName("releaseHold throws 404 when hold belongs to different account")
        void releaseHold_wrongAccount_throws() {
            UUID holdId = UUID.randomUUID();
            Account otherAccount = new Account();
            otherAccount.setId(UUID.randomUUID());

            AccountHold hold = new AccountHold();
            hold.setId(holdId);
            hold.setAccount(otherAccount);
            hold.setStatus(AccountHoldStatus.ACTIVE);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.findById(holdId)).thenReturn(Optional.of(hold));

            assertThatThrownBy(() -> accountService.releaseHold(accountId, holdId, "ops"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // interest
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("interest operations")
    class Interest {

        @Test
        @DisplayName("calculateInterest returns projected daily interest")
        void calculateInterest_returnsProjectedInterest() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

            var result = accountService.calculateInterest(accountId);

            assertThat(result).containsKey("projectedDailyInterest");
            BigDecimal interest = (BigDecimal) result.get("projectedDailyInterest");
            assertThat(interest).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("calculateInterest throws when account is not ACTIVE")
        void calculateInterest_notActive_throws() {
            activeAccount.setStatus(AccountStatus.DORMANT);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.calculateInterest(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("postInterest credits account balance and creates INTEREST_CREDIT transaction")
        void postInterest_creditsBalance() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountRepository.save(any())).thenReturn(activeAccount);

            Transaction interestTx = Transaction.of(activeAccount, TransactionType.INTEREST_CREDIT,
                new BigDecimal("0.1370"), new BigDecimal("1000.1370"), "Manual interest posting",
                "INT-MANUAL-123", "system");
            when(transactionRepository.save(any())).thenReturn(interestTx);

            stubNoHolds(accountId);

            AccountResponse resp = accountService.postInterest(accountId);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("ACCOUNT"), any(), eq("POST_INTEREST"), any(), any());
        }

        @Test
        @DisplayName("postInterest throws when account is not ACTIVE")
        void postInterest_notActive_throws() {
            activeAccount.setStatus(AccountStatus.DORMANT);
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.postInterest(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("postInterest throws when balance is zero (no interest due)")
        void postInterest_zeroBalance_throws() {
            activeAccount.setBalance(BigDecimal.ZERO);
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.postInterest(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("zero");
        }

        @Test
        @DisplayName("postInterest throws when interest rate is zero")
        void postInterest_zeroRate_throws() {
            product.setInterestRate(BigDecimal.ZERO);
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(activeAccount));

            assertThatThrownBy(() -> accountService.postInterest(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("zero");
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // reads
    // ────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("read operations")
    class Reads {

        @Test
        @DisplayName("getAccount throws 404 when not found")
        void getAccount_notFound_throws() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.getAccount(accountId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("getAccount returns response when found")
        void getAccount_found_returnsResponse() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
            stubNoHolds(accountId);

            AccountResponse resp = accountService.getAccount(accountId);
            assertThat(resp.accountNumber()).isEqualTo("001-SAV-0001234");
        }

        @Test
        @DisplayName("getCustomerAccounts returns page of responses")
        void getCustomerAccounts_returnsMappedPage() {
            Page<Account> page = new PageImpl<>(List.of(activeAccount));
            when(accountRepository.findByCustomerId(eq(customerId), any(Pageable.class))).thenReturn(page);
            stubNoHolds(accountId);

            Page<AccountResponse> result = accountService.getCustomerAccounts(customerId, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getHolds returns list of holds for account")
        void getHolds_returnsHoldList() {
            AccountHold hold = new AccountHold();
            hold.setId(UUID.randomUUID());
            hold.setAccount(activeAccount);
            hold.setAmount(BigDecimal.TEN);
            hold.setReason("test");
            hold.setStatus(AccountHoldStatus.ACTIVE);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(activeAccount));
            when(accountHoldRepository.findByAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(List.of(hold));

            List<AccountHoldResponse> holds = accountService.getHolds(accountId);
            assertThat(holds).hasSize(1);
        }

        @Test
        @DisplayName("getOpenAccountTemplate returns products and account types")
        void getOpenAccountTemplate_returnsTemplate() {
            when(depositProductRepository.findAll()).thenReturn(List.of(product));

            var template = accountService.getOpenAccountTemplate();
            assertThat(template).containsKey("depositProducts");
            assertThat(template).containsKey("accountTypes");
        }
    }
}
