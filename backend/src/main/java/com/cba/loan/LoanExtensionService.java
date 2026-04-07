package com.cba.loan;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanExtensionService {

    public record CreateGuarantorRequest(
        Guarantor.GuarantorType guarantorType,
        UUID customerId,
        String firstName, String lastName, String email,
        String mobileNumber, String addressLine1, String addressLine2,
        String city, String country
    ) {}

    public record CreateCollateralRequest(
        UUID collateralTypeCodeValueId,
        BigDecimal value,
        String description,
        String currencyCode
    ) {}

    public record RescheduleRequest(
        Integer rescheduleFromInstallment,
        BigDecimal newInterestRate,
        LocalDate adjustRepaymentDate,
        Integer graceOnPrincipal,
        Integer graceOnInterest,
        Integer extraTerms,
        boolean recalculateInterest,
        String comment
    ) {}

    public record ReagingRequest(
        Integer frequencyNumber,
        LoanReagingRequest.FrequencyType frequencyType,
        LocalDate startDate,
        Integer numberOfInstallments,
        boolean preview
    ) {}

    public record ReamortizationRequest(String comment) {}

    private final LoanRepository loanRepository;
    private final GuarantorRepository guarantorRepository;
    private final CollateralRepository collateralRepository;
    private final LoanRescheduleRepository rescheduleRepository;
    private final LoanReagingRepository reagingRepository;
    private final LoanReamortizationRepository reamortizationRepository;
    private final AuditLogService auditLogService;

    // ── Guarantors ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Guarantor> listGuarantors(UUID loanId, Pageable p) {
        findLoan(loanId);
        return guarantorRepository.findByLoanId(loanId, p);
    }

    @Transactional
    public Guarantor createGuarantor(UUID loanId, CreateGuarantorRequest req) {
        Loan loan = findLoan(loanId);
        Guarantor g = new Guarantor();
        g.setLoan(loan);
        g.setGuarantorType(req.guarantorType() != null ? req.guarantorType() : Guarantor.GuarantorType.EXTERNAL);
        g.setCustomerId(req.customerId());
        g.setFirstName(req.firstName());
        g.setLastName(req.lastName());
        g.setEmail(req.email());
        g.setMobileNumber(req.mobileNumber());
        g.setAddressLine1(req.addressLine1());
        g.setAddressLine2(req.addressLine2());
        g.setCity(req.city());
        g.setCountry(req.country());
        Guarantor saved = guarantorRepository.save(g);
        auditLogService.log("Guarantor", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteGuarantor(UUID loanId, UUID guarantorId) {
        Guarantor g = guarantorRepository.findById(guarantorId)
            .orElseThrow(() -> CbaException.notFound("Guarantor", guarantorId));
        guarantorRepository.delete(g);
        auditLogService.log("Guarantor", guarantorId.toString(), "DELETE", null, null);
    }

    // ── Collaterals ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Collateral> listCollaterals(UUID loanId, Pageable p) {
        findLoan(loanId);
        return collateralRepository.findByLoanId(loanId, p);
    }

    @Transactional
    public Collateral createCollateral(UUID loanId, CreateCollateralRequest req) {
        Loan loan = findLoan(loanId);
        Collateral c = new Collateral();
        c.setLoan(loan);
        c.setCollateralTypeCodeValueId(req.collateralTypeCodeValueId());
        c.setValue(req.value());
        c.setDescription(req.description());
        c.setCurrencyCode(req.currencyCode() != null ? req.currencyCode() : "USD");
        Collateral saved = collateralRepository.save(c);
        auditLogService.log("Collateral", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public void deleteCollateral(UUID loanId, UUID collateralId) {
        Collateral c = collateralRepository.findById(collateralId)
            .orElseThrow(() -> CbaException.notFound("Collateral", collateralId));
        collateralRepository.delete(c);
        auditLogService.log("Collateral", collateralId.toString(), "DELETE", null, null);
    }

    // ── Loan Reschedule ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<LoanRescheduleRequest> listReschedules(UUID loanId, Pageable p) {
        findLoan(loanId);
        return rescheduleRepository.findByLoanId(loanId, p);
    }

    @Transactional
    public LoanRescheduleRequest createReschedule(UUID loanId, RescheduleRequest req) {
        Loan loan = findLoan(loanId);
        LoanRescheduleRequest r = new LoanRescheduleRequest();
        r.setLoan(loan);
        r.setRescheduleFromInstallment(req.rescheduleFromInstallment());
        r.setNewInterestRate(req.newInterestRate());
        r.setAdjustRepaymentDate(req.adjustRepaymentDate());
        r.setGraceOnPrincipal(req.graceOnPrincipal());
        r.setGraceOnInterest(req.graceOnInterest());
        r.setExtraTerms(req.extraTerms());
        r.setRecalculateInterest(req.recalculateInterest());
        r.setComment(req.comment());
        r.setRequestedOnDate(LocalDate.now());
        LoanRescheduleRequest saved = rescheduleRepository.save(r);
        auditLogService.log("LoanReschedule", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LoanRescheduleRequest approveReschedule(UUID id) {
        LoanRescheduleRequest r = rescheduleRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("LoanReschedule", id));
        r.setStatus(LoanRescheduleRequest.Status.APPROVED);
        r.setApprovedOnDate(LocalDate.now());
        return rescheduleRepository.save(r);
    }

    @Transactional
    public LoanRescheduleRequest rejectReschedule(UUID id) {
        LoanRescheduleRequest r = rescheduleRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("LoanReschedule", id));
        r.setStatus(LoanRescheduleRequest.Status.REJECTED);
        return rescheduleRepository.save(r);
    }

    // ── Re-aging (Fineract 1.14.0) ────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<LoanReagingRequest> listReaging(UUID loanId, Pageable p) {
        findLoan(loanId);
        return reagingRepository.findByLoanId(loanId, p);
    }

    @Transactional
    public LoanReagingRequest createReaging(UUID loanId, ReagingRequest req) {
        Loan loan = findLoan(loanId);
        LoanReagingRequest r = new LoanReagingRequest();
        r.setLoan(loan);
        r.setFrequencyNumber(req.frequencyNumber());
        r.setFrequencyType(req.frequencyType());
        r.setStartDate(req.startDate());
        r.setNumberOfInstallments(req.numberOfInstallments());
        r.setPreview(req.preview());
        r.setRequestedOnDate(LocalDate.now());
        LoanReagingRequest saved = reagingRepository.save(r);
        auditLogService.log("LoanReaging", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LoanReagingRequest approveReaging(UUID id) {
        LoanReagingRequest r = reagingRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("LoanReaging", id));
        r.setStatus(LoanReagingRequest.Status.APPROVED);
        r.setApprovedOnDate(LocalDate.now());
        return reagingRepository.save(r);
    }

    // ── Re-amortization (Fineract 1.14.0) ────────────────────────────

    @Transactional(readOnly = true)
    public Page<LoanReamortizationRequest> listReamortization(UUID loanId, Pageable p) {
        findLoan(loanId);
        return reamortizationRepository.findByLoanId(loanId, p);
    }

    @Transactional
    public LoanReamortizationRequest createReamortization(UUID loanId, ReamortizationRequest req) {
        Loan loan = findLoan(loanId);
        LoanReamortizationRequest r = new LoanReamortizationRequest();
        r.setLoan(loan);
        r.setComment(req.comment());
        r.setRequestedOnDate(LocalDate.now());
        LoanReamortizationRequest saved = reamortizationRepository.save(r);
        auditLogService.log("LoanReamortization", saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LoanReamortizationRequest approveReamortization(UUID id) {
        LoanReamortizationRequest r = reamortizationRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("LoanReamortization", id));
        r.setStatus(LoanReamortizationRequest.Status.APPROVED);
        r.setApprovedOnDate(LocalDate.now());
        return reamortizationRepository.save(r);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Loan findLoan(UUID loanId) {
        return loanRepository.findById(loanId)
            .orElseThrow(() -> CbaException.notFound("Loan", loanId));
    }
}
