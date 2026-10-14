package com.cba.loan;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@DisplayName("LoanExtensionService — unit tests")
class LoanExtensionServiceTest {

    @Mock LoanRepository loanRepository;
    @Mock GuarantorRepository guarantorRepository;
    @Mock CollateralRepository collateralRepository;
    @Mock LoanRescheduleRepository rescheduleRepository;
    @Mock LoanReagingRepository reagingRepository;
    @Mock LoanReamortizationRepository reamortizationRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks LoanExtensionService service;

    private UUID loanId;
    private Loan loan;

    @BeforeEach
    void setUp() {
        loanId = UUID.randomUUID();
        loan = new Loan();
        loan.setId(loanId);
    }

    @Nested
    @DisplayName("Guarantors")
    class Guarantors {

        @Test
        @DisplayName("listGuarantors returns page for valid loan")
        void listGuarantors_success() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(guarantorRepository.findByLoanId(eq(loanId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

            assertThat(service.listGuarantors(loanId, Pageable.unpaged()).getContent()).isEmpty();
        }

        @Test
        @DisplayName("listGuarantors throws when loan not found")
        void listGuarantors_loanNotFound_throws() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.listGuarantors(loanId, Pageable.unpaged()))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createGuarantor saves external guarantor")
        void createGuarantor_external_success() {
            Guarantor saved = new Guarantor();
            saved.setId(UUID.randomUUID());
            saved.setGuarantorType(Guarantor.GuarantorType.EXTERNAL);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(guarantorRepository.save(any())).thenReturn(saved);

            LoanExtensionService.CreateGuarantorRequest req = new LoanExtensionService.CreateGuarantorRequest(
                Guarantor.GuarantorType.EXTERNAL, null,
                "Jane", "Doe", "jane@example.com",
                "+1234567890", "123 Main St", null, "Nairobi", "KE"
            );
            Guarantor result = service.createGuarantor(loanId, req);
            assertThat(result.getGuarantorType()).isEqualTo(Guarantor.GuarantorType.EXTERNAL);
        }

        @Test
        @DisplayName("deleteGuarantor removes guarantor")
        void deleteGuarantor_success() {
            UUID guarantorId = UUID.randomUUID();
            Guarantor g = new Guarantor();
            g.setId(guarantorId);

            when(guarantorRepository.findById(guarantorId)).thenReturn(Optional.of(g));

            assertThatCode(() -> service.deleteGuarantor(loanId, guarantorId)).doesNotThrowAnyException();
            verify(guarantorRepository).delete(g);
        }

        @Test
        @DisplayName("deleteGuarantor throws when guarantor not found")
        void deleteGuarantor_notFound_throws() {
            UUID guarantorId = UUID.randomUUID();
            when(guarantorRepository.findById(guarantorId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteGuarantor(loanId, guarantorId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Collaterals")
    class Collaterals {

        @Test
        @DisplayName("createCollateral saves with default currency when null")
        void createCollateral_defaultCurrency() {
            Collateral saved = new Collateral();
            saved.setId(UUID.randomUUID());
            saved.setCurrencyCode("USD");

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(collateralRepository.save(any())).thenReturn(saved);

            LoanExtensionService.CreateCollateralRequest req = new LoanExtensionService.CreateCollateralRequest(
                UUID.randomUUID(), new BigDecimal("50000.00"), "Office equipment", null
            );
            Collateral result = service.createCollateral(loanId, req);
            assertThat(result.getCurrencyCode()).isEqualTo("USD");
        }

        @Test
        @DisplayName("deleteCollateral removes collateral")
        void deleteCollateral_success() {
            UUID collateralId = UUID.randomUUID();
            Collateral c = new Collateral();
            c.setId(collateralId);

            when(collateralRepository.findById(collateralId)).thenReturn(Optional.of(c));

            assertThatCode(() -> service.deleteCollateral(loanId, collateralId)).doesNotThrowAnyException();
            verify(collateralRepository).delete(c);
        }

        @Test
        @DisplayName("listCollaterals throws when loan not found")
        void listCollaterals_loanNotFound_throws() {
            when(loanRepository.findById(loanId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.listCollaterals(loanId, Pageable.unpaged()))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Reschedule")
    class Reschedule {

        @Test
        @DisplayName("createReschedule saves with PENDING status")
        void createReschedule_success() {
            loan.setStatus(LoanStatus.ACTIVE);
            LoanRescheduleRequest saved = new LoanRescheduleRequest();
            saved.setId(UUID.randomUUID());
            saved.setStatus(LoanRescheduleRequest.Status.PENDING);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(rescheduleRepository.save(any())).thenReturn(saved);

            LoanExtensionService.RescheduleRequest req = new LoanExtensionService.RescheduleRequest(
                3, new BigDecimal("12.00"), null, 1, 0, 0, true, "hardship"
            );
            LoanRescheduleRequest result = service.createReschedule(loanId, req);
            assertThat(result.getStatus()).isEqualTo(LoanRescheduleRequest.Status.PENDING);
        }

        @Test
        @DisplayName("a closed or written-off loan can't be rescheduled")
        void createReschedule_closedLoan_rejected() {
            loan.setStatus(LoanStatus.WRITTEN_OFF);
            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));

            LoanExtensionService.RescheduleRequest req = new LoanExtensionService.RescheduleRequest(
                3, null, null, 0, 0, 1, false, "hardship");
            assertThatThrownBy(() -> service.createReschedule(loanId, req))
                .isInstanceOf(CbaException.class).hasMessageContaining("ACTIVE or IN_ARREARS");
            verify(rescheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("approveReschedule sets APPROVED and audits the decision")
        void approveReschedule_success() {
            loan.setStatus(LoanStatus.IN_ARREARS);
            LoanRescheduleRequest r = pendingReschedule();
            when(rescheduleRepository.findById(r.getId())).thenReturn(Optional.of(r));
            when(rescheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanRescheduleRequest result = service.approveReschedule(r.getId());
            assertThat(result.getStatus()).isEqualTo(LoanRescheduleRequest.Status.APPROVED);
            verify(auditLogService).log(eq("LoanReschedule"), eq(r.getId().toString()), eq("APPROVE"), eq("PENDING"), any());
        }

        @Test
        @DisplayName("rejectReschedule sets REJECTED")
        void rejectReschedule_success() {
            LoanRescheduleRequest r = pendingReschedule();
            when(rescheduleRepository.findById(r.getId())).thenReturn(Optional.of(r));
            when(rescheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanRescheduleRequest result = service.rejectReschedule(r.getId());
            assertThat(result.getStatus()).isEqualTo(LoanRescheduleRequest.Status.REJECTED);
        }

        @Test
        @DisplayName("only a PENDING request can be approved or rejected")
        void decided_requestRejected() {
            loan.setStatus(LoanStatus.ACTIVE);
            LoanRescheduleRequest r = pendingReschedule();
            r.setStatus(LoanRescheduleRequest.Status.REJECTED);
            when(rescheduleRepository.findById(r.getId())).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveReschedule(r.getId()))
                .isInstanceOf(CbaException.class).hasMessageContaining("only a PENDING request");
            assertThatThrownBy(() -> service.rejectReschedule(r.getId()))
                .isInstanceOf(CbaException.class).hasMessageContaining("only a PENDING request");
            verify(rescheduleRepository, never()).save(any());
        }

        private LoanRescheduleRequest pendingReschedule() {
            LoanRescheduleRequest r = new LoanRescheduleRequest();
            r.setId(UUID.randomUUID());
            r.setLoan(loan);
            r.setStatus(LoanRescheduleRequest.Status.PENDING);
            return r;
        }
    }

    @Nested
    @DisplayName("Re-aging and Re-amortization")
    class ReagingAndReamortization {

        @Test
        @DisplayName("createReaging saves with PENDING status")
        void createReaging_success() {
            loan.setStatus(LoanStatus.ACTIVE);
            LoanReagingRequest saved = new LoanReagingRequest();
            saved.setId(UUID.randomUUID());
            saved.setStatus(LoanReagingRequest.Status.PENDING);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(reagingRepository.save(any())).thenReturn(saved);

            LoanExtensionService.ReagingRequest req = new LoanExtensionService.ReagingRequest(
                1, LoanReagingRequest.FrequencyType.MONTHS, LocalDate.now().plusMonths(1), 3, false
            );
            LoanReagingRequest result = service.createReaging(loanId, req);
            assertThat(result.getStatus()).isEqualTo(LoanReagingRequest.Status.PENDING);
        }

        @Test
        @DisplayName("approveReaging sets APPROVED status")
        void approveReaging_success() {
            loan.setStatus(LoanStatus.ACTIVE);
            LoanReagingRequest r = new LoanReagingRequest();
            r.setId(UUID.randomUUID());
            r.setLoan(loan);
            r.setStatus(LoanReagingRequest.Status.PENDING);

            when(reagingRepository.findById(r.getId())).thenReturn(Optional.of(r));
            when(reagingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanReagingRequest result = service.approveReaging(loanId, r.getId());
            assertThat(result.getStatus()).isEqualTo(LoanReagingRequest.Status.APPROVED);
        }

        @Test
        @DisplayName("a request of another loan is not found under this loan")
        void approveReaging_otherLoan_notFound() {
            LoanReagingRequest r = new LoanReagingRequest();
            r.setId(UUID.randomUUID());
            r.setLoan(loan);
            r.setStatus(LoanReagingRequest.Status.PENDING);
            when(reagingRepository.findById(r.getId())).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.approveReaging(UUID.randomUUID(), r.getId()))
                .isInstanceOf(CbaException.class).hasMessageContaining("LoanReaging");
            verify(reagingRepository, never()).save(any());
        }

        @Test
        @DisplayName("createReamortization saves with PENDING status")
        void createReamortization_success() {
            loan.setStatus(LoanStatus.ACTIVE);
            LoanReamortizationRequest saved = new LoanReamortizationRequest();
            saved.setId(UUID.randomUUID());
            saved.setStatus(LoanReamortizationRequest.Status.PENDING);

            when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan));
            when(reamortizationRepository.save(any())).thenReturn(saved);

            LoanExtensionService.ReamortizationRequest req =
                new LoanExtensionService.ReamortizationRequest("partial forgiveness applied");
            LoanReamortizationRequest result = service.createReamortization(loanId, req);
            assertThat(result.getStatus()).isEqualTo(LoanReamortizationRequest.Status.PENDING);
        }

        @Test
        @DisplayName("approveReamortization sets APPROVED status")
        void approveReamortization_success() {
            loan.setStatus(LoanStatus.ACTIVE);
            LoanReamortizationRequest r = new LoanReamortizationRequest();
            r.setId(UUID.randomUUID());
            r.setLoan(loan);
            r.setStatus(LoanReamortizationRequest.Status.PENDING);

            when(reamortizationRepository.findById(r.getId())).thenReturn(Optional.of(r));
            when(reamortizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LoanReamortizationRequest result = service.approveReamortization(loanId, r.getId());
            assertThat(result.getStatus()).isEqualTo(LoanReamortizationRequest.Status.APPROVED);
        }
    }
}
