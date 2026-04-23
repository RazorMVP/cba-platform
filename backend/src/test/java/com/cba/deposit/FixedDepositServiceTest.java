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
@DisplayName("FixedDepositService — unit tests")
class FixedDepositServiceTest {

    @Mock FixedDepositProductRepository productRepository;
    @Mock FixedDepositAccountRepository accountRepository;
    @Mock EntityManager entityManager;

    @InjectMocks FixedDepositService service;

    private UUID productId;
    private UUID accountId;
    private FixedDepositProduct product;
    private FixedDepositAccount account;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        product = new FixedDepositProduct();
        product.setId(productId);
        product.setName("12-Month FD");
        product.setShortName("FD12");
        product.setCurrencyCode("USD");
        product.setNominalAnnualInterestRate(new BigDecimal("8.00"));
        product.setMinDepositAmount(new BigDecimal("1000.00"));
        product.setMinDepositTerm(12);
        product.setPrePenaltyApplicable(false);

        account = new FixedDepositAccount();
        account.setId(accountId);
        account.setProduct(product);
        account.setDepositAmount(new BigDecimal("5000.00"));
        account.setStatus(FixedDepositAccount.Status.SUBMITTED);
    }

    @Nested
    @DisplayName("Product CRUD")
    class ProductCrud {

        @Test
        @DisplayName("listProducts returns page of products")
        void listProducts_returnsPage() {
            when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

            Page<FixedDepositProduct> result = service.listProducts(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getProduct returns product when found")
        void getProduct_found() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            assertThat(service.getProduct(productId).getShortName()).isEqualTo("FD12");
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

            FixedDepositService.CreateFdProductRequest req = new FixedDepositService.CreateFdProductRequest(
                "12-Month FD", "FD12", null, "USD",
                new BigDecimal("1000.00"), new BigDecimal("100000.00"),
                new BigDecimal("8.00"), 12, 24, false, null
            );
            assertThat(service.createProduct(req)).isNotNull();
        }

        @Test
        @DisplayName("updateProduct updates and saves")
        void updateProduct_success() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            FixedDepositService.CreateFdProductRequest req = new FixedDepositService.CreateFdProductRequest(
                "Updated FD", "FD12", null, "USD",
                new BigDecimal("2000.00"), null,
                new BigDecimal("9.00"), 6, null, false, null
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
            assertThat(service.getAccount(accountId).getStatus()).isEqualTo(FixedDepositAccount.Status.SUBMITTED);
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

            FixedDepositService.SubmitFdRequest req = new FixedDepositService.SubmitFdRequest(
                customerId, productId, new BigDecimal("5000.00"),
                12, "MONTHS", LocalDate.now().plusMonths(1)
            );
            FixedDepositAccount result = service.submitApplication(req);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.SUBMITTED);
        }

        @Test
        @DisplayName("submitApplication throws when customer not found")
        void submitApplication_customerNotFound_throws() {
            UUID customerId = UUID.randomUUID();
            when(entityManager.find(Customer.class, customerId)).thenReturn(null);

            FixedDepositService.SubmitFdRequest req = new FixedDepositService.SubmitFdRequest(
                customerId, productId, new BigDecimal("5000.00"), 12, "MONTHS", null
            );
            assertThatThrownBy(() -> service.submitApplication(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("approveAccount sets APPROVED status")
        void approveAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            FixedDepositAccount result = service.approveAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.APPROVED);
        }

        @Test
        @DisplayName("activateAccount sets ACTIVE status")
        void activateAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            FixedDepositAccount result = service.activateAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.ACTIVE);
        }

        @Test
        @DisplayName("rejectAccount sets REJECTED status")
        void rejectAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            FixedDepositAccount result = service.rejectAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.REJECTED);
        }

        @Test
        @DisplayName("matureAccount sets MATURED status")
        void matureAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            FixedDepositAccount result = service.matureAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.MATURED);
        }

        @Test
        @DisplayName("prematureClose with no penalty sets PREMATURE_CLOSURE")
        void prematureClose_noPenalty() {
            product.setPrePenaltyApplicable(false);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            FixedDepositAccount result = service.prematureClose(accountId);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.PREMATURE_CLOSURE);
        }

        @Test
        @DisplayName("prematureClose with penalty deducts from deposit amount")
        void prematureClose_withPenalty() {
            product.setPrePenaltyApplicable(true);
            product.setPrePenaltyInterest(new BigDecimal("10.00"));
            // deposit = 5000, penalty = 500, maturity = 4500
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FixedDepositAccount result = service.prematureClose(accountId);
            assertThat(result.getStatus()).isEqualTo(FixedDepositAccount.Status.PREMATURE_CLOSURE);
            assertThat(result.getMaturityAmount()).isEqualByComparingTo(new BigDecimal("4500.00"));
        }

        @Test
        @DisplayName("prematureClose with penalty larger than deposit yields zero maturity")
        void prematureClose_penaltyExceedsDeposit() {
            product.setPrePenaltyApplicable(true);
            product.setPrePenaltyInterest(new BigDecimal("110.00")); // 110% penalty
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            FixedDepositAccount result = service.prematureClose(accountId);
            assertThat(result.getMaturityAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
