package com.cba.card.auth;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import com.cba.card.config.RestClientConfig;
import com.cba.card.fraud.FraudContext;
import com.cba.card.fraud.FraudDecision;
import com.cba.card.fraud.FraudEngine;
import com.cba.card.fraud.FraudEvaluationResult;
import com.cba.card.fraud.FraudRuleResult;
import com.cba.card.fraud.FraudScoreLogRepository;
import com.cba.card.openbanking.webhook.WebhookService;
import com.cba.card.wallet.PrepaidWallet;
import com.cba.card.wallet.PrepaidWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CardAuthorizationService} — the authorization decision engine
 * (fraud → balance → approve/decline) called by fep-service. Exercised through the
 * PREPAID card path, whose balance comes from the wallet repository, so no HTTP
 * mocking of the monolith is needed to cover the full decision tree.
 */
@ExtendWith(MockitoExtension.class)
class CardAuthorizationServiceTest {

    @Mock CardService cardService;
    @Mock FraudEngine fraudEngine;
    @Mock FraudScoreLogRepository scoreLogRepository;
    @Mock AuthorizationLogRepository authLogRepository;
    @Mock PrepaidWalletRepository walletRepository;
    @Mock RestTemplate backendRestTemplate;
    @Mock RestClientConfig restClientConfig;
    @Mock WebhookService webhookService;

    @InjectMocks CardAuthorizationService service;

    @BeforeEach
    void setUp() {
        // @InjectMocks does constructor injection for the repos/clients and does NOT
        // also field-inject the @Lazy @Autowired webhookService — wire it explicitly.
        ReflectionTestUtils.setField(service, "webhookService", webhookService);
    }

    private static final String PAN = "9991110000001111";

    private static Card prepaidCard() {
        Card c = new Card();
        c.setId(UUID.randomUUID());
        c.setCardType(CardType.PREPAID);
        c.setStatus(com.cba.card.card.CardStatus.ACTIVE);
        return c;
    }

    private static CardAuthRequest req(BigDecimal amount, String processingCode, boolean pinVerified) {
        return new CardAuthRequest(PAN, processingCode, amount, "840", "000001",
                "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00", "RRN0001", "VISA",
                pinVerified, true, false, null);
    }

    private static FraudEvaluationResult approve() {
        return new FraudEvaluationResult(0, FraudDecision.APPROVE, List.of());
    }

    @Test
    @DisplayName("an unknown card declines with RC=14 and never runs fraud")
    void cardNotFound() {
        when(cardService.findByPanHash(PAN)).thenThrow(CbaException.notFound("CARD_NOT_FOUND", "x"));

        CardAuthResponse resp = service.authorize(req(new BigDecimal("100"), "000000", false));

        assertThat(resp.responseCode()).isEqualTo("14");
        assertThat(resp.approved()).isFalse();
        verify(fraudEngine, never()).evaluate(any());
    }

    @Test
    @DisplayName("a fraud DECLINE (no hard block) returns RC=05")
    void fraudDeclineDoNotHonour() {
        when(cardService.findByPanHash(PAN)).thenReturn(prepaidCard());
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(new FraudEvaluationResult(
                80, FraudDecision.DECLINE, List.of(FraudRuleResult.triggered("DUPLICATE_TRANSACTION", 50))));

        CardAuthResponse resp = service.authorize(req(new BigDecimal("100"), "000000", false));

        assertThat(resp.responseCode()).isEqualTo("05");
        verify(walletRepository, never()).findByCardId(any());
    }

    @Test
    @DisplayName("a fraud DECLINE caused by CARD_BLOCKED returns RC=62 (restricted)")
    void fraudDeclineRestricted() {
        when(cardService.findByPanHash(PAN)).thenReturn(prepaidCard());
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(new FraudEvaluationResult(
                100, FraudDecision.DECLINE, List.of(FraudRuleResult.triggered("CARD_BLOCKED", 100))));

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("62");
    }

    @Test
    @DisplayName("prepaid balance below the amount declines with RC=51 (insufficient funds)")
    void prepaidInsufficientFunds() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(wallet("50")));

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("51");
    }

    @Test
    @DisplayName("a missing prepaid wallet declines with RC=91 (issuer unavailable)")
    void prepaidWalletMissing() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.empty());

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("91");
    }

    @Test
    @DisplayName("sufficient funds + clean fraud approves with RC=00 and an auth code")
    void approved() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(wallet("1000")));

        CardAuthResponse resp = service.authorize(req(new BigDecimal("100"), "000000", false));

        assertThat(resp.approved()).isTrue();
        assertThat(resp.responseCode()).isEqualTo("00");
        assertThat(resp.authorizationCode()).isNotBlank();
    }

    @Test
    @DisplayName("a balance inquiry (310000) is approved and skips the insufficient-funds check")
    void balanceInquiryApproved() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(wallet("500")));

        // amount above balance, but balance-inquiry processing code → still approved
        CardAuthResponse resp = service.authorize(req(new BigDecimal("999999"), "310000", false));

        assertThat(resp.approved()).isTrue();
        assertThat(resp.responseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("a PIN-verified approval resets the card's PIN retry counter")
    void pinVerifiedResetsRetry() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(wallet("1000")));

        service.authorize(req(new BigDecimal("100"), "000000", true));

        verify(cardService).resetPinRetry(card.getId());
    }

    private static PrepaidWallet wallet(String balance) {
        PrepaidWallet w = new PrepaidWallet();
        w.setBalance(new BigDecimal(balance));
        return w;
    }
}
