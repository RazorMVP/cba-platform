package com.cba.share;

import com.cba.audit.AuditLogService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShareService — unit tests")
class ShareServiceTest {

    @Mock ShareProductRepository productRepository;
    @Mock ShareAccountRepository accountRepository;
    @Mock ShareAccountTransactionRepository transactionRepository;
    @Mock EntityManager entityManager;
    @Mock AuditLogService auditLogService;

    @InjectMocks ShareService shareService;

    private UUID productId;
    private UUID accountId;
    private ShareProduct product;
    private ShareAccount account;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        product = new ShareProduct();
        product.setId(productId);
        product.setName("Employee Shares");
        product.setShortName("EMPL");
        product.setCurrencyCode("USD");
        product.setUnitPrice(new BigDecimal("10.00"));
        product.setSharesIssued(0L);

        account = new ShareAccount();
        account.setId(accountId);
        account.setProduct(product);
        account.setStatus(ShareAccount.Status.SUBMITTED);
        account.setRequestedShares(100L);
        account.setApprovedShares(100L);
        account.setUnitPrice(new BigDecimal("10.00"));
        account.setTotalSharesHeld(0L);
    }

    @Nested
    @DisplayName("Share Products")
    class ShareProducts {

        @Test
        @DisplayName("listProducts returns page of products")
        void listProducts_returnsPage() {
            when(productRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

            Page<ShareProduct> result = shareService.listProducts(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getProduct returns product when found")
        void getProduct_found() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            ShareProduct result = shareService.getProduct(productId);
            assertThat(result.getShortName()).isEqualTo("EMPL");
        }

        @Test
        @DisplayName("getProduct throws when not found")
        void getProduct_notFound_throws() {
            when(productRepository.findById(productId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> shareService.getProduct(productId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createProduct saves new product")
        void createProduct_success() {
            when(productRepository.existsByShortName("EMPL")).thenReturn(false);
            when(productRepository.save(any())).thenReturn(product);

            ShareService.CreateShareProductRequest req = new ShareService.CreateShareProductRequest(
                "Employee Shares", "EMPL", null, "USD",
                10000L, new BigDecimal("10.00"), 1L, 10L, 10000L,
                null, null, null, null, false
            );
            ShareProduct result = shareService.createProduct(req);
            assertThat(result.getShortName()).isEqualTo("EMPL");
            verify(auditLogService).log(eq("ShareProduct"), any(), eq("CREATE"), isNull(), any());
        }

        @Test
        @DisplayName("createProduct throws when short name already exists")
        void createProduct_duplicateShortName_throws() {
            when(productRepository.existsByShortName("EMPL")).thenReturn(true);

            ShareService.CreateShareProductRequest req = new ShareService.CreateShareProductRequest(
                "Employee Shares", "EMPL", null, "USD",
                10000L, new BigDecimal("10.00"), 1L, 10L, 10000L,
                null, null, null, null, false
            );
            assertThatThrownBy(() -> shareService.createProduct(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updateProduct updates and saves product")
        void updateProduct_success() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.save(any())).thenReturn(product);

            // Keep same shortName so existsByShortName is not called
            ShareService.CreateShareProductRequest req = new ShareService.CreateShareProductRequest(
                "Updated Shares", "EMPL", null, "USD",
                20000L, new BigDecimal("12.00"), 1L, 10L, 20000L,
                null, null, null, null, false
            );
            ShareProduct result = shareService.updateProduct(productId, req);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("deleteProduct removes product")
        void deleteProduct_success() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            assertThatCode(() -> shareService.deleteProduct(productId))
                .doesNotThrowAnyException();
            verify(productRepository).delete(product);
        }
    }

    @Nested
    @DisplayName("Share Accounts")
    class ShareAccounts {

        @Test
        @DisplayName("applyForShares creates SUBMITTED account")
        void applyForShares_success() {
            UUID customerId = UUID.randomUUID();
            Customer customer = new Customer();
            customer.setId(customerId);

            when(entityManager.find(Customer.class, customerId)).thenReturn(customer);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(accountRepository.save(any())).thenReturn(account);

            ShareService.ApplySharesRequest req = new ShareService.ApplySharesRequest(customerId, productId, 100L);
            ShareAccount result = shareService.applyForShares(req);
            assertThat(result.getStatus()).isEqualTo(ShareAccount.Status.SUBMITTED);
        }

        @Test
        @DisplayName("applyForShares throws when customer not found")
        void applyForShares_customerNotFound_throws() {
            UUID customerId = UUID.randomUUID();
            when(entityManager.find(Customer.class, customerId)).thenReturn(null);

            ShareService.ApplySharesRequest req = new ShareService.ApplySharesRequest(customerId, productId, 100L);
            assertThatThrownBy(() -> shareService.applyForShares(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("approveAccount sets APPROVED status")
        void approveAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            ShareAccount result = shareService.approveAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(ShareAccount.Status.APPROVED);
        }

        @Test
        @DisplayName("activateAccount sets ACTIVE and updates product shares issued")
        void activateAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);
            when(productRepository.save(any())).thenReturn(product);

            ShareAccount result = shareService.activateAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(ShareAccount.Status.ACTIVE);
        }

        @Test
        @DisplayName("rejectAccount sets REJECTED status")
        void rejectAccount_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            ShareAccount result = shareService.rejectAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(ShareAccount.Status.REJECTED);
        }

        @Test
        @DisplayName("closeAccount sets CLOSED status")
        void closeAccount_success() {
            account.setStatus(ShareAccount.Status.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(accountRepository.save(any())).thenReturn(account);

            ShareAccount result = shareService.closeAccount(accountId);
            assertThat(result.getStatus()).isEqualTo(ShareAccount.Status.CLOSED);
        }
    }

    @Nested
    @DisplayName("Share Transactions")
    class ShareTransactions {

        @Test
        @DisplayName("purchaseShares creates PURCHASE transaction")
        void purchaseShares_success() {
            account.setTotalSharesHeld(50L);
            ShareAccountTransaction tx = new ShareAccountTransaction();
            tx.setId(UUID.randomUUID());
            tx.setShareAccount(account);
            tx.setTransactionType(ShareAccountTransaction.TransactionType.PURCHASE);
            tx.setNumberOfShares(10L);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.save(any())).thenReturn(account);

            ShareService.ShareTransactionRequest req = new ShareService.ShareTransactionRequest(
                10L, new BigDecimal("10.00"), LocalDate.now()
            );
            ShareAccountTransaction result = shareService.purchaseShares(accountId, req);
            assertThat(result.getTransactionType()).isEqualTo(ShareAccountTransaction.TransactionType.PURCHASE);
        }

        @Test
        @DisplayName("redeemShares creates REDEEM transaction")
        void redeemShares_success() {
            account.setTotalSharesHeld(100L);
            ShareAccountTransaction tx = new ShareAccountTransaction();
            tx.setId(UUID.randomUUID());
            tx.setTransactionType(ShareAccountTransaction.TransactionType.REDEEM);
            tx.setNumberOfShares(20L);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(transactionRepository.save(any())).thenReturn(tx);
            when(accountRepository.save(any())).thenReturn(account);

            ShareService.ShareTransactionRequest req = new ShareService.ShareTransactionRequest(
                20L, null, null
            );
            ShareAccountTransaction result = shareService.redeemShares(accountId, req);
            assertThat(result.getTransactionType()).isEqualTo(ShareAccountTransaction.TransactionType.REDEEM);
        }

        @Test
        @DisplayName("redeemShares throws when insufficient shares")
        void redeemShares_insufficient_throws() {
            account.setTotalSharesHeld(5L);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            ShareService.ShareTransactionRequest req = new ShareService.ShareTransactionRequest(
                10L, null, null
            );
            assertThatThrownBy(() -> shareService.redeemShares(accountId, req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Not enough shares");
        }

        @Test
        @DisplayName("getTransactions returns page for account")
        void getTransactions_returnsPage() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
            when(transactionRepository.findByShareAccountId(eq(accountId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            Page<ShareAccountTransaction> result = shareService.getTransactions(accountId, Pageable.unpaged());
            assertThat(result).isNotNull();
        }
    }
}
