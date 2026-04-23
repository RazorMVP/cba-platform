package com.cba.product;

import com.cba.audit.AuditLogService;
import com.cba.accounting.GlAccountRepository;
import com.cba.charge.ChargeDefinitionRepository;
import com.cba.system.FundRepository;
import com.cba.common.exception.CbaException;
import com.cba.product.dto.DepositProductRequest;
import com.cba.product.dto.DepositProductResponse;
import com.cba.product.dto.LoanProductRequest;
import com.cba.product.dto.LoanProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService — unit tests")
class ProductServiceTest {

    @Mock LoanProductRepository loanProductRepository;
    @Mock DepositProductRepository depositProductRepository;
    @Mock GlAccountRepository glAccountRepository;
    @Mock ChargeDefinitionRepository chargeDefinitionRepository;
    @Mock FundRepository fundRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks ProductService productService;

    private UUID loanProductId;
    private UUID depositProductId;
    private LoanProduct loanProduct;
    private DepositProduct depositProduct;

    @BeforeEach
    void setUp() {
        loanProductId = UUID.randomUUID();
        depositProductId = UUID.randomUUID();

        loanProduct = new LoanProduct();
        loanProduct.setId(loanProductId);
        loanProduct.setName("Personal Loan");
        loanProduct.setShortName("PERS");
        loanProduct.setMinPrincipal(new BigDecimal("1000.00"));
        loanProduct.setMaxPrincipal(new BigDecimal("50000.00"));
        loanProduct.setMinInterestRate(new BigDecimal("5.00"));
        loanProduct.setMaxInterestRate(new BigDecimal("25.00"));
        loanProduct.setDefaultInterestRate(new BigDecimal("12.00"));
        loanProduct.setMinTermMonths(6);
        loanProduct.setMaxTermMonths(60);
        loanProduct.setActive(true);

        depositProduct = new DepositProduct();
        depositProduct.setId(depositProductId);
        depositProduct.setName("Standard Savings");
        depositProduct.setShortName("SAVI");
        depositProduct.setActive(true);
        depositProduct.setMinimumBalance(BigDecimal.ZERO);
    }

    // ── Loan Products ─────────────────────────────────────────────

    @Nested
    @DisplayName("getAllLoanProducts")
    class GetAllLoanProducts {

