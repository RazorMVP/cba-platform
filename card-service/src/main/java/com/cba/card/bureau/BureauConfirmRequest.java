package com.cba.card.bureau;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Bureau confirmation callback payload.
 *
 * <p>Sent by the card bureau when cards in a batch have been personalised.
 * A single confirmation can cover the entire batch or a partial subset
 * (bureaus often confirm in production-line order, not batch order).
 *
 * @param batchRef  bureau's own reference for this batch (may differ from our {@code batch_ref})
 * @param items     per-card confirmation entries
 */
public record BureauConfirmRequest(

        String batchRef,

        @NotEmpty @Valid
        List<ItemConfirmation> items

) {

    /**
     * Confirmation entry for a single card.
     *
     * @param cardId        our internal card UUID
     * @param success       {@code true} if personalisation succeeded
     * @param chipSerialNo  unique serial number burned into the EMV chip (populated on success)
     * @param bureauRef     bureau's internal card reference (populated on success)
     * @param failureReason short description if {@code success=false}
     */
    public record ItemConfirmation(
            UUID    cardId,
            boolean success,
            String  chipSerialNo,
            String  bureauRef,
            String  failureReason
    ) {}

    /** Find the confirmation entry for a specific card, or null if not in this payload. */
    public ItemConfirmation findByCardId(UUID cardId) {
        if (items == null) return null;
        return items.stream()
                .filter(i -> cardId.equals(i.cardId()))
                .findFirst()
                .orElse(null);
    }
}
