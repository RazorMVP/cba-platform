package com.cba.teller;

import com.cba.account.*;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.teller.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TellerService — unit tests")
class TellerServiceTest {

    @Mock TellerRepository tellerRepository;
    @Mock CashierRepository cashierRepository;
    @Mock TellerSessionRepository sessionRepository;
    @Mock CashTransactionRepository cashTransactionRepository;
    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks TellerService tellerService;

    private UUID tellerId;
    private UUID cashierId;
    private UUID sessionId;
    private Teller teller;
    private Cashier cashier;
    private TellerSession session;

    @BeforeEach
    void setUp() {
        tellerId  = UUID.randomUUID();
        cashierId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        teller = new Teller();
        teller.setId(tellerId);
        teller.setName("Main Teller");
        teller.setBranchCode("001");
        teller.setStatus(TellerStatus.ACTIVE);

        cashier = new Cashier();
        cashier.setId(cashierId);
        cashier.setTeller(teller);
        cashier.setStaffId("STAFF-001");

        session = new TellerSession();
        session.setId(sessionId);
        session.setTeller(teller);
        session.setCashier(cashier);
        session.setOpeningBalance(new BigDecimal("1000.00"));
        session.setCurrencyCode("USD");
        session.setStatus(SessionStatus.OPEN);
    }

    // ── Teller CRUD ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllTellers")
    class GetAllTellers {

