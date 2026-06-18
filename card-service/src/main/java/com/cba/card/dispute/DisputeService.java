package com.cba.card.dispute;

import com.cba.card.common.CbaException;
import com.cba.card.openbanking.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Scheme-compliant chargeback workflow service.
 *
 * State machine commands:
 *   raiseDispute           -> creates RAISED dispute
 *   requestRetrieval       -> RAISED -> RETRIEVAL_REQUESTED  (creates RetrievalRequest)
 *   initiateChargeback     -> RAISED/RETRIEVAL_REQUESTED -> CHARGEBACK_INITIATED
 *   recordRepresentment    -> CHARGEBACK_INITIATED -> REPRESENTMENT (creates Representment)
 *   escalateToPreArbitration -> REPRESENTMENT -> PRE_ARBITRATION
 *   resolve                -> any active -> RESOLVED (requires resolutionFavor)
 *   withdraw               -> any non-terminal -> WITHDRAWN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final CardDisputeRepository           disputeRepository;
    private final ChargebackReasonCodeRepository  reasonCodeRepository;
    private final RetrievalRequestRepository      retrievalRepository;
    private final RepresentmentRepository         representmentRepository;

    /** Injected lazily to avoid a potential cycle with WebhookDeliveryService. */
    @Lazy @Autowired
    private WebhookService webhookService;

    private static final Set<DisputeStatus> TERMINAL = Set.of(
            DisputeStatus.RESOLVED, DisputeStatus.WITHDRAWN);

    // ── Raise ─────────────────────────────────────────────────────────────────

    @Transactional
    public CardDispute raiseDispute(UUID cardId, String transactionRef, DisputeReason reason,
                                    UUID raisedBy, BigDecimal originalAmount, String currencyCode) {
        CardDispute dispute = new CardDispute();
        dispute.setCardId(cardId);
        dispute.setTransactionRef(transactionRef);
        dispute.setDisputeReason(reason);
        dispute.setRaisedBy(raisedBy);
        dispute.setOriginalAmount(originalAmount);
        dispute.setCurrencyCode(currencyCode);
        dispute.setStatus(DisputeStatus.RAISED);

        CardDispute saved = disputeRepository.save(dispute);
        log.info("Dispute raised: id={} card={} ref={} reason={}",
                saved.getId(), cardId, transactionRef, reason);
        try {
            webhookService.publishEvent("DISPUTE.RAISED", Map.of(
                    "disputeId", saved.getId(), "cardId", cardId, "reason", reason.name()));
        } catch (Exception e) {
            log.debug("Webhook publish failed for DISPUTE.RAISED: {}", e.getMessage());
        }
        return saved;
    }

    // ── Request Retrieval ─────────────────────────────────────────────────────

    @Transactional
    public CardDispute requestRetrieval(UUID disputeId) {
        CardDispute dispute = findById(disputeId);
        requireStatus(dispute, "request_retrieval", DisputeStatus.RAISED);

        dispute.setStatus(DisputeStatus.RETRIEVAL_REQUESTED);

        RetrievalRequest req = new RetrievalRequest();
        req.setDispute(dispute);
        req.setDeadline(LocalDate.now().plusDays(14));
        retrievalRepository.save(req);

        log.info("Dispute {} -> RETRIEVAL_REQUESTED", disputeId);
        return disputeRepository.save(dispute);
    }

    // ── Initiate Chargeback ───────────────────────────────────────────────────

    @Transactional
    public CardDispute initiateChargeback(UUID disputeId, UUID reasonCodeId) {
        CardDispute dispute = findById(disputeId);
        if (dispute.getStatus() != DisputeStatus.RAISED
                && dispute.getStatus() != DisputeStatus.RETRIEVAL_REQUESTED) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Chargeback can only be initiated from RAISED or RETRIEVAL_REQUESTED; current: "
                    + dispute.getStatus());
        }

        ChargebackReasonCode rc = reasonCodeRepository.findById(reasonCodeId)
                .orElseThrow(() -> CbaException.notFound("REASON_CODE_NOT_FOUND",
                        "Reason code not found: " + reasonCodeId));

        LocalDate today = LocalDate.now();
        dispute.setSchemeReasonCode(rc);
        dispute.setChargebackDeadline(today.plusDays(rc.getMaxDaysToChargeback()));
        dispute.setResponseDeadline(today.plusDays(rc.getMaxDaysToRespond()));
        dispute.setStatus(DisputeStatus.CHARGEBACK_INITIATED);

        log.info("Dispute {} -> CHARGEBACK_INITIATED scheme={} code={}",
                disputeId, rc.getScheme(), rc.getCode());
        return disputeRepository.save(dispute);
    }

    // ── Representment ─────────────────────────────────────────────────────────

    @Transactional
    public CardDispute recordRepresentment(UUID disputeId, String acquirerReason) {
        CardDispute dispute = findById(disputeId);
        requireStatus(dispute, "representment", DisputeStatus.CHARGEBACK_INITIATED);

        ChargebackReasonCode rc = dispute.getSchemeReasonCode();
        int preArbDays = (rc != null) ? rc.getMaxDaysPreArbitration() : 30;

        LocalDate deadline = LocalDate.now().plusDays(preArbDays);
        dispute.setPreArbitrationDeadline(deadline);
        dispute.setStatus(DisputeStatus.REPRESENTMENT);

        Representment rep = new Representment();
        rep.setDispute(dispute);
        rep.setReason((acquirerReason != null) ? acquirerReason : "");
        rep.setDeadline(deadline);
        representmentRepository.save(rep);

        log.info("Dispute {} -> REPRESENTMENT preArbDeadline={}", disputeId, deadline);
        return disputeRepository.save(dispute);
    }

    // ── Pre-Arbitration ───────────────────────────────────────────────────────

    @Transactional
    public CardDispute escalateToPreArbitration(UUID disputeId) {
        CardDispute dispute = findById(disputeId);
        requireStatus(dispute, "pre_arbitration", DisputeStatus.REPRESENTMENT);

        representmentRepository
                .findTopByDisputeIdOrderByCreatedAtDesc(disputeId)
                .ifPresent(rep -> {
                    rep.setStatus("ESCALATED");
                    representmentRepository.save(rep);
                });

        dispute.setStatus(DisputeStatus.PRE_ARBITRATION);
        log.info("Dispute {} -> PRE_ARBITRATION", disputeId);
        return disputeRepository.save(dispute);
    }

    // ── Resolve ───────────────────────────────────────────────────────────────

    @Transactional
    public CardDispute resolve(UUID disputeId, UUID resolvedBy,
                               String resolutionFavor, String notes) {
        CardDispute dispute = findById(disputeId);
        if (TERMINAL.contains(dispute.getStatus())) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Dispute " + disputeId + " is already terminal: " + dispute.getStatus());
        }
        if (!"ISSUER".equalsIgnoreCase(resolutionFavor)
                && !"ACQUIRER".equalsIgnoreCase(resolutionFavor)) {
            throw CbaException.badRequest("INVALID_RESOLUTION_FAVOR",
                    "resolutionFavor must be ISSUER or ACQUIRER; got: " + resolutionFavor);
        }

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolvedBy(resolvedBy);
        dispute.setResolutionFavor(resolutionFavor.toUpperCase());
        dispute.setResolutionNotes(notes);

        log.info("Dispute {} -> RESOLVED favor={}", disputeId, resolutionFavor);
        CardDispute saved = disputeRepository.save(dispute);
        try {
            webhookService.publishEvent("DISPUTE.RESOLVED",
                    Map.of("disputeId", disputeId, "resolutionFavor", saved.getResolutionFavor()));
        } catch (Exception e) {
            log.debug("Webhook publish failed for DISPUTE.RESOLVED: {}", e.getMessage());
        }
        return saved;
    }

    // ── Withdraw ──────────────────────────────────────────────────────────────

    @Transactional
    public CardDispute withdraw(UUID disputeId) {
        CardDispute dispute = findById(disputeId);
        if (TERMINAL.contains(dispute.getStatus())) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Cannot withdraw a dispute already in state: " + dispute.getStatus());
        }
        dispute.setStatus(DisputeStatus.WITHDRAWN);
        log.info("Dispute {} -> WITHDRAWN", disputeId);
        return disputeRepository.save(dispute);
    }

    // ── Reason Code Lookup ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChargebackReasonCode> listReasonCodes(String scheme) {
        if (scheme != null) {
            return reasonCodeRepository.findBySchemeOrderByCode(scheme.toUpperCase());
        }
        return reasonCodeRepository.findAll();
    }

    // ── Sub-resource queries ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RetrievalRequest> listRetrievalRequests(UUID disputeId) {
        findById(disputeId);
        return retrievalRepository.findAll().stream()
                .filter(r -> r.getDispute().getId().equals(disputeId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Representment> listRepresentments(UUID disputeId) {
        findById(disputeId);
        return representmentRepository.findAll().stream()
                .filter(r -> r.getDispute().getId().equals(disputeId))
                .collect(Collectors.toList());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CardDispute findById(UUID id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("DISPUTE_NOT_FOUND",
                        "Dispute not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CardDispute> findByCard(UUID cardId) {
        return disputeRepository.findByCardIdOrderByCreatedAtDesc(cardId);
    }

    @Transactional(readOnly = true)
    public List<CardDispute> findAll(DisputeStatus status) {
        if (status != null) {
            return disputeRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return disputeRepository.findAll();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void requireStatus(CardDispute dispute, String command, DisputeStatus required) {
        if (dispute.getStatus() != required) {
            throw CbaException.badRequest("INVALID_STATE",
                    "Command '" + command + "' requires status " + required
                    + "; current: " + dispute.getStatus());
        }
    }
}
