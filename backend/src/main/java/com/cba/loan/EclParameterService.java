package com.cba.loan;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The risk team's ECL parameters (probability of default by stage and days-past-due
 * band) and the portfolio's impairment position by currency and stage.
 */
@Service
@RequiredArgsConstructor
public class EclParameterService {

    private final EclPdRateRepository pdRateRepository;
    private final LoanRepository loanRepository;
    private final RegulatoryReserveService regulatoryReserveService;
    private final AuditLogService auditLogService;

    /** One PD band. {@code pdPercentage}: 12-month for stage 1, lifetime for stages 2 and 3. */
    public record PdRateRequest(
            @NotNull @Min(1) @Max(3) Integer stage,
            @NotNull @Min(0) Integer minDpd,
            @NotNull @Min(0) Integer maxDpd,
            @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal pdPercentage,
            @Size(max = 200) String description) {}

    public record PdRateView(UUID id, int stage, int minDpd, int maxDpd, BigDecimal pdPercentage, String description) {
        static PdRateView of(EclPdRate r) {
            return new PdRateView(r.getId(), r.getStage(), r.getMinDpd(), r.getMaxDpd(), r.getPdPercentage(), r.getDescription());
        }
    }

    /** Open loans of one currency and stage (null stage: not yet measured). */
    public record StageTotals(String currency, Integer stage, long loans, BigDecimal principal,
                              BigDecimal lossAllowance, BigDecimal prudentialProvision) {}

    public record EclSummary(List<StageTotals> stages, RegulatoryReserveService.ReservePosition regulatoryReserve) {}

    @Transactional(readOnly = true)
    public List<PdRateView> listPdRates() {
        return pdRateRepository.findAllByOrderByStageAscMinDpdAsc().stream().map(PdRateView::of).toList();
    }

    /**
     * Replaces the whole PD table. Every stage needs at least one band; bands may not
     * overlap within a stage; a PD is a percentage between 0 and 100.
     */
    @Transactional
    public List<PdRateView> replacePdRates(List<PdRateRequest> rates) {
        validate(rates);
        List<PdRateView> before = listPdRates();
        pdRateRepository.deleteAllInBatch();
        pdRateRepository.saveAll(rates.stream()
                .map(r -> new EclPdRate(r.stage(), r.minDpd(), r.maxDpd(), r.pdPercentage(), r.description()))
                .toList());
        List<PdRateView> after = listPdRates();
        auditLogService.log("ECL_PD_RATES", "all", "REPLACED", before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public EclSummary summary() {
        List<StageTotals> stages = loanRepository.provisionTotals(EnumSet.of(LoanStatus.ACTIVE, LoanStatus.IN_ARREARS))
                .stream()
                .map(r -> new StageTotals((String) r[0], (Integer) r[1], ((Number) r[2]).longValue(),
                        (BigDecimal) r[3], (BigDecimal) r[4], (BigDecimal) r[5]))
                .toList();
        return new EclSummary(stages, regulatoryReserveService.position());
    }

    static void validate(List<PdRateRequest> rates) {
        if (rates == null || rates.isEmpty()) {
            throw invalid("At least one band per stage is required");
        }
        for (int stage = 1; stage <= 3; stage++) {
            int s = stage;
            List<PdRateRequest> bands = rates.stream().filter(r -> r.stage() == s)
                    .sorted(Comparator.comparing(PdRateRequest::minDpd)).toList();
            if (bands.isEmpty()) throw invalid("Stage " + s + " has no PD band");
            for (int k = 0; k < bands.size(); k++) {
                PdRateRequest b = bands.get(k);
                if (b.maxDpd() < b.minDpd()) {
                    throw invalid("Stage " + s + " band " + b.minDpd() + "–" + b.maxDpd() + " ends before it starts");
                }
                if (k > 0 && b.minDpd() <= bands.get(k - 1).maxDpd()) {
                    throw invalid("Stage " + s + " bands overlap at " + b.minDpd() + " days past due");
                }
            }
        }
    }

    private static CbaException invalid(String message) {
        return CbaException.badRequest("ECL_PD_RATES_INVALID", message);
    }
}
