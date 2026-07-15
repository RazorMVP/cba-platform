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
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CardAuthorizationService} — the authorization decision engine
 * (fraud → balance → approve/decline) called by fep-service. The PREPAID path is
 * driven through the wallet repository (no HTTP), while the DEBIT/CREDIT paths stub
 * the monolith balance/credit REST call to cover the approve, insufficient-funds
 * (RC51), and issuer-unavailable (RC91) branches.
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

    // ── DEBIT / CREDIT balance-source paths (issuer-unavailable → RC91) ────────

    private static Card debitCard(UUID linkedEntityId) {
        Card c = new Card();
        c.setId(UUID.randomUUID());
        c.setCardType(CardType.DEBIT);
        c.setStatus(com.cba.card.card.CardStatus.ACTIVE);
        c.setLinkedEntityId(linkedEntityId);
        return c;
    }

    @Test
    @DisplayName("DEBIT card with no linked account → RC=91 (no balance source)")
    void debitNoLinkedAccount() {
        Card card = debitCard(null); // no linked monolith account
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("91");
    }

    @Test
    @DisplayName("DEBIT card declines RC=91 when the monolith balance call fails")
    void debitBackendUnavailable() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        when(backendRestTemplate.getForEntity(any(String.class), any()))
                .thenThrow(new RestClientException("connection refused"));

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("91");
    }

    @Test
    @DisplayName("CREDIT card with no linked loan → RC=91 (no credit line)")
    void creditNoLinkedLoan() {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setCardType(CardType.CREDIT);
        card.setStatus(com.cba.card.card.CardStatus.ACTIVE);
        card.setLinkedEntityId(null);
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("91");
    }

    // ── DEBIT / CREDIT approve + insufficient paths (balance via monolith REST) ──
    // BalanceResponse is package-private so we can stub the REST call with a real
    // balance and exercise the approve / RC51 branches (not just the RC91 failures).

    private static Card creditCard(UUID linkedLoanId) {
        Card c = new Card();
        c.setId(UUID.randomUUID());
        c.setCardType(CardType.CREDIT);
        c.setStatus(com.cba.card.card.CardStatus.ACTIVE);
        c.setLinkedEntityId(linkedLoanId);
        return c;
    }

    /** Stubs the monolith balance/credit REST call to return the given available amount. */
    private void stubBackendBalance(String available) {
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(
                new CardAuthorizationService.BalanceResponse(new BigDecimal(available), "840")))
                .when(backendRestTemplate).getForEntity(any(String.class), any());
    }

    @Test
    @DisplayName("DEBIT card with sufficient account balance approves with RC=00")
    void debitApproved() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendBalance("500.00");

        CardAuthResponse resp = service.authorize(req(new BigDecimal("100"), "000000", false));

        assertThat(resp.approved()).isTrue();
        assertThat(resp.responseCode()).isEqualTo("00");
        assertThat(resp.authorizationCode()).isNotBlank();
    }

    @Test
    @DisplayName("DEBIT card with balance below the amount declines RC=51 (insufficient funds)")
    void debitInsufficientFunds() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendBalance("50.00");

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("51");
    }

    @Test
    @DisplayName("DEBIT balance inquiry (310000) approves even when the balance is below the amount")
    void debitBalanceInquiryApproved() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendBalance("10.00");

        CardAuthResponse resp = service.authorize(req(new BigDecimal("999999"), "310000", false));

        assertThat(resp.approved()).isTrue();
        assertThat(resp.responseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("CREDIT card with available credit ≥ amount approves with RC=00")
    void creditApproved() {
        Card card = creditCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendBalance("1000.00");

        CardAuthResponse resp = service.authorize(req(new BigDecimal("100"), "000000", false));

        assertThat(resp.approved()).isTrue();
        assertThat(resp.responseCode()).isEqualTo("00");
        assertThat(resp.authorizationCode()).isNotBlank();
    }

    @Test
    @DisplayName("CREDIT card with available credit below the amount declines RC=51")
    void creditInsufficientAvailability() {
        Card card = creditCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendBalance("50.00");

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("51");
    }

    private static PrepaidWallet wallet(String balance) {
        PrepaidWallet w = new PrepaidWallet();
        w.setBalance(new BigDecimal(balance));
        return w;
    }

    // ── Reversal (0400) ─────────────────────────────────────────────────────────

    private static final String REV_STAN = "000123";

    private static AuthorizationLog originalAuth(UUID cardId) {
        AuthorizationLog a = new AuthorizationLog();
        a.setCardId(cardId);
        a.setStan(REV_STAN);
        a.setMti("0200");
        a.setAmount(new BigDecimal("100.00"));
        a.setCurrencyCode("840");
        a.setResponseCode("00");
        a.setMerchantId("MERCH01");
        a.setTerminalId("TERM01");
        a.setScheme("VISA");
        return a;
    }

    @Test
    @DisplayName("reversal of a located original records a 0400 and fires AUTHORIZATION.REVERSED")
    void reverse_recordsAndFires() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(originalAuth(card.getId())));

        String rc = service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "0200000123...");

        assertThat(rc).isEqualTo("00");
        verify(authLogRepository).save(argThat(a ->
                "0400".equals(a.getMti()) && "00".equals(a.getResponseCode())));
        verify(webhookService).publishEvent(eq("AUTHORIZATION.REVERSED"), any());
    }

    @Test
    @DisplayName("a duplicate reversal is idempotent — no second 0400, no second event")
    void reverse_idempotent() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(true);

        String rc = service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x");

        assertThat(rc).isEqualTo("00");
        verify(authLogRepository, never()).save(any());
        verify(webhookService, never()).publishEvent(any(), any());
    }

    @Test
    @DisplayName("no locatable original → RC=25, nothing recorded or notified")
    void reverse_noOriginal_returns25() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.empty());

        assertThat(service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x")).isEqualTo("25");
        verify(authLogRepository, never()).save(any());
        verify(webhookService, never()).publishEvent(any(), any());
    }

    @Test
    @DisplayName("unknown card → RC=25, no event")
    void reverse_cardNotFound_returns25() {
        when(cardService.findByPanHash(PAN)).thenThrow(CbaException.notFound("CARD_NOT_FOUND", "x"));

        assertThat(service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x")).isEqualTo("25");
        verify(webhookService, never()).publishEvent(any(), any());
    }
}
