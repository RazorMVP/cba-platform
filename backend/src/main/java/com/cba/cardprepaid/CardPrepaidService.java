package com.cba.cardprepaid;

import com.cba.account.Account;
import com.cba.account.AccountGlPosting;
import com.cba.account.AccountRepository;
import com.cba.account.AccountService;
import com.cba.account.AccountStatus;
import com.cba.account.CardCharges;
import com.cba.account.TransactionType;
import com.cba.account.dto.CardFunds;
import com.cba.audit.AuditLogService;
import com.cba.cardprepaid.dto.PrepaidClearingRequest;
import com.cba.cardprepaid.dto.PrepaidMovementResult;
import com.cba.cardprepaid.dto.PrepaidRefundRequest;
import com.cba.cardprepaid.dto.PrepaidTransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Prepaid card wallets on the backend's books (backlog 15c). card-service owns the wallet balance and calls
 * these (service role CARD_SERVICE) to move the money and post the journal:
 * <ul>
 *   <li><b>load</b> — debit the customer's account under the withdrawal rules, DR deposit / CR 2500;</li>
 *   <li><b>unload</b> — redeem the wallet to the customer's account, DR 2500 / CR deposit;</li>
 *   <li><b>clearing</b> — prepaid spend cleared at batch close, DR 2500 / CR 2300 plus interchange and fees;</li>
 *   <li><b>refund</b> — a cleared purchase reversed, the opposite, pro rata; any number of refunds up to the cleared amount.</li>
 * </ul>
 * Each is idempotent on its reference (an advisory lock serialises two requests with one reference). A
 * problem is a decline with a reason, never a 4xx: card-service treats a 4xx as "unreachable" and retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardPrepaidService {

    private final PrepaidCardMovementRepository movements;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final AccountGlPosting accountGlPosting;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @Transactional
    public PrepaidMovementResult load(PrepaidTransferRequest req, String actor) {
        return transfer(req, PrepaidCardMovement.Type.LOAD, actor);
    }

    @Transactional
    public PrepaidMovementResult unload(PrepaidTransferRequest req, String actor) {
        return transfer(req, PrepaidCardMovement.Type.UNLOAD, actor);
    }

    private PrepaidMovementResult transfer(PrepaidTransferRequest req, PrepaidCardMovement.Type type, String actor) {
        String reference = req.reference().trim();
        lock(reference);
        String currency = upper(req.currencyCode());
        var existing = movements.findByReference(reference);
        if (existing.isPresent()) {
            PrepaidCardMovement m = existing.get();
            boolean same = m.getType() == type && Objects.equals(m.getAccountId(), req.accountId())
                    && Objects.equals(m.getCardId(), req.cardId()) && m.getCurrencyCode().equals(currency)
                    && req.amount() != null && m.getAmount().compareTo(req.amount()) == 0;
            return same ? result(m, true) : PrepaidMovementResult.declined(reference, "REFERENCE_CONFLICT");
        }
        String invalid = invalidAmount(req.amount(), currency);
        if (invalid != null) return PrepaidMovementResult.declined(reference, invalid);
        if (req.accountId() == null || req.customerId() == null || req.cardId() == null) {
            return PrepaidMovementResult.declined(reference, "ACCOUNT_NOT_FOUND");
        }
        Account account = accountRepository.findByIdWithLock(req.accountId()).orElse(null);
        if (account == null || account.getCustomer() == null || !account.getCustomer().getId().equals(req.customerId())) {
            // Someone else's account is reported exactly like a missing one.
            return PrepaidMovementResult.declined(reference, "ACCOUNT_NOT_FOUND");
        }
        if (!account.getCurrencyCode().equalsIgnoreCase(currency)) {
            return PrepaidMovementResult.declined(reference, "CURRENCY_MISMATCH");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return PrepaidMovementResult.declined(reference, "ACCOUNT_NOT_ACTIVE");
        }
        if (type == PrepaidCardMovement.Type.LOAD) {
            CardFunds funds = accountService.cardFunds(account.getId());
            if (!funds.debitAllowed()) return PrepaidMovementResult.declined(reference, funds.reason());
            if (funds.availableBalance().compareTo(req.amount()) < 0) {
                return PrepaidMovementResult.declined(reference, "INSUFFICIENT_FUNDS");
            }
        }

        String description = (type == PrepaidCardMovement.Type.LOAD ? "Prepaid card load " : "Prepaid card unload ") + reference;
        AccountService.BalanceChange change = type == PrepaidCardMovement.Type.LOAD
                ? accountService.debitForSubledger(account.getId(), req.amount(), TransactionType.PREPAID_CARD_LOAD,
                        description, reference, actor)
                : accountService.creditForSubledger(account.getId(), req.amount(), TransactionType.PREPAID_CARD_UNLOAD,
                        description, reference, actor);
        String journal = accountGlPosting.postPrepaidCardMovement(change.account(), change.before(), LocalDate.now(),
                description, reference, req.cardId());

        PrepaidCardMovement m = movement(reference, type, req.amount(), currency, journal, actor);
        m.setCustomerId(req.customerId());
        m.setCardId(req.cardId());
        m.setAccountId(account.getId());
        movements.save(m);
        audit(m);
        return result(m, false);
    }

    /** Prepaid spend cleared: DR 2500 / CR 2300 with interchange and scheme fees (as 15b). */
    @Transactional
    public PrepaidMovementResult clearing(PrepaidClearingRequest req, String actor) {
        String reference = req.reference().trim();
        lock(reference);
        String currency = upper(req.currencyCode());
        var existing = movements.findByReference(reference);
        if (existing.isPresent()) {
            PrepaidCardMovement m = existing.get();
            boolean same = m.getType() == PrepaidCardMovement.Type.CLEARING && m.getCurrencyCode().equals(currency)
                    && req.amount() != null && m.getAmount().compareTo(req.amount()) == 0;
            // A repeat returns the first figures: they were posted.
            return same ? result(m, true) : PrepaidMovementResult.declined(reference, "REFERENCE_CONFLICT");
        }
        String invalid = invalidAmount(req.amount(), currency);
        if (invalid != null) return PrepaidMovementResult.declined(reference, invalid);
        CardCharges charges = req.charges();
        if (invalidCharges(req.amount(), currency, charges)) {
            return PrepaidMovementResult.declined(reference, "INVALID_CARD_FEES");
        }
        String journal = accountGlPosting.postPrepaidCardClearing(req.amount(), currency, charges, false, LocalDate.now(),
                "Prepaid card clearing " + reference, reference, req.cardId());
        PrepaidCardMovement m = movement(reference, PrepaidCardMovement.Type.CLEARING, req.amount(), currency, journal, actor);
        m.setCardId(req.cardId());
        m.setScheme(req.scheme() == null || req.scheme().isBlank() ? null : req.scheme().trim().toUpperCase(Locale.ROOT));
        if (!charges.isZero()) {
            m.setInterchangeAmount(charges.interchange());
            m.setInterchangeDirection(charges.interchange().signum() > 0 ? charges.direction() : null);
            m.setSchemeFeeAmount(charges.schemeFee());
        }
        movements.save(m);
        audit(m);
        return result(m, false);
    }

    /**
     * A cleared prepaid purchase reversed: the opposite of its clearing for {@code amount}, with this refund's share
     * of interchange and fees. Any number of refunds per clearing until the cleared amount is used up (backlog 15d,
     * decision D1a); the shares are cumulative — pro rata of everything refunded so far less what earlier refunds
     * reversed (HALF_UP) — so they add up exactly to the clearing's figures once it is fully refunded. The clearing
     * row is locked while a refund is decided, so two refunds are serialised.
     */
    @Transactional
    public PrepaidMovementResult refund(String clearingReference, PrepaidRefundRequest req, String actor) {
        String refundReference = req.refundReference().trim();
        lock(refundReference);
        String currency = upper(req.currencyCode());
        var existing = movements.findByReference(refundReference);
        if (existing.isPresent()) {
            PrepaidCardMovement m = existing.get();
            boolean same = m.getType() == PrepaidCardMovement.Type.REFUND && clearingReference.equals(m.getOriginalReference())
                    && req.amount() != null && m.getAmount().compareTo(req.amount()) == 0;
            return same ? refundResult(m, true) : PrepaidMovementResult.declined(refundReference, "REFERENCE_CONFLICT");
        }
        PrepaidCardMovement clearing = movements.findByReferenceForUpdate(clearingReference).orElse(null);
        if (clearing == null || clearing.getType() != PrepaidCardMovement.Type.CLEARING) {
            return PrepaidMovementResult.declined(refundReference, "CLEARING_NOT_FOUND");
        }
        if (!clearing.getCurrencyCode().equals(currency)) {
            return PrepaidMovementResult.declined(refundReference, "CURRENCY_MISMATCH");
        }
        String invalid = invalidAmount(req.amount(), currency);
        if (invalid != null) return PrepaidMovementResult.declined(refundReference, invalid);
        BigDecimal refundedBefore = clearing.getRefundedAmount() == null ? BigDecimal.ZERO : clearing.getRefundedAmount();
        BigDecimal refundedAfter = refundedBefore.add(req.amount());
        if (refundedAfter.compareTo(clearing.getAmount()) > 0) {
            return PrepaidMovementResult.declined(refundReference, "AMOUNT_EXCEEDS_CLEARED");
        }
        BigDecimal interchangeBefore = BigDecimal.ZERO;
        BigDecimal feeBefore = BigDecimal.ZERO;
        for (PrepaidCardMovement earlier : movements.findRefundsOf(clearingReference)) {
            if (earlier.getInterchangeAmount() != null) interchangeBefore = interchangeBefore.add(earlier.getInterchangeAmount());
            if (earlier.getSchemeFeeAmount() != null) feeBefore = feeBefore.add(earlier.getSchemeFeeAmount());
        }
        int digits = digits(currency);
        BigDecimal interchange = proRata(clearing.getInterchangeAmount(), refundedAfter, clearing.getAmount(), digits)
                .subtract(interchangeBefore).max(BigDecimal.ZERO);
        BigDecimal fee = proRata(clearing.getSchemeFeeAmount(), refundedAfter, clearing.getAmount(), digits)
                .subtract(feeBefore).max(BigDecimal.ZERO);
        CardCharges reversed = new CardCharges(interchange, clearing.getInterchangeDirection(), fee);
        String journal = accountGlPosting.postPrepaidCardClearing(req.amount(), currency, reversed, true, LocalDate.now(),
                "Prepaid card refund " + refundReference + " of " + clearingReference, refundReference, clearing.getCardId());

        clearing.setRefundedAmount(refundedAfter);
        clearing.setRefundReference(refundReference);
        movements.save(clearing);
        PrepaidCardMovement m = movement(refundReference, PrepaidCardMovement.Type.REFUND, req.amount(), currency, journal, actor);
        m.setCardId(clearing.getCardId());
        m.setScheme(clearing.getScheme());
        m.setOriginalReference(clearingReference);
        m.setInterchangeAmount(interchange.signum() > 0 ? interchange : null);
        m.setInterchangeDirection(interchange.signum() > 0 ? clearing.getInterchangeDirection() : null);
        m.setSchemeFeeAmount(fee.signum() > 0 ? fee : null);
        movements.save(m);
        audit(m);
        return refundResult(m, false);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void lock(String reference) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { }, "prepaid-card:" + reference);
    }

    private static PrepaidCardMovement movement(String reference, PrepaidCardMovement.Type type, BigDecimal amount,
                                                String currency, String journal, String actor) {
        PrepaidCardMovement m = new PrepaidCardMovement();
        m.setId(UUID.randomUUID());
        m.setReference(reference);
        m.setType(type);
        m.setAmount(amount);
        m.setCurrencyCode(currency);
        m.setJournalTransactionId(journal);
        m.setCreatedBy(actor);
        return m;
    }

    private void audit(PrepaidCardMovement m) {
        auditLogService.log("PREPAID_CARD", m.getId().toString(), "PREPAID_CARD_" + m.getType().name(), null,
                "reference=" + m.getReference() + " amount=" + m.getAmount() + " " + m.getCurrencyCode()
                        + (m.getCardId() == null ? "" : " card=" + m.getCardId()));
    }

    private static PrepaidMovementResult result(PrepaidCardMovement m, boolean repeat) {
        return new PrepaidMovementResult(true, repeat, m.getReference(), null, m.getJournalTransactionId(),
                m.getInterchangeAmount(), m.getInterchangeDirection() == null ? null : m.getInterchangeDirection().name(),
                m.getSchemeFeeAmount(), null, null);
    }

    private static PrepaidMovementResult refundResult(PrepaidCardMovement m, boolean repeat) {
        return new PrepaidMovementResult(true, repeat, m.getReference(), null, m.getJournalTransactionId(),
                null, m.getInterchangeDirection() == null ? null : m.getInterchangeDirection().name(), null,
                m.getInterchangeAmount() == null ? BigDecimal.ZERO : m.getInterchangeAmount(),
                m.getSchemeFeeAmount() == null ? BigDecimal.ZERO : m.getSchemeFeeAmount());
    }

    /** Null when {@code amount} is a positive amount in the currency's minor units. */
    private static String invalidAmount(BigDecimal amount, String currency) {
        if (currency == null || digitsOrNull(currency) == null) return "UNKNOWN_CURRENCY";
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > digits(currency)) {
            return "INVALID_AMOUNT";
        }
        return null;
    }

    private static boolean invalidCharges(BigDecimal amount, String currency, CardCharges charges) {
        int digits = digits(currency);
        for (BigDecimal v : new BigDecimal[] {charges.interchange(), charges.schemeFee()}) {
            if (v.signum() < 0 || v.stripTrailingZeros().scale() > digits || v.compareTo(amount) > 0) return true;
        }
        return charges.interchange().signum() > 0 && charges.direction() == null;
    }

    /** {@code figure × part / whole}, at the currency's digits (HALF_UP); never more than {@code figure} (as the debit refund). */
    private static BigDecimal proRata(BigDecimal figure, BigDecimal part, BigDecimal whole, int digits) {
        if (figure == null || figure.signum() == 0) return BigDecimal.ZERO;
        if (part.compareTo(whole) >= 0) return figure;
        return figure.multiply(part).divide(whole, digits, RoundingMode.HALF_UP).min(figure);
    }

    private static Integer digitsOrNull(String currency) {
        try {
            return Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The currency's minor-unit digits; 4 for a currency the JDK doesn't know (as the debit refund). */
    private static int digits(String currency) {
        Integer d = digitsOrNull(currency);
        return d == null ? 4 : d;
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }
}
