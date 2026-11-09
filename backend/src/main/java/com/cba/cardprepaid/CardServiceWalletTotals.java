package com.cba.cardprepaid;

import com.cba.accounting.PrepaidWalletTotals;
import com.cba.openbanking.card.CardServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Wallet totals read from card-service with the backend's service token (role CARD_READER). */
@Component
@RequiredArgsConstructor
public class CardServiceWalletTotals implements PrepaidWalletTotals {

    private final CardServiceClient cardServiceClient;

    @Override
    public Optional<Map<String, BigDecimal>> totals() {
        return cardServiceClient.getWalletTotals();
    }
}
