package com.cba.card.dispute;

import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final CardDisputeRepository disputeRepository;

    // ── Raise Dispute ─────────────────────────────────────────────────────────

    @Transactional
    public CardDispute raiseDispute(UUID cardId, String transactionRef, DisputeReason reason,
                                    UUID raisedBy, BigDecimal originalAmount) {
        CardDispute dispute = new CardDispute();
        dispute.setCardId(cardId);
        dispute.setTransactionRef(transactionRef);
        dispute.setDisputeReason(reason);
        dispute.setRaisedBy(raisedBy);
        dispute.setOriginalAmount(originalAmount);
        dispute.setStatus(DisputeStatus.RAISED);

        CardDispute saved = disputeRepository.save(dispute);
        log.info("Dispute raised: id={} card={} ref={} reason={}",
                saved.getId(), cardId, transactionRef, reason);
        return saved;
    }

    // ── State Transitions ─────────────────────────────────────────────────────

    @Transactional
    public CardDispute updateDispute(UUID disputeId, String command,
                                     UUID resolvedBy, String resolutionNotes) {
        CardDispute dispute = findById(disputeId);
        switch (command.toLowerCase()) {
            case "review" -> {
                if (dispute.getStatus() != DisputeStatus.RAISED) {
                    throw CbaException.badRequest("INVALID_STATE",
                            "Only RAISED disputes can be moved to UNDER_REVIEW");
                }
                dispute.setStatus(DisputeStatus.UNDER_REVIEW);
            }
            case "resolve_issuer" -> {
                if (dispute.getStatus() != DisputeStatus.UNDER_REVIEW) {
                    throw CbaException.badRequest("INVALID_STATE",
                            "Only UNDER_REVIEW disputes can be resolved");
                }
                dispute.setStatus(DisputeStatus.RESOLVED_ISSUER);
                dispute.setResolvedBy(resolvedBy);
                dispute.setResolutionNotes(resolutionNotes);
            }
            case "resolve_acquirer" -> {
                if (dispute.getStatus() != DisputeStatus.UNDER_REVIEW) {
                    throw CbaException.badRequest("INVALID_STATE",
                            "Only UNDER_REVIEW disputes can be resolved");
                }
                dispute.setStatus(DisputeStatus.RESOLVED_ACQUIRER);
                dispute.setResolvedBy(resolvedBy);
                dispute.setResolutionNotes(resolutionNotes);
            }
            case "withdraw" -> {
                if (dispute.getStatus() == DisputeStatus.RESOLVED_ISSUER
                        || dispute.getStatus() == DisputeStatus.RESOLVED_ACQUIRER) {
                    throw CbaException.badRequest("INVALID_STATE",
                            "Already resolved disputes cannot be withdrawn");
                }
                dispute.setStatus(DisputeStatus.WITHDRAWN);
            }
            default -> throw CbaException.badRequest("INVALID_COMMAND",
                    "Unknown dispute command: " + command +
                    ". Valid: review, resolve_issuer, resolve_acquirer, withdraw");
        }
        log.info("Dispute {} → status={}", disputeId, dispute.getStatus());
        return disputeRepository.save(dispute);
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
}
