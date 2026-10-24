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
 * card-service calls this only after the scheme's settlement report matched its expected net.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardSchemeSettlementService {

    private final CardSchemeSettlementRepository repository;
    private final AccountGlPosting accountGlPosting;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

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

        var existing = repository.findByReference(reference);
        if (existing.isPresent()) {
            CardSchemeSettlement s = existing.get();
            boolean same = s.getScheme().equals(scheme) && s.getSettlementDate().equals(request.settlementDate())
                    && s.getCurrencyCode().equals(currency) && s.getAmount().compareTo(request.amount()) == 0;
            return new CardSchemeSettlementResult(same, same, reference, s.getJournalTransactionId(),
                    same ? null : "REFERENCE_CONFLICT");
        }

        String reason = invalid(request.amount(), currency);
        if (reason != null) {
            return new CardSchemeSettlementResult(false, false, reference, null, reason);
        }

        CardSchemeSettlement settlement = new CardSchemeSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setReference(reference);
        settlement.setScheme(scheme);
        settlement.setSettlementDate(request.settlementDate());
        settlement.setCurrencyCode(currency);
        settlement.setAmount(request.amount());
        settlement.setCreatedBy(actor);
        String journal = accountGlPosting.postCardSchemeSettlement(request.amount(), currency, LocalDate.now(),
                "Card scheme settlement " + scheme + " " + request.settlementDate() + " " + currency,
                settlement.getId(), reference);
        settlement.setJournalTransactionId(journal);
        repository.save(settlement);
        auditLogService.log("CARD_SCHEME_SETTLEMENT", settlement.getId().toString(), "CARD_SCHEME_SETTLED", null,
                "scheme=" + scheme + " date=" + request.settlementDate() + " amount=" + request.amount() + " " + currency
                        + " reference=" + reference);
        log.info("Card scheme settlement {} {} {} {} posted ({})", scheme, request.settlementDate(),
                request.amount(), currency, journal);
        return new CardSchemeSettlementResult(true, false, reference, journal, null);
    }

    private static String invalid(BigDecimal amount, String currency) {
        if (amount.signum() == 0) return "ZERO_AMOUNT";
        int digits;
        try {
            digits = Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN_CURRENCY";
        }
        return amount.stripTrailingZeros().scale() > Math.max(digits, 0) ? "INVALID_AMOUNT" : null;
    }
}
