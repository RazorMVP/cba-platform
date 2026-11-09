package com.cba.cardsettlement;

import com.cba.account.AccountGlPosting;
import com.cba.audit.AuditLogService;
import com.cba.cardsettlement.dto.CardSchemeSettlementRequest;
import com.cba.cardsettlement.dto.CardSchemeSettlementResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

/**
 * Posts a card scheme's net settlement (the cash leg, backlog 15b): what GL 2300 holds for the scheme
 * is paid from the settlement-bank account 1003, or received into it when the scheme owes the bank.
 * card-service sends the scheme's reported net and its own expected net; the backend, which owns the GL,
 * decides whether their difference is within the currency's tolerance ({@link CardSettlementProperties},
 * default 0). Within it the difference posts to 1004 Card Settlement Differences (backlog 15d, decision
 * D7a); above it the request is refused DIFFERENCE_OVER_TOLERANCE and nothing is written.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardSchemeSettlementService {

    /** The card schemes card-service settles with. */
    private static final java.util.Set<String> SCHEMES = java.util.Set.of("VISA", "MASTERCARD", "VERVE", "AFRIGO", "UNIONPAY");

    private final CardSchemeSettlementRepository repository;
    private final AccountGlPosting accountGlPosting;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final CardSettlementProperties properties;

    /**
     * Idempotent on {@code reference}: the same figures again return the first posting; other figures
     * are refused. The journal is dated today — the settlement date is in the description and the
     * record — so a closed period can't block recording a settlement that has happened.
     */
    @Transactional
    public CardSchemeSettlementResult record(CardSchemeSettlementRequest request, String actor) {
        String reference = request.reference().trim();
        // Two requests with one reference: the second waits, then finds the first's row.
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> { }, "card-scheme-settlement:" + reference);
        String scheme = request.scheme().trim().toUpperCase(Locale.ROOT);
        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        BigDecimal amount = request.amount();
        BigDecimal expected = request.expected();
        BigDecimal difference = amount.subtract(expected);

        var existing = repository.findByReference(reference);
        if (existing.isPresent()) {
            CardSchemeSettlement s = existing.get();
            boolean same = s.getScheme().equals(scheme) && s.getSettlementDate().equals(request.settlementDate())
                    && s.getCurrencyCode().equals(currency) && s.getAmount().compareTo(amount) == 0
                    && s.getExpectedAmount().compareTo(expected) == 0;
            return new CardSchemeSettlementResult(same, same, reference, s.getJournalTransactionId(),
                    same ? null : "REFERENCE_CONFLICT", s.getExpectedAmount(), s.getAmount(), s.getDifference());
        }

        String reason = !SCHEMES.contains(scheme) ? "UNKNOWN_SCHEME"
                : !request.settlementDate().isBefore(LocalDate.now()) ? "DATE_NOT_CLOSED"
                : invalid(amount, expected, currency);
        if (reason == null && difference.abs().compareTo(properties.toleranceFor(currency)) > 0) {
            log.warn("Card scheme settlement {} refused: reported {} − expected {} = {} {} is over the tolerance {}",
                    reference, amount, expected, difference, currency, properties.toleranceFor(currency));
            reason = "DIFFERENCE_OVER_TOLERANCE";
        }
        if (reason != null) {
            return new CardSchemeSettlementResult(false, false, reference, null, reason, expected, amount, difference);
        }

        CardSchemeSettlement settlement = new CardSchemeSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setReference(reference);
        settlement.setScheme(scheme);
        settlement.setSettlementDate(request.settlementDate());
        settlement.setCurrencyCode(currency);
        settlement.setAmount(amount);
        settlement.setExpectedAmount(expected);
        settlement.setDifference(difference);
        settlement.setCreatedBy(actor);
        String journal = accountGlPosting.postCardSchemeSettlement(amount, expected, currency, LocalDate.now(),
                "Card scheme settlement " + scheme + " " + request.settlementDate() + " " + currency,
                settlement.getId(), reference);
        settlement.setJournalTransactionId(journal);
        repository.save(settlement);
        auditLogService.log("CARD_SCHEME_SETTLEMENT", settlement.getId().toString(), "CARD_SCHEME_SETTLED", null,
                "scheme=" + scheme + " date=" + request.settlementDate() + " amount=" + amount + " expected=" + expected
                        + " difference=" + difference + " " + currency + " reference=" + reference);
        log.info("Card scheme settlement {} {} {} {} (expected {}, difference {}) posted ({})", scheme,
                request.settlementDate(), amount, currency, expected, difference, journal);
        return new CardSchemeSettlementResult(true, false, reference, journal, null, expected, amount, difference);
    }

    private static String invalid(BigDecimal amount, BigDecimal expected, String currency) {
        if (amount.signum() == 0 && expected.signum() == 0) return "ZERO_AMOUNT";
        int digits;
        try {
            digits = Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN_CURRENCY";
        }
        int max = Math.max(digits, 0);
        return amount.stripTrailingZeros().scale() > max || expected.stripTrailingZeros().scale() > max
                ? "INVALID_AMOUNT" : null;
    }
}
