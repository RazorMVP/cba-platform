package com.cba.payment;

import com.cba.account.AccountGlPosting;
import com.cba.account.AccountService;
import com.cba.account.TransactionType;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The final outcome of an external payment (backlog 1), from the gateway's signed callback or
 * from ops. The payment row is locked, so two outcomes for one payment run one after the other;
 * only a PROCESSING payment moves, and the same outcome again is answered as a repeat.
 * <ul>
 *   <li>{@code SETTLED} — paid out of the nostro: DR 2400 / CR nostro, status COMPLETED.</li>
 *   <li>{@code RETURNED} (the beneficiary's bank sent it back) / {@code FAILED} (the gateway
 *       refused it after accepting) — the source is credited back: DR 2400 / CR deposit.</li>
 * </ul>
 * A return after settlement isn't handled here: it is an incoming credit (decision D3a).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalPaymentOutcomeService {

    public enum Outcome { SETTLED, RETURNED, FAILED }

    public record Result(PaymentResponse payment, boolean repeat) {}

    private final PaymentRepository paymentRepository;
    private final AccountService accountService;
    private final AccountGlPosting accountGlPosting;
    private final AuditLogService auditLogService;
    private final PaymentService paymentService;
    private final org.springframework.data.domain.AuditorAware<String> auditorAware;

    /** From the gateway's callback: the payment named by our reference, or else by the network's. */
    @Transactional
    public Result applyCallback(String reference, String networkReference, Outcome outcome, String reason) {
        boolean byReference = reference != null && !reference.isBlank();
        Payment payment = (byReference
                ? paymentRepository.findByReferenceForUpdate(reference.trim())
                : paymentRepository.findByNetworkReferenceForUpdate(networkReference == null ? "" : networkReference.trim()))
                .orElseThrow(() -> CbaException.notFound("Payment", byReference ? reference : networkReference));
        if (byReference && networkReference != null && !networkReference.isBlank()
                && payment.getNetworkReference() != null && !payment.getNetworkReference().equals(networkReference.trim())) {
            throw CbaException.conflict("REFERENCE_MISMATCH",
                    "The network reference doesn't match payment " + payment.getReferenceNumber());
        }
        return apply(payment, outcome, reason, "CALLBACK", "external-gateway");
    }

    /**
     * From ops (ADMIN), with a reason — not by the user who initiated the payment (403 SAME_USER),
     * compared through the same identity {@code created_by} records.
     */
    @Transactional
    public Result applyManual(UUID paymentId, Outcome outcome, String reason) {
        if (outcome == Outcome.FAILED) {
            throw CbaException.badRequest("INVALID_OUTCOME", "outcome must be SETTLED or RETURNED");
        }
        if (reason == null || reason.isBlank()) {
            throw CbaException.badRequest("REASON_REQUIRED", "A reason is required");
        }
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> CbaException.notFound("Payment", paymentId));
        String actor = auditorAware.getCurrentAuditor().orElse(null);
        if (actor != null && actor.equals(payment.getCreatedBy())) {
            throw CbaException.forbidden("SAME_USER",
                    "An external payment's outcome must be recorded by someone other than the user who initiated it");
        }
        return apply(payment, outcome, reason, "MANUAL", actor == null ? "unknown" : actor);
    }

    private Result apply(Payment payment, Outcome outcome, String reason, String source, String actor) {
        if (payment.getPaymentType() != PaymentType.EXTERNAL_PAYMENT) {
            throw CbaException.badRequest("NOT_AN_EXTERNAL_PAYMENT",
                    "Payment " + payment.getReferenceNumber() + " is not an external payment");
        }
        PaymentStatus target = switch (outcome) {
            case SETTLED -> PaymentStatus.COMPLETED;
            case RETURNED -> PaymentStatus.RETURNED;
            case FAILED -> PaymentStatus.FAILED;
        };
        if (payment.getStatus() == target) {
            return new Result(paymentService.toResponse(payment), true);
        }
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw CbaException.conflict("PAYMENT_NOT_PROCESSING",
                    "Payment " + payment.getReferenceNumber() + " is " + payment.getStatus() + ", not PROCESSING");
        }

        String ref = payment.getReferenceNumber();
        LocalDate today = LocalDate.now();
        PaymentStatus before = payment.getStatus();
        if (outcome == Outcome.SETTLED) {
            accountGlPosting.postExternalPaymentSettlement(payment.getAmount(), payment.getCurrencyCode(), today,
                    "External payment " + ref + " settled", ref, payment.getId());
            payment.setExecutedDate(Instant.now());
            payment.setSettledAt(Instant.now());
        } else {
            String returnRef = "RET-" + ref;
            AccountService.BalanceChange credit = accountService.creditForSubledger(
                    payment.getSourceAccount().getId(), payment.getAmount(), TransactionType.REVERSAL,
                    "External payment " + ref + " " + outcome.name().toLowerCase(Locale.ROOT), returnRef, actor);
            accountGlPosting.postExternalPaymentMovement(credit.account(), credit.before(), today,
                    "External payment " + ref + " " + outcome.name().toLowerCase(Locale.ROOT), returnRef, payment.getId());
            payment.setFailureReason(reason);
        }
        payment.setStatus(target);
        payment.setOutcomeSource(source);
        Payment saved = paymentRepository.save(payment);

        auditLogService.log("PAYMENT", saved.getId().toString(), "EXTERNAL_PAYMENT_" + outcome.name(),
                before.name(), Map.of("status", target.name(), "source", source, "by", actor,
                        "reason", reason == null ? "" : reason));
        log.info("External payment {} {} ({})", ref, outcome, source);
        return new Result(paymentService.toResponse(saved), false);
    }
}