        @Test
        @DisplayName("returns all when activeOnly=false")
        void getAllLoanProducts_all() {
            when(loanProductRepository.findAll()).thenReturn(List.of(loanProduct));
            List<LoanProductResponse> result = productService.getAllLoanProducts(false);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns only active when activeOnly=true")
        void getAllLoanProducts_activeOnly() {
            when(loanProductRepository.findByActiveTrue()).thenReturn(List.of(loanProduct));
            List<LoanProductResponse> result = productService.getAllLoanProducts(true);
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getLoanProduct")
    class GetLoanProduct {

        @Test
        @DisplayName("returns product when found")
        void getLoanProduct_found() {
            when(loanProductRepository.findById(loanProductId)).thenReturn(Optional.of(loanProduct));
            LoanProductResponse resp = productService.getLoanProduct(loanProductId);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("throws 404 when not found")
        void getLoanProduct_notFound() {
            when(loanProductRepository.findById(loanProductId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> productService.getLoanProduct(loanProductId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("createLoanProduct")
    class CreateLoanProduct {

        @Test
        @DisplayName("creates product with valid ranges")
        void createLoanProduct_valid() {
            LoanProductRequest req = buildValidLoanRequest();
            when(loanProductRepository.save(any())).thenReturn(loanProduct);

            LoanProductResponse resp = productService.createLoanProduct(req);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("LoanProduct"), any(), eq("CREATE"), isNull(), any());
        }

        @Test
        @DisplayName("throws when minPrincipal > maxPrincipal")
        void createLoanProduct_invalidPrincipalRange() {
            LoanProductRequest req = buildLoanRequest(
                new BigDecimal("50000"), new BigDecimal("1000"),
                new BigDecimal("5"), new BigDecimal("25"), new BigDecimal("12"),
                6, 60
            );
            assertThatThrownBy(() -> productService.createLoanProduct(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("minPrincipal");
        }

        @Test
        @DisplayName("throws when defaultInterestRate outside range")
        void createLoanProduct_defaultRateOutOfRange() {
            LoanProductRequest req = buildLoanRequest(
                new BigDecimal("1000"), new BigDecimal("50000"),
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("15"),
                6, 60
            );
            assertThatThrownBy(() -> productService.createLoanProduct(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("defaultInterestRate");
        }

        @Test
        @DisplayName("throws when minTermMonths > maxTermMonths")
        void createLoanProduct_invalidTermRange() {
            LoanProductRequest req = buildLoanRequest(
                new BigDecimal("1000"), new BigDecimal("50000"),
                new BigDecimal("5"), new BigDecimal("25"), new BigDecimal("12"),
                60, 6
            );
            assertThatThrownBy(() -> productService.createLoanProduct(req))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("minTermMonths");
        }
    }

    @Nested
    @DisplayName("updateLoanProduct")
    class UpdateLoanProduct {

        @Test
        @DisplayName("updates existing product")
        void updateLoanProduct_valid() {
            when(loanProductRepository.findById(loanProductId)).thenReturn(Optional.of(loanProduct));
            when(loanProductRepository.save(any())).thenReturn(loanProduct);

            LoanProductResponse resp = productService.updateLoanProduct(loanProductId, buildValidLoanRequest());
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("LoanProduct"), any(), eq("UPDATE"), isNull(), any());
        }

        @Test
        @DisplayName("throws when product not found")
        void updateLoanProduct_notFound() {
            when(loanProductRepository.findById(loanProductId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> productService.updateLoanProduct(loanProductId, buildValidLoanRequest()))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("deactivateLoanProduct")
    class DeactivateLoanProduct {

        @Test
        @DisplayName("deactivates existing product")
        void deactivateLoanProduct_succeeds() {
            when(loanProductRepository.findById(loanProductId)).thenReturn(Optional.of(loanProduct));
            when(loanProductRepository.save(any())).thenReturn(loanProduct);

            productService.deactivateLoanProduct(loanProductId);
            assertThat(loanProduct.isActive()).isFalse();
        }
    }

    // ── Deposit Products ─────────────────────────────────────────

    @Nested
    @DisplayName("getAllDepositProducts")
    class GetAllDepositProducts {

        @Test
        @DisplayName("returns all deposit products")
        void getAllDepositProducts_all() {
            when(depositProductRepository.findAll()).thenReturn(List.of(depositProduct));
            List<DepositProductResponse> result = productService.getAllDepositProducts(false);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns only active deposit products")
        void getAllDepositProducts_activeOnly() {
            when(depositProductRepository.findByActiveTrue()).thenReturn(List.of(depositProduct));
            List<DepositProductResponse> result = productService.getAllDepositProducts(true);
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getDepositProduct")
    class GetDepositProduct {

        @Test
        @DisplayName("returns product when found")
        void getDepositProduct_found() {
            when(depositProductRepository.findById(depositProductId)).thenReturn(Optional.of(depositProduct));
            DepositProductResponse resp = productService.getDepositProduct(depositProductId);
            assertThat(resp).isNotNull();
        }

        @Test
        @DisplayName("throws 404 when not found")
        void getDepositProduct_notFound() {
            when(depositProductRepository.findById(depositProductId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> productService.getDepositProduct(depositProductId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("createDepositProduct")
    class CreateDepositProduct {

        @Test
        @DisplayName("creates deposit product")
        void createDepositProduct_valid() {
            DepositProductRequest req = buildValidDepositRequest();
            when(depositProductRepository.save(any())).thenReturn(depositProduct);

            DepositProductResponse resp = productService.createDepositProduct(req);
            assertThat(resp).isNotNull();
            verify(auditLogService).log(eq("DepositProduct"), any(), eq("CREATE"), isNull(), any());
        }
    }

    @Nested
    @DisplayName("updateDepositProduct")
    class UpdateDepositProduct {

        @Test
        @DisplayName("updates existing deposit product")
        void updateDepositProduct_valid() {
            when(depositProductRepository.findById(depositProductId)).thenReturn(Optional.of(depositProduct));
            when(depositProductRepository.save(any())).thenReturn(depositProduct);

            DepositProductResponse resp = productService.updateDepositProduct(depositProductId, buildValidDepositRequest());
            assertThat(resp).isNotNull();
        }
    }

    @Nested
    @DisplayName("deactivateDepositProduct")
    class DeactivateDepositProduct {

        @Test
        @DisplayName("deactivates existing deposit product")
        void deactivateDepositProduct_succeeds() {
            when(depositProductRepository.findById(depositProductId)).thenReturn(Optional.of(depositProduct));
            when(depositProductRepository.save(any())).thenReturn(depositProduct);

            productService.deactivateDepositProduct(depositProductId);
            assertThat(depositProduct.isActive()).isFalse();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private LoanProductRequest buildValidLoanRequest() {
        return buildLoanRequest(
            new BigDecimal("1000"), new BigDecimal("50000"),
            new BigDecimal("5"), new BigDecimal("25"), new BigDecimal("12"),
            6, 60
        );
    }

    private LoanProductRequest buildLoanRequest(
        BigDecimal minP, BigDecimal maxP,
        BigDecimal minR, BigDecimal maxR, BigDecimal defR,
        int minT, int maxT
    ) {
        return new LoanProductRequest(
            "Test Product", "TEST", null, "USD", null,
            minP, maxP, null, null,
            minR, maxR, defR,
            null, null, null, null, null, null,
            minT, maxT,
            12, 1, null, null,
            0, 0, 0, 0,
            null, null, null,
            null,
            null, null, null, null, null, null, null, null,
            null
        );
    }

    private DepositProductRequest buildValidDepositRequest() {
        return new DepositProductRequest(
            "Standard Savings", "SAVI", null,
            DepositAccountType.SAVINGS, "USD",
            BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("3.50"),
            null, null, null, null,
            0, null,
            false,
            false, null, null, null,
            null,
            null, null, null, null, null, null, null, null,
            null
        );
    }
}
