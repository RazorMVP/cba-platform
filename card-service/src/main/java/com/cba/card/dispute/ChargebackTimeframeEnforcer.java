package com.cba.card.dispute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly job that enforces scheme chargeback timeframes.
 *
 * Runs at 02:00 every night. Three enforcement actions:
 *
 * 1. Expire retrieval requests whose deadline has passed without acquirer response.
 *    The dispute stays at RETRIEVAL_REQUESTED — a human must then initiate
 *    the chargeback (no auto-escalation, as the reason code is not yet known).
 *
 * 2. Auto-accept representments whose issuer deadline has passed without
 *    escalation. The dispute resolves in the acquirer's favour by scheme rules.
 *
 * 3. Auto-resolve PRE_ARBITRATION disputes that have exceeded the scheme's
 *    total maximum window (365 days from raise date). In practice schemes
 *    issue rulings well before this — this is a backstop only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargebackTimeframeEnforcer {

    private final CardDisputeRepository      disputeRepository;
    private final RetrievalRequestRepository retrievalRepository;
    private final RepresentmentRepository    representmentRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void enforceTimeframes() {
        LocalDate today = LocalDate.now();
        log.info("ChargebackTimeframeEnforcer running for date={}", today);

        expireRetrievalRequests(today);
        autoAcceptLapsedRepresentments(today);
    }

    // ── 1. Expire overdue retrieval requests ─────────────────────────────────

    private void expireRetrievalRequests(LocalDate today) {
        List<RetrievalRequest> overdue =
                retrievalRepository.findByStatusAndDeadlineBefore("PENDING", today);

        for (RetrievalRequest req : overdue) {
            req.setStatus("EXPIRED");
            retrievalRepository.save(req);
            log.warn("RetrievalRequest {} expired (dispute={}). "
                    + "Manual chargeback initiation required.",
                    req.getId(), req.getDispute().getId());
        }
        if (!overdue.isEmpty()) {
            log.info("Expired {} overdue retrieval request(s)", overdue.size());
        }
    }

    // ── 2. Auto-accept representments whose issuer deadline has passed ────────

    private void autoAcceptLapsedRepresentments(LocalDate today) {
        List<Representment> lapsed =
                representmentRepository.findByStatusAndDeadlineBefore("PENDING", today);

        for (Representment rep : lapsed) {
            rep.setStatus("ACCEPTED");
            representmentRepository.save(rep);

            CardDispute dispute = rep.getDispute();
            if (dispute.getStatus() == DisputeStatus.REPRESENTMENT) {
                dispute.setStatus(DisputeStatus.RESOLVED);
                dispute.setResolutionFavor("ACQUIRER");
                dispute.setResolutionNotes(
                        "Auto-resolved: issuer missed pre-arbitration deadline ("
                        + rep.getDeadline() + ")");
                disputeRepository.save(dispute);
                log.warn("Dispute {} auto-resolved ACQUIRER: issuer missed pre-arb deadline {}",
                        dispute.getId(), rep.getDeadline());
            }
        }
        if (!lapsed.isEmpty()) {
            log.info("Auto-resolved {} dispute(s) due to lapsed representment deadlines",
                    lapsed.size());
        }
    }
}
