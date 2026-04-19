package com.cba.fraud;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class FraudAlertService {

    private final FraudAlertRepository alertRepository;
    private final FraudCaseRepository  caseRepository;

    private static final AtomicInteger caseSeq = new AtomicInteger(1000);

    @Transactional(readOnly = true)
    public Page<FraudAlert> listAlerts(String status, String severity, UUID customerId, Pageable pageable) {
        return alertRepository.findFiltered(status, severity, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public FraudAlert getAlert(UUID id) {
        return alertRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("FraudAlert", id));
    }

    @Transactional
    public FraudAlert reviewAlert(UUID id, String reviewedBy) {
        FraudAlert alert = getAlert(id);
        alert.setStatus("REVIEWING");
        alert.setReviewedBy(reviewedBy);
        return alertRepository.save(alert);
    }

    @Transactional
    public FraudAlert closeAlert(UUID id, String status, String reviewedBy) {
        FraudAlert alert = getAlert(id);
        if (!status.equals("CLOSED_FALSE_POSITIVE") && !status.equals("CLOSED_CONFIRMED") && !status.equals("SUPPRESSED")) {
            throw CbaException.badRequest("INVALID_ALERT_STATUS", "Use CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED or SUPPRESSED");
        }
        alert.setStatus(status);
        alert.setReviewedBy(reviewedBy);
        alert.setResolvedAt(Instant.now());
        return alertRepository.save(alert);
    }

    @Transactional
    public FraudCase createCase(String title, UUID customerId, String riskLevel, String assignedTo) {
        FraudCase fraudCase = new FraudCase();
        fraudCase.setCaseNumber("CASE-" + String.format("%06d", caseSeq.getAndIncrement()));
        fraudCase.setTitle(title);
        fraudCase.setCustomerId(customerId);
        fraudCase.setStatus("OPEN");
        fraudCase.setRiskLevel(riskLevel != null ? riskLevel : "MEDIUM");
        fraudCase.setAssignedTo(assignedTo);
        return caseRepository.save(fraudCase);
    }

    @Transactional
    public FraudCase linkAlertToCase(UUID alertId, UUID caseId) {
        FraudAlert alert = getAlert(alertId);
        caseRepository.findById(caseId)
            .orElseThrow(() -> CbaException.notFound("FraudCase", caseId));
        alert.setCaseId(caseId);
        alertRepository.save(alert);
        return caseRepository.findById(caseId).get();
    }

    @Transactional(readOnly = true)
    public Page<FraudCase> listCases(String status, String riskLevel, UUID customerId, Pageable pageable) {
        return caseRepository.findFiltered(status, riskLevel, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public FraudCase getCase(UUID id) {
        return caseRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("FraudCase", id));
    }

    @Transactional
    public FraudCase updateCase(UUID id, String status, String assignedTo, String resolutionNotes) {
        FraudCase fraudCase = getCase(id);
        if (status != null) fraudCase.setStatus(status);
        if (assignedTo != null) fraudCase.setAssignedTo(assignedTo);
        if (resolutionNotes != null) fraudCase.setResolutionNotes(resolutionNotes);
        if ("CLOSED".equals(status)) fraudCase.setResolvedAt(Instant.now());
        return caseRepository.save(fraudCase);
    }
}
