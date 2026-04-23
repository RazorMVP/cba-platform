package com.cba.charge;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.loan.Loan;
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

        @BeforeEach
        void setUpLoanCharge() {
            loanChargeId = UUID.randomUUID();

            loan = new Loan();
            loan.setId(loanId);

            loanCharge = new LoanCharge();
            loanCharge.setId(loanChargeId);
            loanCharge.setLoan(loan);
            loanCharge.setChargeDefinition(chargeDef);
            loanCharge.setName("Processing Fee");
            loanCharge.setCurrencyCode("USD");
            loanCharge.setChargeTimeType(ChargeDefinition.ChargeTimeType.DISBURSEMENT);
            loanCharge.setChargeCalculation(ChargeDefinition.ChargeCalculation.FLAT);
            loanCharge.setAmount(new BigDecimal("50.00"));
            loanCharge.setAmountOutstanding(new BigDecimal("50.00"));
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
        @DisplayName("addLoanCharge creates loan charge when loan exists")
        void addLoanCharge_success() {
            when(entityManager.find(Loan.class, loanId)).thenReturn(loan);
            when(chargeRepository.findById(chargeDefId)).thenReturn(Optional.of(chargeDef));
            when(loanChargeRepository.save(any())).thenReturn(loanCharge);

            ChargeService.AddChargeRequest req = new ChargeService.AddChargeRequest(
                chargeDefId, new BigDecimal("50.00"), LocalDate.now());
            LoanCharge result = chargeService.addLoanCharge(loanId, req);
            assertThat(result).isNotNull();
            verify(loanChargeRepository).save(any(LoanCharge.class));
        }

        @Test
        @DisplayName("addLoanCharge throws when loan not found")
        void addLoanCharge_loanNotFound_throws() {
            when(entityManager.find(Loan.class, loanId)).thenReturn(null);

            ChargeService.AddChargeRequest req = new ChargeService.AddChargeRequest(
                chargeDefId, new BigDecimal("50.00"), LocalDate.now());
            assertThatThrownBy(() -> chargeService.addLoanCharge(loanId, req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("payLoanCharge marks charge as paid")
        void payLoanCharge_success() {
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));
            when(loanChargeRepository.save(any())).thenReturn(loanCharge);

            LoanCharge result = chargeService.payLoanCharge(loanId, loanChargeId);
            assertThat(result.isPaid()).isTrue();
            assertThat(result.getAmountOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("payLoanCharge throws when charge not found for loan")
        void payLoanCharge_notFound_throws() {
            UUID wrongLoanId = UUID.randomUUID();
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));

            assertThatThrownBy(() -> chargeService.payLoanCharge(wrongLoanId, loanChargeId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("waiveLoanCharge marks charge as waived")
        void waiveLoanCharge_success() {
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));
            when(loanChargeRepository.save(any())).thenReturn(loanCharge);

            LoanCharge result = chargeService.waiveLoanCharge(loanId, loanChargeId);
            assertThat(result.isWaived()).isTrue();
            assertThat(result.getAmountOutstanding()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("deleteLoanCharge removes the loan charge")
        void deleteLoanCharge_success() {
            when(loanChargeRepository.findById(loanChargeId)).thenReturn(Optional.of(loanCharge));

            assertThatCode(() -> chargeService.deleteLoanCharge(loanId, loanChargeId))
                .doesNotThrowAnyException();
            verify(loanChargeRepository).delete(loanCharge);
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
