package com.cba.card.card;

import com.cba.card.common.CbaException;
import com.cba.card.limits.CardLimitRepository;
import com.cba.card.openbanking.webhook.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CardService} lifecycle state machine, PIN retry blocking,
 * CoB expiry, and PAN hashing. {@code WebhookService} (concrete) is injected via
 * reflection; {@code @Value} fields are set the same way.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock CardRepository cardRepository;
    @Mock CardProductRepository cardProductRepository;
    @Mock PhysicalCardOrderRepository physicalCardOrderRepository;
    @Mock CardLimitRepository cardLimitRepository;
    @Mock WebhookService webhookService;

    @InjectMocks CardService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "panHmacKey", "unit-test-hmac-key");
        ReflectionTestUtils.setField(service, "defaultCurrency", "840");
        // @InjectMocks uses constructor injection for the repos and does NOT also
        // field-inject the @Lazy @Autowired webhookService — wire it explicitly.
        ReflectionTestUtils.setField(service, "webhookService", webhookService);
    }

    private static Card card(CardStatus status) {
        Card c = new Card();
        c.setId(UUID.randomUUID());
        c.setStatus(status);
        c.setExpiryDate("9912");
        return c;
    }

    // ── Lifecycle commands ────────────────────────────────────────────────────

    @Test
    @DisplayName("block: ACTIVE → BLOCKED and fires CARD.BLOCKED")
    void blockActive() {
        Card c = card(CardStatus.ACTIVE);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

        Card result = service.executeCommand(c.getId(), "block");

        assertThat(result.getStatus()).isEqualTo(CardStatus.BLOCKED);
        verify(webhookService).publishEvent(eq("CARD.BLOCKED"), anyMap());
    }

    @Test
    @DisplayName("block: non-ACTIVE card is rejected")
    void blockNonActiveRejected() {
        Card c = card(CardStatus.BLOCKED);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.executeCommand(c.getId(), "block"))
                .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("unblock: BLOCKED → ACTIVE and fires CARD.UNBLOCKED")
    void unblock() {
        Card c = card(CardStatus.BLOCKED);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

        Card result = service.executeCommand(c.getId(), "unblock");

        assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        verify(webhookService).publishEvent(eq("CARD.UNBLOCKED"), anyMap());
    }

    @Test
    @DisplayName("activate: ISSUED → ACTIVE and fires CARD.ACTIVATED")
    void activate() {
        Card c = card(CardStatus.ISSUED);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

        Card result = service.executeCommand(c.getId(), "activate");

        assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        verify(webhookService).publishEvent(eq("CARD.ACTIVATED"), anyMap());
    }

    @Test
    @DisplayName("cancel: ACTIVE → CANCELLED")
    void cancel() {
        Card c = card(CardStatus.ACTIVE);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

        Card result = service.executeCommand(c.getId(), "cancel");

        assertThat(result.getStatus()).isEqualTo(CardStatus.CANCELLED);
    }

    @Test
    @DisplayName("an unknown command is rejected")
    void unknownCommand() {
        Card c = card(CardStatus.ACTIVE);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.executeCommand(c.getId(), "frobnicate"))
                .isInstanceOf(CbaException.class);
    }

    // ── PIN retry ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("incrementPinRetry blocks the card on the 3rd failed attempt")
    void pinRetryBlocksAtThree() {
        Card c = card(CardStatus.ACTIVE);
        c.setPinRetryCount((short) 2);
        when(cardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

        service.incrementPinRetry(c.getId());

        assertThat(c.getPinRetryCount()).isEqualTo((short) 3);
        assertThat(c.getStatus()).isEqualTo(CardStatus.BLOCKED);
    }

    // ── CoB expiry ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("expireCards expires only past-dated ACTIVE cards and fires CARD.EXPIRED")
    void expireCards() {
        Card past = card(CardStatus.ACTIVE);   past.setExpiryDate("0001"); // Jan 2000
        Card future = card(CardStatus.ACTIVE); future.setExpiryDate("9912");
        when(cardRepository.findByStatus(CardStatus.ACTIVE)).thenReturn(List.of(past, future));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

        int expired = service.expireCards();

        assertThat(expired).isEqualTo(1);
        assertThat(past.getStatus()).isEqualTo(CardStatus.EXPIRED);
        assertThat(future.getStatus()).isEqualTo(CardStatus.ACTIVE);
        verify(webhookService).publishEvent(eq("CARD.EXPIRED"), anyMap());
    }

    // ── PAN hashing ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("hashPan is deterministic, 64 hex chars, and distinguishes PANs")
    void hashPan() {
        String h1 = service.hashPan("4111111111111111");
        String h2 = service.hashPan("4111111111111111");
        String other = service.hashPan("5111111111111118");
        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(h1).isNotEqualTo(other);
    }
}
