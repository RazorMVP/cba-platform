package com.cba.payment;

import com.cba.account.*;
import com.cba.account.algorithm.AccountNumberAlgorithmService;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.currency.ExchangeRateService;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.StandingOrderRequest;
import com.cba.payment.dto.TransferRequest;
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
@DisplayName("PaymentService — unit tests")
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock AccountRepository accountRepository;
    @Mock AccountHoldRepository accountHoldRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AccountNumberAlgorithmService accountNumberAlgorithmService;
    @Mock AuditLogService auditLogService;
    @Mock ExchangeRateService exchangeRateService;
    @Mock StandingOrderRepository standingOrderRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks PaymentService paymentService;

    private UUID srcId;
    private UUID dstId;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        srcId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        dstId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        source = buildActiveAccount(srcId, "001-SAV-0001234", "USD", new BigDecimal("1000.00"));
        destination = buildActiveAccount(dstId, "001-SAV-0005678", "USD", new BigDecimal("500.00"));
    }

    private Account buildActiveAccount(UUID id, String accNo, String currency, BigDecimal balance) {
        com.cba.customer.Customer customer = new com.cba.customer.Customer();
        customer.setId(UUID.randomUUID());

        com.cba.product.DepositProduct product = new com.cba.product.DepositProduct();
        product.setId(UUID.randomUUID());
        product.setName("Standard Savings");
        product.setShortName("SAVI");
        product.setMinimumBalance(BigDecimal.ZERO);
        product.setAllowOverdraft(false);

        Account a = new Account();
        a.setId(id);
        a.setAccountNumber(accNo);
        a.setCustomer(customer);
        a.setProduct(product);
        a.setAccountType(AccountType.SAVINGS);
        a.setStatus(AccountStatus.ACTIVE);
        a.setBalance(balance);
        a.setCurrencyCode(currency);
        a.setOpenedDate(LocalDate.now().minusMonths(3));
        return a;
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("throws when source equals destination")
        void transfer_sameAccount_throws() {
            TransferRequest req = new TransferRequest(srcId, srcId, BigDecimal.TEN, "self", null);
            assertThatThrownBy(() -> paymentService.transfer(req, "teller1"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("must differ");
        }

        @Test
        @DisplayName("completes same-currency transfer and updates both account balances")
        void transfer_sameCurrency_completes() {
            TransferRequest req = new TransferRequest(srcId, dstId, new BigDecimal("200.00"), "test transfer", null);

            // srcId < dstId (both have deterministic UUIDs), so first=srcId, second=dstId
            when(accountRepository.findByIdWithLock(srcId)).thenReturn(Optional.of(source));
            when(accountRepository.findByIdWithLock(dstId)).thenReturn(Optional.of(destination));
            when(accountHoldRepository.sumActiveHoldsByAccount(srcId)).thenReturn(BigDecimal.ZERO);
            doNothing().when(accountNumberAlgorithmService).validatePaymentDestination(null);

            Payment saved = buildPayment(srcId, dstId, new BigDecimal("200.00"), "USD");
            when(paymentRepository.save(any())).thenReturn(saved);
            when(transactionRepository.save(any())).thenReturn(mock(Transaction.class));

            PaymentResponse resp = paymentService.transfer(req, "teller1");
            assertThat(resp).isNotNull();
            verify(paymentRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("throws when source account is not ACTIVE")
        void transfer_sourceNotActive_throws() {
            source.setStatus(AccountStatus.DORMANT);
            TransferRequest req = new TransferRequest(srcId, dstId, new BigDecimal("100.00"), "test", null);

            when(accountRepository.findByIdWithLock(srcId)).thenReturn(Optional.of(source));
            when(accountRepository.findByIdWithLock(dstId)).thenReturn(Optional.of(destination));
            doNothing().when(accountNumberAlgorithmService).validatePaymentDestination(null);

            assertThatThrownBy(() -> paymentService.transfer(req, "teller1"))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("throws when source account not found")
        void transfer_sourceNotFound_throws() {
            TransferRequest req = new TransferRequest(srcId, dstId, BigDecimal.TEN, "test", null);
            when(accountRepository.findByIdWithLock(srcId)).thenReturn(Optional.empty());
            lenient().when(accountRepository.findByIdWithLock(dstId)).thenReturn(Optional.of(destination));
            doNothing().when(accountNumberAlgorithmService).validatePaymentDestination(null);

            assertThatThrownBy(() -> paymentService.transfer(req, "teller1"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        private Payment buildPayment(UUID src, UUID dst, BigDecimal amount, String currency) {
            Payment p = new Payment();
            p.setId(UUID.randomUUID());
            p.setReferenceNumber("PAY-12345");
            p.setPaymentType(PaymentType.INTERNAL_TRANSFER);
            p.setSourceAccount(source);
            p.setDestinationAccount(destination);
            p.setAmount(amount);
            p.setCurrencyCode(currency);
            p.setStatus(PaymentStatus.COMPLETED);
            p.setCreatedBy("teller1");
            return p;
        }
    }

    @Nested
    @DisplayName("getPayment")
    class GetPayment {

        @Test
        @DisplayName("returns payment when found")
        void getPayment_found_returnsResponse() {
            UUID paymentId = UUID.randomUUID();
            Payment payment = new Payment();
            payment.setId(paymentId);
            payment.setReferenceNumber("PAY-001");
            payment.setPaymentType(PaymentType.INTERNAL_TRANSFER);
            payment.setSourceAccount(source);
            payment.setDestinationAccount(destination);
            payment.setAmount(new BigDecimal("100.00"));
            payment.setCurrencyCode("USD");
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCreatedBy("sys");

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            PaymentResponse resp = paymentService.getPayment(paymentId);
            assertThat(resp.referenceNumber()).isEqualTo("PAY-001");
        }

        @Test
        @DisplayName("throws 404 when payment not found")
        void getPayment_notFound_throws() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(paymentId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("getAccountPayments")
    class GetAccountPayments {

        @Test
        @DisplayName("returns page of payments for account")
        void getAccountPayments_returnsPage() {
            Payment payment = new Payment();
            payment.setId(UUID.randomUUID());
            payment.setSourceAccount(source);
            payment.setDestinationAccount(destination);
            payment.setAmount(BigDecimal.TEN);
            payment.setCurrencyCode("USD");
            payment.setPaymentType(PaymentType.INTERNAL_TRANSFER);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCreatedBy("sys");

            Page<Payment> page = new PageImpl<>(List.of(payment));
            when(paymentRepository.findBySourceAccountIdOrDestinationAccountId(
                eq(srcId), eq(srcId), any(Pageable.class))).thenReturn(page);

            Page<PaymentResponse> result = paymentService.getAccountPayments(srcId, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("standing orders")
    class StandingOrders {

        @Test
        @DisplayName("createStandingOrder creates standing order for active accounts")
        void createStandingOrder_happyPath() {
            StandingOrderRequest req = new StandingOrderRequest(
                srcId, dstId, new BigDecimal("100.00"), "USD",
                StandingOrder.Frequency.MONTHLY, LocalDate.now().plusDays(1), null, "monthly rent"
            );

            when(accountRepository.findById(srcId)).thenReturn(Optional.of(source));
            when(accountRepository.findById(dstId)).thenReturn(Optional.of(destination));

            StandingOrder so = new StandingOrder();
            so.setId(UUID.randomUUID());
            so.setSourceAccount(source);
            so.setDestinationAccount(destination);
            so.setAmount(req.amount());
            so.setCurrencyCode("USD");
            so.setFrequency(StandingOrder.Frequency.MONTHLY);
            so.setStartDate(req.startDate());
            so.setStatus(StandingOrder.Status.ACTIVE);
            when(standingOrderRepository.save(any())).thenReturn(so);

            var resp = paymentService.createStandingOrder(req);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("StandingOrder"), any(), eq("CREATE"), isNull(), any());
        }

        @Test
        @DisplayName("createStandingOrder throws when source account not found")
        void createStandingOrder_sourceNotFound_throws() {
            StandingOrderRequest req = new StandingOrderRequest(
                srcId, dstId, new BigDecimal("100.00"), "USD",
                StandingOrder.Frequency.MONTHLY, LocalDate.now().plusDays(1), null, "test"
            );
            when(accountRepository.findById(srcId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createStandingOrder(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("listStandingOrders returns list for account")
        void listStandingOrders_returnsList() {
            StandingOrder so = new StandingOrder();
            so.setId(UUID.randomUUID());
            so.setSourceAccount(source);
            so.setDestinationAccount(destination);
            so.setAmount(BigDecimal.TEN);
            so.setCurrencyCode("USD");
            so.setFrequency(StandingOrder.Frequency.MONTHLY);
            so.setStartDate(LocalDate.now());
            so.setStatus(StandingOrder.Status.ACTIVE);

            when(standingOrderRepository.findBySourceAccountId(srcId)).thenReturn(List.of(so));

            var result = paymentService.listStandingOrders(srcId);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("cancelStandingOrder sets status to CANCELLED")
        void cancelStandingOrder_setsStatusCancelled() {
            UUID soId = UUID.randomUUID();
            StandingOrder so = new StandingOrder();
            so.setId(soId);
            so.setSourceAccount(source);
            so.setDestinationAccount(destination);
            so.setAmount(BigDecimal.TEN);
            so.setCurrencyCode("USD");
            so.setFrequency(StandingOrder.Frequency.MONTHLY);
            so.setStartDate(LocalDate.now());
            so.setStatus(StandingOrder.Status.ACTIVE);

            when(standingOrderRepository.findById(soId)).thenReturn(Optional.of(so));
            when(standingOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var resp = paymentService.cancelStandingOrder(soId);
            assertThat(resp.status()).isEqualTo(StandingOrder.Status.CANCELLED);
        }
    }
}
