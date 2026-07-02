package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.system.bureau.CreditBureauProvider;
import com.cba.system.bureau.CreditCheckRequest;
import com.cba.system.bureau.CreditReport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs a credit-bureau pull through the active {@link CreditBureauProvider} and applies the
 * platform's pass/fail policy. This is the seam loan origination would call before approving
 * a loan; today it is exposed via {@code POST /api/v1/creditbureaus/check} for ops/testing.
 *
 * <p>Policy:
 * <ul>
 *   <li>{@code HIT} → pass iff {@code score >= minScore}</li>
 *   <li>{@code NO_HIT} / {@code UNAVAILABLE} → pass iff the loan product does not mandate a
 *       check (a thin file or a bureau outage only blocks products that require the check)</li>
 * </ul>
 * The {@code mandatory} flag comes from the product's {@link CreditBureauProductMapping}.
 */
@Service
@RequiredArgsConstructor
public class CreditBureauCheckService {

    /** Aggregate outcome returned to the caller. */
    public record CreditCheckResult(CreditReport report, boolean mandatory, boolean passed, String provider) {}

    private final CreditBureauProvider provider;
    private final CreditBureauProductMappingRepository mappingRepo;
    private final AuditLogService auditLogService;

    @Value("${app.creditbureau.min-score:600}")
    private int defaultMinScore;

    /**
     * @param req          subject to look up
     * @param loanProductId optional — drives whether the check is mandatory for this product
     * @param minScore      optional override of {@code app.creditbureau.min-score}
     */
    @Transactional
    public CreditCheckResult check(CreditCheckRequest req, UUID loanProductId, Integer minScore) {
        int threshold = (minScore != null) ? minScore : defaultMinScore;
        boolean mandatory = isMandatory(loanProductId);

        CreditReport report = provider.pull(req);

        boolean passed = switch (report.status()) {
            case HIT -> report.score() >= threshold;
            case NO_HIT, UNAVAILABLE -> !mandatory;
        };

        CreditCheckResult result = new CreditCheckResult(report, mandatory, passed, provider.providerId());
        // Audit the decision, not the raw report, to avoid persisting bureau PII verbatim.
        auditLogService.log("CreditCheck",
                req.customerId() != null ? req.customerId().toString() : "unknown",
                "CHECK", null,
                report.status() + "/score=" + report.score() + "/passed=" + passed);
        return result;
    }

    private boolean isMandatory(UUID loanProductId) {
        if (loanProductId == null) return false;
        return mappingRepo.findByLoanProductId(loanProductId).stream()
                .anyMatch(m -> m.isActive() && m.isCreditCheckMandatory());
    }
}
