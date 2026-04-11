package com.cba.card.limits;

import com.cba.card.card.CardService;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardLimitService {

    private final CardLimitRepository cardLimitRepository;
    private final CardService         cardService;

    @Transactional(readOnly = true)
    public CardLimit getForCard(UUID cardId) {
        cardService.findById(cardId); // validates card exists
        return cardLimitRepository.findByCardId(cardId)
                .orElseThrow(() -> CbaException.notFound("LIMIT_NOT_FOUND", "No limits found for card: " + cardId));
    }

    @Transactional
    public CardLimit update(UUID cardId, BigDecimal dailyPurchase, BigDecimal dailyWithdrawal,
                            BigDecimal perTxn, BigDecimal monthly) {
        CardLimit limit = getForCard(cardId);
        if (dailyPurchase  != null) limit.setDailyPurchaseLimit(dailyPurchase);
        if (dailyWithdrawal != null) limit.setDailyWithdrawalLimit(dailyWithdrawal);
        if (perTxn         != null) limit.setPerTxnLimit(perTxn);
        if (monthly        != null) limit.setMonthlyLimit(monthly);
        return cardLimitRepository.save(limit);
    }

    /** Check whether a transaction amount exceeds the per-transaction limit. */
    public boolean exceedsPerTxnLimit(UUID cardId, BigDecimal amount) {
        return cardLimitRepository.findByCardId(cardId)
                .map(l -> amount.compareTo(l.getPerTxnLimit()) > 0)
                .orElse(false);
    }
}
