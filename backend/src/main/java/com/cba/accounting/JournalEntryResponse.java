package com.cba.accounting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One journal line as the API returns it: flat, with the GL account's code and name, so
 * a client never has to load the account or a lazy reversal proxy.
 * {@code manual} is true for journals created in the GL (only those can be reversed there).
 */
public record JournalEntryResponse(
        UUID id,
        String transactionId,
        LocalDate transactionDate,
        Instant postedAt,
        UUID glAccountId,
        String glAccountCode,
        String glAccountName,
        JournalEntry.EntryType entryType,
        BigDecimal amount,
        String currencyCode,
        String positionCurrency,
        JournalEntry.EntityType entityType,
        UUID entityId,
        String referenceNumber,
        String description,
        boolean manual,
        boolean reversed,
        UUID reversalOfId) {

    public static JournalEntryResponse from(JournalEntry e) {
        GlAccount gl = e.getGlAccount();
        return new JournalEntryResponse(
                e.getId(), e.getTransactionId(), e.getTransactionDate(), e.getPostedAt(),
                gl.getId(), gl.getGlCode(), gl.getName(),
                e.getEntryType(), e.getAmount(), e.getCurrencyCode(), e.getPositionCurrency(),
                e.getEntityType(), e.getEntityId(), e.getReferenceNumber(), e.getDescription(),
                e.getEntityType() == JournalEntry.EntityType.MANUAL,
                e.isReversed(),
                e.getReversalOf() == null ? null : e.getReversalOf().getId());
    }
}
