package com.cba.deposit;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringDepositService — unit tests")
class RecurringDepositServiceTest {

    @Mock RecurringDepositProductRepository productRepository;
    @Mock RecurringDepositAccountRepository accountRepository;
    @Mock EntityManager entityManager;

    @InjectMocks RecurringDepositService service;

    private UUID productId;
    private UUID accountId;
    private RecurringDepositProduct product;
    private RecurringDepositAccount account;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        product = new RecurringDepositProduct();
        product.setId(productId);
        product.setName("Monthly RD");
        product.setShortName("RD01");
        product.setCurrencyCode("USD");
        product.setNominalAnnualInterestRate(new BigDecimal("7.00"));
        product.setMandatoryRecommendedDepositAmount(new BigDecimal("500.00"));
        product.setMinDepositTerm(12);
        product.setPrePenaltyApplicable(false);

        account = new RecurringDepositAccount();
        account.setId(accountId);
        account.setProduct(product);
        account.setMandatoryRecommendedDepositAmount(new BigDecimal("500.00"));
        account.setStatus(RecurringDepositAccount.Status.SUBMITTED);
    }

    @Nested
    @DisplayName("Product CRUD")
    class ProductCrud {

        @Test
        @DisplayName("listProducts returns page of products")
        void listProducts_returnsPage() {
            when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

            Page<RecurringDepositProduct> result = service.listProducts(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getProduct returns product when found")
        void getProduct_found() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            assertThat(service.getProduct(productId).getShortName()).isEqualTo("RD01");
        }

        @Test
        @DisplayName("getProduct throws when not found")
        void getProduct_notFound_throws() {
            when(productRepository.findById(productId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getProduct(productId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createProduct saves new product")
        void createProduct_success() {
            when(productRepository.save(any())).thenReturn(product);

            RecurringDepositService.CreateRdProductRequest req = new RecurringDepositService.CreateRdProductRequest(
                "Monthly RD", "RD01", null, "USD",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"), new BigDecimal("10000.00"),
                new BigDecimal("7.00"), 12, 36, false, null
            );
            assertThat(service.createProduct(req)).isNotNull();
        }

        @Test
        @DisplayName("updateProduct updates and saves")
        void updateProduct_success() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            RecurringDepositService.CreateRdProductRequest req = new RecurringDepositService.CreateRdProductRequest(
                "Updated RD", "RD01", null, "USD",
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"), null,
                new BigDecimal("8.00"), 6, null, false, null
            );
            assertThat(service.updateProduct(productId, req)).isNotNull();
        }

        @Test
        @DisplayName("deleteProduct removes product")
        void deleteProduct_success() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatCode(() -> service.deleteProduct(productId)).doesNotThrowAnyException();
            verify(productRepository).delete(product);
        }
    }

    @Nested
    @DisplayName("Account Lifecycle")
    class AccountLifecycle {

        @Test
        @DisplayName("listAccounts with no customerId returns all")
        void listAccounts_noFilter() {
            when(accountRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
            assertThat(service.listAccounts(null, Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listAccounts with customerId filters by customer")
        void listAccounts_withCustomerId() {
            UUID customerId = UUID.randomUUID();
            when(accountRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
            assertThat(service.listAccounts(customerId, Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getAccount returns account when found")
        void getAccount_found() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            assertThat(service.getAccount(accountId).getStatus()).isEqualTo(RecurringDepositAccount.Status.SUBMITTED);
        }

        @Test
        @DisplayName("getAccount throws when not found")
        void getAccount_notFound_throws() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getAccount(accountId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("submitApplication creates SUBMITTED account")
        void submitApplication_success() {
            UUID customerId = UUID.randomUUID();
            Customer customer = new Customer();
            customer.setId(customerId);

            when(entityManager.find(Customer.class, customerId)).thenReturn(customer);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(accountRepository.save(any())).thenReturn(account);

            RecurringDepositService.SubmitRdRequest req = new RecurringDepositService.SubmitRdRequest(
                customerId, productId, new BigDecimal("500.00"),
                12, "MONTHS", LocalDate.now().plusMonths(1)
            );
            RecurringDepositAccount result = service.submitApplication(req);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.SUBMITTED);
        }

        @Test
        @DisplayName("submitApplication throws when customer not found")
        void submitApplication_customerNotFound_throws() {
            UUID customerId = UUID.randomUUID();
            when(entityManager.find(Customer.class, customerId)).thenReturn(null);

            RecurringDepositService.SubmitRdRequest req = new RecurringDepositService.SubmitRdRequest(
                customerId, productId, new BigDecimal("500.00"), 12, "MONTHS", null
            );
            assertThatThrownBy(() -> service.submitApplication(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("approveAccount sets APPROVED status")
        void approveAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            RecurringDepositAccount result = service.approveAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.APPROVED);
        }

        @Test
        @DisplayName("activateAccount sets ACTIVE status")
        void activateAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            RecurringDepositAccount result = service.activateAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.ACTIVE);
        }

        @Test
        @DisplayName("rejectAccount sets REJECTED status")
        void rejectAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            RecurringDepositAccount result = service.rejectAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.REJECTED);
        }

        @Test
        @DisplayName("matureAccount sets MATURED status")
        void matureAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            RecurringDepositAccount result = service.matureAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.MATURED);
        }

        @Test
        @DisplayName("prematureClose with no penalty sets PREMATURE_CLOSURE")
        void prematureClose_noPenalty() {
            product.setPrePenaltyApplicable(false);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            RecurringDepositAccount result = service.prematureClose(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.PREMATURE_CLOSURE);
        }

        @Test
        @DisplayName("prematureClose skips penalty when depositAmount is null")
        void prematureClose_nullDepositAmount() {
            product.setPrePenaltyApplicable(true);
            product.setPrePenaltyInterest(new BigDecimal("10.00"));
            account.setDepositAmount(null); // no deposit amount set
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RecurringDepositAccount result = service.prematureClose(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.PREMATURE_CLOSURE);
            assertThat(result.getMaturityAmount()).isNull();
        }

        @Test
        @DisplayName("prematureClose with penalty deducts from deposit amount")
        void prematureClose_withPenalty() {
            product.setPrePenaltyApplicable(true);
            product.setPrePenaltyInterest(new BigDecimal("10.00"));
            account.setDepositAmount(new BigDecimal("6000.00")); // penalty = 600, maturity = 5400
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RecurringDepositAccount result = service.prematureClose(accountId);
            assertThat(result.getStatus()).isEqualTo(RecurringDepositAccount.Status.PREMATURE_CLOSURE);
            assertThat(result.getMaturityAmount()).isEqualByComparingTo(new BigDecimal("5400.00"));
        }
    }
}