        @Test
        @DisplayName("returns all tellers")
        void getAllTellers_returnsList() {
            when(tellerRepository.findAll()).thenReturn(List.of(teller));
            assertThat(tellerService.getAllTellers()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getTeller")
    class GetTeller {

        @Test
        @DisplayName("returns teller when found")
        void getTeller_found() {
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            assertThat(tellerService.getTeller(tellerId)).isNotNull();
        }

        @Test
        @DisplayName("throws 404 when not found")
        void getTeller_notFound() {
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> tellerService.getTeller(tellerId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("createTeller")
    class CreateTeller {

        @Test
        @DisplayName("creates and audits teller")
        void createTeller_valid() {
            TellerRequest req = new TellerRequest("Main Teller", "Desc", "001", null,
                    LocalDate.now(), null);
            when(tellerRepository.save(any())).thenReturn(teller);

            var resp = tellerService.createTeller(req);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("Teller"), any(), eq("CREATE"), isNull(), any());
        }
    }

    @Nested
    @DisplayName("activateTeller")
    class ActivateTeller {

        @Test
        @DisplayName("sets status ACTIVE")
        void activateTeller_succeeds() {
            teller.setStatus(TellerStatus.INACTIVE);
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(tellerRepository.save(any())).thenReturn(teller);

            tellerService.activateTeller(tellerId);
            assertThat(teller.getStatus()).isEqualTo(TellerStatus.ACTIVE);
            verify(auditLogService).log(eq("Teller"), any(), eq("ACTIVATE"), isNull(), any());
        }

        @Test
        @DisplayName("throws when not found")
        void activateTeller_notFound() {
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> tellerService.activateTeller(tellerId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("closeTeller")
    class CloseTeller {

        @Test
        @DisplayName("sets status CLOSED and records end date")
        void closeTeller_succeeds() {
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(tellerRepository.save(any())).thenReturn(teller);

            tellerService.closeTeller(tellerId);
            assertThat(teller.getStatus()).isEqualTo(TellerStatus.CLOSED);
            assertThat(teller.getEndDate()).isNotNull();
        }
    }

    // ── Cashier management ────────────────────────────────────────────

    @Nested
    @DisplayName("assignCashier")
    class AssignCashier {

        @Test
        @DisplayName("assigns cashier to active teller")
        void assignCashier_happyPath() {
            CashierRequest req = new CashierRequest("STAFF-001", "Main cashier",
                    null, null, true, null, null);
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(cashierRepository.save(any())).thenReturn(cashier);

            var resp = tellerService.assignCashier(tellerId, req);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("throws when teller is not ACTIVE")
        void assignCashier_tellerNotActive_throws() {
            teller.setStatus(TellerStatus.INACTIVE);
            CashierRequest req = new CashierRequest("STAFF-001", null,
                    null, null, true, null, null);
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));

            assertThatThrownBy(() -> tellerService.assignCashier(tellerId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not active");
        }
    }

    @Nested
    @DisplayName("getCashiers")
    class GetCashiers {

        @Test
        @DisplayName("returns cashiers for teller")
        void getCashiers_returnsList() {
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(cashierRepository.findByTellerId(tellerId)).thenReturn(List.of(cashier));

            assertThat(tellerService.getCashiers(tellerId)).hasSize(1);
        }
    }

    // ── Session lifecycle ─────────────────────────────────────────────

    @Nested
    @DisplayName("openSession")
    class OpenSession {

        @Test
        @DisplayName("opens session for active teller and cashier")
        void openSession_happyPath() {
            OpenSessionRequest req = new OpenSessionRequest(new BigDecimal("500.00"), "USD");
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));
            when(sessionRepository.findByCashierIdAndSessionDate(eq(cashierId), any()))
                .thenReturn(Optional.empty());
            when(sessionRepository.save(any())).thenReturn(session);

            var resp = tellerService.openSession(tellerId, cashierId, req);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("TellerSession"), any(), eq("OPEN"), isNull(), any());
        }

        @Test
        @DisplayName("throws when cashier belongs to different teller")
        void openSession_cashierMismatch_throws() {
            Teller otherTeller = new Teller();
            otherTeller.setId(UUID.randomUUID());
            cashier.setTeller(otherTeller);

            OpenSessionRequest req = new OpenSessionRequest(BigDecimal.ZERO, "USD");
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));

            assertThatThrownBy(() -> tellerService.openSession(tellerId, cashierId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("does not belong");
        }

        @Test
        @DisplayName("throws when teller is not ACTIVE")
        void openSession_tellerNotActive_throws() {
            teller.setStatus(TellerStatus.INACTIVE);
            OpenSessionRequest req = new OpenSessionRequest(BigDecimal.ZERO, "USD");
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));

            assertThatThrownBy(() -> tellerService.openSession(tellerId, cashierId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("must be ACTIVE");
        }

        @Test
        @DisplayName("throws when session already open for today")
        void openSession_duplicateSession_throws() {
            OpenSessionRequest req = new OpenSessionRequest(BigDecimal.ZERO, "USD");
            when(tellerRepository.findById(tellerId)).thenReturn(Optional.of(teller));
            when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));
            when(sessionRepository.findByCashierIdAndSessionDate(eq(cashierId), any()))
                .thenReturn(Optional.of(session));

            assertThatThrownBy(() -> tellerService.openSession(tellerId, cashierId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already has an open session");
        }
    }

    @Nested
    @DisplayName("closeSession")
    class CloseSession {

        @Test
        @DisplayName("calculates closing balance and difference")
        void closeSession_happyPath() {
            CloseSessionRequest req = new CloseSessionRequest(new BigDecimal("1200.00"), "All good");
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(cashTransactionRepository.sumBySessionIdAndType(sessionId, CashTransactionType.CASH_IN))
                .thenReturn(new BigDecimal("300.00"));
            when(cashTransactionRepository.sumBySessionIdAndType(sessionId, CashTransactionType.CASH_OUT))
                .thenReturn(new BigDecimal("100.00"));
            when(sessionRepository.save(any())).thenReturn(session);

            var resp = tellerService.closeSession(sessionId, req);
            assertThat(resp).isNotNull();
            // openingBalance(1000) + cashIn(300) - cashOut(100) = 1200 expected
            assertThat(session.getClosingBalance()).isEqualByComparingTo("1200.00");
            assertThat(session.getDifference()).isEqualByComparingTo("0.00");
            assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
        }

        @Test
        @DisplayName("throws when session is already CLOSED")
        void closeSession_alreadyClosed_throws() {
            session.setStatus(SessionStatus.CLOSED);
            CloseSessionRequest req = new CloseSessionRequest(BigDecimal.ZERO, null);
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> tellerService.closeSession(sessionId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already closed");
        }
    }

    // ── Cash transactions ─────────────────────────────────────────────

    @Nested
    @DisplayName("recordCashTransaction")
    class RecordCashTransaction {

        @Test
        @DisplayName("records cash-in without account link")
        void recordCashTransaction_cashIn_noAccount() {
            CashTransactionRequest req = new CashTransactionRequest(
                CashTransactionType.CASH_IN, new BigDecimal("200.00"), "USD", null, "deposit"
            );
            CashTransaction ct = new CashTransaction();
            ct.setId(UUID.randomUUID());
            ct.setSession(session);
            ct.setTeller(teller);
            ct.setCashier(cashier);
            ct.setTransactionType(CashTransactionType.CASH_IN);
            ct.setAmount(new BigDecimal("200.00"));

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(cashTransactionRepository.save(any())).thenReturn(ct);

            var resp = tellerService.recordCashTransaction(sessionId, req);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("records cash-in linked to active account and updates balance")
        void recordCashTransaction_cashIn_withAccount() {
            UUID accountId = UUID.randomUUID();
            Account account = buildActiveAccount(accountId, new BigDecimal("500.00"));

            CashTransactionRequest req = new CashTransactionRequest(
                CashTransactionType.CASH_IN, new BigDecimal("200.00"), "USD", accountId, "teller deposit"
            );
            CashTransaction ct = buildCashTx(CashTransactionType.CASH_IN, new BigDecimal("200.00"));

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
            when(cashTransactionRepository.save(any())).thenReturn(ct);

            var resp = tellerService.recordCashTransaction(sessionId, req);
            assertThat(resp).isNotNull();
            assertThat(account.getBalance()).isEqualByComparingTo("700.00");
        }

        @Test
        @DisplayName("records cash-out and debits account balance")
        void recordCashTransaction_cashOut_withAccount() {
            UUID accountId = UUID.randomUUID();
            Account account = buildActiveAccount(accountId, new BigDecimal("500.00"));

            CashTransactionRequest req = new CashTransactionRequest(
                CashTransactionType.CASH_OUT, new BigDecimal("100.00"), "USD", accountId, "withdrawal"
            );
            CashTransaction ct = buildCashTx(CashTransactionType.CASH_OUT, new BigDecimal("100.00"));

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));
            when(cashTransactionRepository.save(any())).thenReturn(ct);

            tellerService.recordCashTransaction(sessionId, req);
            assertThat(account.getBalance()).isEqualByComparingTo("400.00");
        }

        @Test
        @DisplayName("throws when session is closed")
        void recordCashTransaction_sessionClosed_throws() {
            session.setStatus(SessionStatus.CLOSED);
            CashTransactionRequest req = new CashTransactionRequest(
                CashTransactionType.CASH_IN, BigDecimal.TEN, "USD", null, null
            );
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> tellerService.recordCashTransaction(sessionId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("throws when cash-out exceeds account balance")
        void recordCashTransaction_insufficientFunds_throws() {
            UUID accountId = UUID.randomUUID();
            Account account = buildActiveAccount(accountId, new BigDecimal("50.00"));

            CashTransactionRequest req = new CashTransactionRequest(
                CashTransactionType.CASH_OUT, new BigDecimal("200.00"), "USD", accountId, "test"
            );

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> tellerService.recordCashTransaction(sessionId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Insufficient");
        }

        @Test
        @DisplayName("throws when account is not ACTIVE")
        void recordCashTransaction_accountNotActive_throws() {
            UUID accountId = UUID.randomUUID();
            Account account = buildActiveAccount(accountId, new BigDecimal("500.00"));
            account.setStatus(AccountStatus.FROZEN);

            CashTransactionRequest req = new CashTransactionRequest(
                CashTransactionType.CASH_IN, new BigDecimal("100.00"), "USD", accountId, null
            );

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> tellerService.recordCashTransaction(sessionId, req))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("getSessionTransactions")
    class GetSessionTransactions {

        @Test
        @DisplayName("returns transactions for session")
        void getSessionTransactions_returnsList() {
            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(cashTransactionRepository.findBySessionId(sessionId)).thenReturn(List.of());

            assertThat(tellerService.getSessionTransactions(sessionId)).isEmpty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Account buildActiveAccount(UUID id, BigDecimal balance) {
        com.cba.product.DepositProduct product = new com.cba.product.DepositProduct();
        product.setId(UUID.randomUUID());
        product.setName("Savings");
        product.setShortName("SAVI");
        product.setMinimumBalance(BigDecimal.ZERO);
        product.setAllowOverdraft(false);

        Account a = new Account();
        a.setId(id);
        a.setAccountNumber("001-SAV-0001234");
        a.setProduct(product);
        a.setAccountType(AccountType.SAVINGS);
        a.setStatus(AccountStatus.ACTIVE);
        a.setBalance(balance);
        a.setCurrencyCode("USD");
        return a;
    }

    private CashTransaction buildCashTx(CashTransactionType type, BigDecimal amount) {
        CashTransaction ct = new CashTransaction();
        ct.setId(UUID.randomUUID());
        ct.setSession(session);
        ct.setTeller(teller);
        ct.setCashier(cashier);
        ct.setTransactionType(type);
        ct.setAmount(amount);
        ct.setCurrencyCode("USD");
        return ct;
    }
}
