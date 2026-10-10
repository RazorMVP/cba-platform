package com.cba.charge;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.accounting.GlAccountRepository;
import com.cba.loan.Loan;
import com.cba.loan.LoanGlPosting;
import com.cba.loan.LoanPaymentSource;
import com.cba.loan.LoanStatus;
import com.cba.product.LoanProduct;
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
@DisplayName("ChargeService — unit tests")
class ChargeServiceTest {

    @Mock ChargeRepository chargeRepository;
    @Mock LoanChargeRepository loanChargeRepository;
    @Mock ClientChargeRepository clientChargeRepository;
    @Mock EntityManager entityManager;
    @Mock LoanGlPosting loanGlPosting;
    @Mock GlAccountRepository glAccountRepository;

    @InjectMocks ChargeService chargeService;

    private UUID chargeDefId;
    private UUID loanId;
    private UUID customerId;
    private ChargeDefinition chargeDef;

    @BeforeEach
    void setUp() {
        chargeDefId = UUID.randomUUID();
        loanId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        chargeDef = new ChargeDefinition();
        chargeDef.setId(chargeDefId);
        chargeDef.setName("Processing Fee");
        chargeDef.setCurrencyCode("USD");
        chargeDef.setChargeAppliesTo(ChargeDefinition.ChargeAppliesTo.LOAN);
        chargeDef.setChargeTimeType(ChargeDefinition.ChargeTimeType.DISBURSEMENT);
        chargeDef.setChargeCalculation(ChargeDefinition.ChargeCalculation.FLAT);
        chargeDef.setAmount(new BigDecimal("50.00"));
        chargeDef.setPenalty(false);
        chargeDef.setActive(true);
    }

    @Nested
    @DisplayName("Charge Definitions")
    class ChargeDefinitions {

        @Test
        @DisplayName("listCharges returns all when appliesTo is null")
        void listCharges_noFilter_returnsAll() {
            when(chargeRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(chargeDef)));

