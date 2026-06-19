package com.cba.card.limits;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CardLimitService}. {@code CardService} is a concrete class —
 * mocking it exercises the Java 25 Mockito fix added to card-service/pom.xml.
 */
@ExtendWith(MockitoExtension.class)
class CardLimitServiceTest {

    @Mock CardLimitRepository cardLimitRepository;
    @Mock CardService cardService; // concrete class

    @InjectMocks CardLimitService service;

    private static CardLimit limit(BigDecimal perTxn) {
        CardLimit l = new CardLimit();
        l.setDailyPurchaseLimit(new BigDecimal("1000"));
        l.setDailyWithdrawalLimit(new BigDecimal("500"));
        l.setPerTxnLimit(perTxn);
        l.setMonthlyLimit(new BigDecimal("10000"));
        return l;
    }

    @Test
    @DisplayName("getForCard returns the limit when the card and limit exist")
    void getForCardReturnsLimit() {
        UUID cardId = UUID.randomUUID();
        CardLimit expected = limit(new BigDecimal("200"));
        when(cardService.findById(cardId)).thenReturn(new Card());
        when(cardLimitRepository.findByCardId(cardId)).thenReturn(Optional.of(expected));

        assertThat(service.getForCard(cardId)).isSameAs(expected);
    }

    @Test
    @DisplayName("getForCard throws LIMIT_NOT_FOUND when no limit row exists")
    void getForCardThrowsWhenMissing() {
        UUID cardId = UUID.randomUUID();
        when(cardService.findById(cardId)).thenReturn(new Card());
        when(cardLimitRepository.findByCardId(cardId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForCard(cardId)).isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("update changes only the non-null fields and saves")
    void updateOnlyNonNullFields() {
        UUID cardId = UUID.randomUUID();
        CardLimit existing = limit(new BigDecimal("200"));
        when(cardService.findById(cardId)).thenReturn(new Card());
        when(cardLimitRepository.findByCardId(cardId)).thenReturn(Optional.of(existing));
        when(cardLimitRepository.save(any(CardLimit.class))).thenAnswer(i -> i.getArgument(0));

        CardLimit updated = service.update(cardId, new BigDecimal("1500"), null, null, null);

        assertThat(updated.getDailyPurchaseLimit()).isEqualByComparingTo("1500"); // changed
        assertThat(updated.getPerTxnLimit()).isEqualByComparingTo("200");        // untouched
        assertThat(updated.getMonthlyLimit()).isEqualByComparingTo("10000");     // untouched
    }

    @Test
    @DisplayName("exceedsPerTxnLimit is true only when amount exceeds the per-transaction cap")
    void exceedsPerTxnLimit() {
        UUID cardId = UUID.randomUUID();
        when(cardLimitRepository.findByCardId(cardId)).thenReturn(Optional.of(limit(new BigDecimal("100"))));

        assertThat(service.exceedsPerTxnLimit(cardId, new BigDecimal("150"))).isTrue();
        assertThat(service.exceedsPerTxnLimit(cardId, new BigDecimal("50"))).isFalse();
    }

    @Test
    @DisplayName("exceedsPerTxnLimit is false (fail-open) when no limit row exists")
    void exceedsPerTxnLimitNoRow() {
        UUID cardId = UUID.randomUUID();
        when(cardLimitRepository.findByCardId(cardId)).thenReturn(Optional.empty());
        assertThat(service.exceedsPerTxnLimit(cardId, new BigDecimal("999999"))).isFalse();
    }
}