            Page<ChargeDefinition> result = chargeService.listCharges(null, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
            verify(chargeRepository).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("listCharges filters by appliesTo when provided")
        void listCharges_withFilter_callsFilteredQuery() {
            when(chargeRepository.findByChargeAppliesTo(eq(ChargeDefinition.ChargeAppliesTo.LOAN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(chargeDef)));

            Page<ChargeDefinition> result = chargeService.listCharges(
                ChargeDefinition.ChargeAppliesTo.LOAN, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
            verify(chargeRepository).findByChargeAppliesTo(
                eq(ChargeDefinition.ChargeAppliesTo.LOAN), any(Pageable.class));
        }

        @Test
        @DisplayName("getCharge returns charge when found")
        void getCharge_found() {
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.of(chargeDef));
            ChargeDefinition result = chargeService.getCharge(chargeDefId);
            assertThat(result.getName()).isEqualTo("Processing Fee");
        }

        @Test
        @DisplayName("getCharge throws when not found")
        void getCharge_notFound_throws() {
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> chargeService.getCharge(chargeDefId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createCharge saves new charge definition")
        void createCharge_success() {
            when(chargeRepository.save(any())).thenReturn(chargeDef);

            ChargeService.CreateChargeRequest req = new ChargeService.CreateChargeRequest(
                "Processing Fee", "USD",
                ChargeDefinition.ChargeAppliesTo.LOAN,
                ChargeDefinition.ChargeTimeType.DISBURSEMENT,
                ChargeDefinition.ChargeCalculation.FLAT,
                new BigDecimal("50.00"), false, true
            );
            ChargeDefinition result = chargeService.createCharge(req);
            assertThat(result.getName()).isEqualTo("Processing Fee");
            verify(chargeRepository).save(any(ChargeDefinition.class));
        }

        @Test
        @DisplayName("updateCharge updates and saves existing charge")
        void updateCharge_success() {
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.of(chargeDef));
            when(chargeRepository.save(any())).thenReturn(chargeDef);

            ChargeService.CreateChargeRequest req = new ChargeService.CreateChargeRequest(
                "Updated Fee", "USD",
                ChargeDefinition.ChargeAppliesTo.LOAN,
                ChargeDefinition.ChargeTimeType.DISBURSEMENT,
                ChargeDefinition.ChargeCalculation.FLAT,
                new BigDecimal("75.00"), false, true
            );
            ChargeDefinition result = chargeService.updateCharge(chargeDefId, req);
            assertThat(result).isNotNull();
            verify(chargeRepository).save(any(ChargeDefinition.class));
        }

        @Test
        @DisplayName("deleteCharge removes charge definition")
        void deleteCharge_success() {
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.of(chargeDef));

            assertThatCode(() -> chargeService.deleteCharge(chargeDefId))
                .doesNotThrowAnyException();
            verify(chargeRepository).delete(chargeDef);
        }
    }

    @Nested
    @DisplayName("Loan Charges")
    class LoanCharges {

        private UUID loanChargeId;
        private LoanCharge loanCharge;
        private Loan loan;

        private static final BigDecimal FIFTY = new BigDecimal("50.00");

        @BeforeEach
        void setUpLoanCharge() {
            loanChargeId = UUID.randomUUID();

            LoanProduct product = new LoanProduct();
            product.setCurrencyCode("USD");
            loan = new Loan();
            loan.setId(loanId);
            loan.setProduct(product);
            loan.setStatus(LoanStatus.ACTIVE);

            chargeDef.setChargeTimeType(ChargeDefinition.ChargeTimeType.SPECIFIED_DUE_DATE);

            loanCharge = new LoanCharge();
            loanCharge.setId(loanChargeId);
            loanCharge.setLoan(loan);
            loanCharge.setChargeDefinition(chargeDef);
            loanCharge.setName("Processing Fee");
            loanCharge.setCurrencyCode("USD");
            loanCharge.setChargeTimeType(ChargeDefinition.ChargeTimeType.SPECIFIED_DUE_DATE);
            loanCharge.setChargeCalculation(ChargeDefinition.ChargeCalculation.FLAT);
            loanCharge.setAmount(FIFTY);
            loanCharge.setAmountOutstanding(FIFTY);
        }

        private LoanCharge add(BigDecimal amount, LocalDate dueDate) {
            when(entityManager.find(Loan.class, loanId)).thenReturn(loan);
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.of(chargeDef));
            return chargeService.addLoanCharge(loanId, new ChargeService.AddChargeRequest(chargeDefId, amount, dueDate));
        }

        @Test
        @DisplayName("getLoanCharges returns page for loan")
        void getLoanCharges_returnsPage() {
            when(loanChargeRepository.findByLoanId(eq(loanId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(loanCharge)));

            Page<LoanCharge> result = chargeService.getLoanCharges(loanId, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("a fee due today is income now: DR fees receivable / CR fee income")
        void addLoanCharge_dueToday_recognisesIncome() {
            when(loanChargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoanCharge result = add(FIFTY, LocalDate.now());

            assertThat(result.getIncomeRecognizedOn()).isEqualTo(LocalDate.now());
            verify(loanGlPosting).postChargeRecognition(eq(loan), any(LoanCharge.class), eq(FIFTY), eq(LocalDate.now()));
        }

        @Test
        @DisplayName("a fee due later is not income yet: nothing posted")
        void addLoanCharge_futureFee_notRecognised() {
            when(loanChargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoanCharge result = add(FIFTY, LocalDate.now().plusDays(10));

            assertThat(result.getIncomeRecognizedOn()).isNull();
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("a penalty is income when charged, whatever its due date")
        void addLoanCharge_penalty_recognisedNow() {
            chargeDef.setPenalty(true);
            when(loanChargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoanCharge result = add(FIFTY, LocalDate.now().plusDays(10));

            assertThat(result.getIncomeRecognizedOn()).isEqualTo(LocalDate.now());
            verify(loanGlPosting).postChargeRecognition(eq(loan), any(LoanCharge.class), eq(FIFTY), any());
        }

        @Test
        @DisplayName("a disbursement fee is rejected: it belongs in the effective interest rate")
        void addLoanCharge_originationFee_rejected() {
            chargeDef.setChargeTimeType(ChargeDefinition.ChargeTimeType.DISBURSEMENT);

            assertThatThrownBy(() -> add(FIFTY, null))
                .isInstanceOf(CbaException.class).hasMessageContaining("effective interest rate");
            verify(loanChargeRepository, never()).save(any());
        }

        @Test
        @DisplayName("a charge in another currency than the loan is rejected")
        void addLoanCharge_currencyMismatch_rejected() {
            chargeDef.setCurrencyCode("KES");

            assertThatThrownBy(() -> add(FIFTY, null))
                .isInstanceOf(CbaException.class).hasMessageContaining("KES");
        }

        @Test
        @DisplayName("addLoanCharge throws when loan not found")
        void addLoanCharge_loanNotFound_throws() {
            when(entityManager.find(Loan.class, loanId)).thenReturn(null);

            ChargeService.AddChargeRequest req = new ChargeService.AddChargeRequest(chargeDefId, FIFTY, LocalDate.now());
            assertThatThrownBy(() -> chargeService.addLoanCharge(loanId, req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("paying a recognised charge collects it from the linked account by default")
        void payLoanCharge_success() {
            loanCharge.setIncomeRecognizedOn(LocalDate.now());
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));
            when(loanChargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoanCharge result = chargeService.payLoanCharge(loanId, loanChargeId, null);

            assertThat(result.isPaid()).isTrue();
            assertThat(result.getAmountPaid()).isEqualByComparingTo(FIFTY);
            assertThat(result.getAmountOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(loanGlPosting).postChargePayment(eq(loan), eq(loanCharge),
                eq(new LoanPaymentSource(LoanPaymentSource.Method.ACCOUNT, null, null)), eq(FIFTY), any());
        }

        @Test
        @DisplayName("a fee not yet due cannot be paid")
        void payLoanCharge_notDue_throws() {
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));

            assertThatThrownBy(() -> chargeService.payLoanCharge(loanId, loanChargeId, null))
                .isInstanceOf(CbaException.class).hasMessageContaining("cannot be paid before");
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("payLoanCharge throws when charge not found for loan")
        void payLoanCharge_notFound_throws() {
            UUID wrongLoanId = UUID.randomUUID();
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));

            assertThatThrownBy(() -> chargeService.payLoanCharge(wrongLoanId, loanChargeId, null))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("waiving a recognised charge reverses its income")
        void waiveLoanCharge_recognised_reversesIncome() {
            loanCharge.setIncomeRecognizedOn(LocalDate.now());
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));
            when(loanChargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            LoanCharge result = chargeService.waiveLoanCharge(loanId, loanChargeId);

            assertThat(result.isWaived()).isTrue();
            assertThat(result.getAmountWaived()).isEqualByComparingTo(FIFTY);
            assertThat(result.getAmountOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(loanGlPosting).postChargeWaiver(eq(loan), eq(loanCharge), eq(FIFTY), any());
        }

        @Test
        @DisplayName("waiving a charge that was never income posts nothing")
        void waiveLoanCharge_unrecognised_noPosting() {
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));
            when(loanChargeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(chargeService.waiveLoanCharge(loanId, loanChargeId).isWaived()).isTrue();
            verifyNoInteractions(loanGlPosting);
        }

        @Test
        @DisplayName("deleteLoanCharge removes a charge with nothing posted")
        void deleteLoanCharge_success() {
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));

            assertThatCode(() -> chargeService.deleteLoanCharge(loanId, loanChargeId))
                .doesNotThrowAnyException();
            verify(loanChargeRepository).delete(loanCharge);
        }

        @Test
        @DisplayName("a charge already in the ledger cannot be deleted")
        void deleteLoanCharge_posted_rejected() {
            loanCharge.setIncomeRecognizedOn(LocalDate.now());
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));

            assertThatThrownBy(() -> chargeService.deleteLoanCharge(loanId, loanChargeId))
                .isInstanceOf(CbaException.class).hasMessageContaining("waive it instead");
            verify(loanChargeRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Client Charges")
    class ClientCharges {

        private UUID clientChargeId;
        private ClientCharge clientCharge;
        private Customer customer;

        @BeforeEach
        void setUpClientCharge() {
            clientChargeId = UUID.randomUUID();

            customer = new Customer();
            customer.setId(customerId);

            clientCharge = new ClientCharge();
            clientCharge.setId(clientChargeId);
            clientCharge.setCustomer(customer);
            clientCharge.setChargeDefinition(chargeDef);
            clientCharge.setName("Annual Fee");
            clientCharge.setCurrencyCode("USD");
            clientCharge.setChargeTimeType(ChargeDefinition.ChargeTimeType.ANNUAL_FEE);
            clientCharge.setChargeCalculation(ChargeDefinition.ChargeCalculation.FLAT);
            clientCharge.setAmount(new BigDecimal("25.00"));
            clientCharge.setAmountOutstanding(new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("getClientCharges returns page for customer")
        void getClientCharges_returnsPage() {
            when(clientChargeRepository.findByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(clientCharge)));

            Page<ClientCharge> result = chargeService.getClientCharges(customerId, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("addClientCharge creates client charge when customer exists")
        void addClientCharge_success() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(customer);
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.of(chargeDef));
            when(clientChargeRepository.save(any())).thenReturn(clientCharge);

            ChargeService.AddChargeRequest req = new ChargeService.AddChargeRequest(
                chargeDefId, new BigDecimal("25.00"), LocalDate.now());
            ClientCharge result = chargeService.addClientCharge(customerId, req);
            assertThat(result).isNotNull();
            verify(clientChargeRepository).save(any(ClientCharge.class));
        }

        @Test
        @DisplayName("addClientCharge throws when customer not found")
        void addClientCharge_customerNotFound_throws() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(null);

            ChargeService.AddChargeRequest req = new ChargeService.AddChargeRequest(
                chargeDefId, new BigDecimal("25.00"), LocalDate.now());
            assertThatThrownBy(() -> chargeService.addClientCharge(customerId, req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("waiveClientCharge marks client charge as waived")
        void waiveClientCharge_success() {
            when(clientChargeRepository.findById(clientChargeId)).thenReturn(Optional.of(clientCharge));
            when(clientChargeRepository.save(any())).thenReturn(clientCharge);

            ClientCharge result = chargeService.waiveClientCharge(customerId, clientChargeId);
            assertThat(result.isWaived()).isTrue();
            assertThat(result.getAmountOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("waiveClientCharge throws when charge belongs to different customer")
        void waiveClientCharge_wrongCustomer_throws() {
            UUID wrongCustomerId = UUID.randomUUID();
            when(clientChargeRepository.findById(clientChargeId)).thenReturn(Optional.of(clientCharge));

            assertThatThrownBy(() -> chargeService.waiveClientCharge(wrongCustomerId, clientChargeId))
                .isInstanceOf(CbaException.class);
        }
    }
}
