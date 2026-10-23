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
import com.cba.card.settlement.SettlementService;
import com.cba.card.wallet.PrepaidWallet;
import com.cba.card.wallet.PrepaidWalletRepository;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.core.ParameterizedTypeReference;
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

import static org.mockito.ArgumentMatchers.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock SettlementService settlementService;

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
        verify(walletRepository, never()).findByCardIdForUpdate(any());
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
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("50")));

        // 10000 minor units = 100.00 USD against a 50.00 wallet
        assertThat(service.authorize(req(new BigDecimal("10000"), "000000", false)).responseCode())
                .isEqualTo("51");
    }

    @Test
    @DisplayName("a missing prepaid wallet declines with RC=91 (issuer unavailable)")
    void prepaidWalletMissing() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.empty());

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("91");
    }

    @Test
    @DisplayName("sufficient funds + clean fraud approves with RC=00 and an auth code")
    void approved() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("1000")));

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
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("500")));

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
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("1000")));

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
        when(backendRestTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("connection refused"));

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("91");
    }

    @Test
    @DisplayName("CREDIT card → RC=57: the backend has no credit line to authorize against")
    void creditNoLinkedLoan() {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setCardType(CardType.CREDIT);
        card.setStatus(com.cba.card.card.CardStatus.ACTIVE);
        card.setLinkedEntityId(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("57");
        verifyNoInteractions(backendRestTemplate);
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

    /** Stubs the backend's card-funds call (ApiResponse envelope): USD, debitable, the given amount (major units). */
    private void stubBackendBalance(String available) {
        stubBackendFunds("USD", available, true, null);
    }

    private void stubBackendFunds(String currency, String available, boolean debitAllowed, String reason) {
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendFunds(
                UUID.randomUUID(), currency, new BigDecimal(available), debitAllowed, reason))))
                .when(backendRestTemplate).exchange(any(String.class), eq(HttpMethod.GET), isNull(),
                        any(ParameterizedTypeReference.class));
    }

    /** Stubs the backend's card-hold call (atomic check-and-hold) with the given outcome. */
    private void stubBackendHold(boolean approved, String reason) {
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                approved, approved ? UUID.randomUUID() : null, "ref", approved ? "ACTIVE" : null, reason, BigDecimal.ZERO))))
                .when(backendRestTemplate).exchange(contains("/card-holds"), eq(HttpMethod.POST), any(HttpEntity.class),
                        any(ParameterizedTypeReference.class));
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> postedHold() {
        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(backendRestTemplate).exchange(contains("/card-holds"), eq(HttpMethod.POST), captor.capture(),
                any(ParameterizedTypeReference.class));
        return (java.util.Map<String, Object>) captor.getValue().getBody();
    }

    @Test
    @DisplayName("DE4 is minor units: 2500 (= 25.00 USD) against a 500.00 balance approves")
    void debitMinorUnits_approvesAgainstMajorBalance() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);

        assertThat(service.authorize(req(new BigDecimal("2500"), "000000", false)).responseCode())
                .isEqualTo("00");
        var body = postedHold();
        assertThat((BigDecimal) body.get("amount")).isEqualByComparingTo("25.00");
        assertThat(body.get("currencyCode")).isEqualTo("USD");
        assertThat(body.get("reference")).isEqualTo(CardAuthorizationService.holdReference(card,
                req(new BigDecimal("2500"), "000000", false)));
    }

    @Test
    @DisplayName("hold reference: a retry maps to the same key; another amount, terminal or RRN gets its own")
    void holdReference_bindsTheWholeAuthorization() {
        Card card = debitCard(UUID.randomUUID());
        CardAuthRequest a = req(new BigDecimal("2500"), "000000", false);
        String ref = CardAuthorizationService.holdReference(card, a);

        assertThat(ref).startsWith("CARD-").hasSize(45);
        assertThat(CardAuthorizationService.holdReference(card, req(new BigDecimal("2500"), "000000", false)))
                .isEqualTo(ref);
        assertThat(CardAuthorizationService.holdReference(card, req(new BigDecimal("2501"), "000000", false)))
                .isNotEqualTo(ref);
        assertThat(CardAuthorizationService.holdReference(card, new CardAuthRequest(PAN, "000000",
                new BigDecimal("2500"), "840", "000001", "TERM02", "MERCH01", "Shop", "5411", "CHIP", "00",
                "RRN0001", "VISA", false, true, false, null))).isNotEqualTo(ref);
        assertThat(CardAuthorizationService.holdReference(card, new CardAuthRequest(PAN, "000000",
                new BigDecimal("2500"), "840", "000001", "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00",
                null, "VISA", false, true, false, null))).isNotEqualTo(ref);
    }

    @Test
    @DisplayName("if the authorization's transaction rolls back after the hold was placed, the hold is released")
    void debitHold_releasedOnRollback() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);
        CardAuthRequest r = req(new BigDecimal("2500"), "000000", false);

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.authorize(r).responseCode()).isEqualTo("00");
            for (var sync : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        verify(backendRestTemplate).exchange(
                contains("/card-holds/" + CardAuthorizationService.holdReference(card, r) + "/release"),
                eq(HttpMethod.POST), isNull(), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("backend REFERENCE_CONFLICT (key reused for another amount) declines RC=94, no approval")
    void debitReferenceConflict_94() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(false, "REFERENCE_CONFLICT");

        assertThat(service.authorize(req(new BigDecimal("2500"), "000000", false)).responseCode()).isEqualTo("94");
        verify(authLogRepository).save(argThat(a -> a.getHoldReference() == null));
    }

    @Test
    @DisplayName("DEBIT card with sufficient account balance approves with RC=00")
    void debitApproved() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);

        CardAuthResponse resp = service.authorize(req(new BigDecimal("100"), "000000", false));

        assertThat(resp.approved()).isTrue();
        verify(authLogRepository).save(argThat(a -> a.getHoldReference() != null && a.getHoldReference().startsWith("CARD-")));
        assertThat(resp.responseCode()).isEqualTo("00");
        assertThat(resp.authorizationCode()).isNotBlank();
    }

    @Test
    @DisplayName("DEBIT card with balance below the amount declines RC=51 (insufficient funds)")
    void debitInsufficientFunds() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(false, "INSUFFICIENT_FUNDS");

        // 10000 minor units = 100.00 USD; the backend's hold says insufficient
        assertThat(service.authorize(req(new BigDecimal("10000"), "000000", false)).responseCode())
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
    @DisplayName("DEBIT: an account that can't be debited (frozen, lock-in) → RC=57, not 51")
    void debitNotAllowed() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(false, "ACCOUNT_NOT_ACTIVE");

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("57");
    }

    @Test
    @DisplayName("DEBIT: transaction currency ≠ account currency (EUR 978 vs USD) → RC=57 (no FX here)")
    void debitCurrencyMismatch() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(false, "CURRENCY_MISMATCH");

        CardAuthRequest eur = new CardAuthRequest(PAN, "000000", new BigDecimal("1000"), "978", "000001",
                "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false, null);
        assertThat(service.authorize(eur).responseCode()).isEqualTo("57");
    }

    @Test
    @DisplayName("JPY has no minor unit: DE4 600 reserves 600 JPY")
    void jpyZeroDecimals() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);

        CardAuthRequest jpy = new CardAuthRequest(PAN, "000000", new BigDecimal("600"), "392", "000001",
                "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false, null);
        assertThat(service.authorize(jpy).responseCode()).isEqualTo("00");
        var body = postedHold();
        assertThat((BigDecimal) body.get("amount")).isEqualByComparingTo("600");
        assertThat(body.get("currencyCode")).isEqualTo("JPY");
    }

    @Test
    @DisplayName("PREPAID: DE4 minor units too — 2500 (= 25.00 USD) against a 50.00 wallet approves")
    void prepaidMinorUnits() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        PrepaidWallet w = wallet("50");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));

        assertThat(service.authorize(req(new BigDecimal("2500"), "000000", false)).responseCode()).isEqualTo("00");
        assertThat(w.getBalance()).as("approval reserves: wallet decremented").isEqualByComparingTo("25.00");
        verify(authLogRepository).save(argThat(a -> a.getWalletDebit() != null
                && a.getWalletDebit().compareTo(new BigDecimal("25.00")) == 0));
    }

    @Test
    @DisplayName("currency(): numeric DE49 and alphabetic codes resolve to the same ISO 4217 currency; unknown → null")
    void currencyCodes() {
        assertThat(CardAuthorizationService.currency("840")).isEqualTo(java.util.Currency.getInstance("USD"));
        assertThat(CardAuthorizationService.currency("usd")).isEqualTo(java.util.Currency.getInstance("USD"));
        assertThat(CardAuthorizationService.currency("566")).isEqualTo(java.util.Currency.getInstance("NGN"));
        assertThat(CardAuthorizationService.currency("999999")).isNull();
        assertThat(CardAuthorizationService.currency("ZZZ")).isNull();
        assertThat(CardAuthorizationService.toMajorUnits(new BigDecimal("2500"), java.util.Currency.getInstance("USD")))
                .isEqualByComparingTo("25.00");
        assertThat(CardAuthorizationService.toMajorUnits(new BigDecimal("1500"), java.util.Currency.getInstance("KWD")))
                .isEqualByComparingTo("1.500");
    }

    private static PrepaidWallet wallet(String balance) {
        PrepaidWallet w = new PrepaidWallet();
        w.setBalance(new BigDecimal(balance));
        w.setCurrencyCode("USD");
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


    @Test
    @DisplayName("reversal of an approved debit releases its backend hold")
    void reverse_releasesHold() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setHoldReference("CARD-x-000123-R1");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");

        assertThat(service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x")).isEqualTo("00");
        verify(backendRestTemplate).exchange(contains("/card-holds/CARD-x-000123-R1/release"), eq(HttpMethod.POST),
                any(), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("backend unreachable on release: answers 96 and records no 0400, so a retry can release it")
    void reverse_releaseFails_96_notRecorded() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setHoldReference("CARD-x-000123-R1");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        when(backendRestTemplate.exchange(contains("/release"), eq(HttpMethod.POST), any(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("down"));

        assertThat(service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x")).isEqualTo("96");
        verify(authLogRepository, never()).save(any());
        verify(webhookService, never()).publishEvent(any(), any());
    }

    @Test
    @DisplayName("reversal of an approved prepaid authorization restores the wallet")
    void reverse_restoresWallet() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setWalletDebit(new BigDecimal("30.00"));
        original.setAmount(new BigDecimal("3000"));   // DE4 minor units: 30.00 USD
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        PrepaidWallet w = wallet("20.00");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));

        assertThat(service.reverse(PAN, new BigDecimal("3000"), REV_STAN, "x")).isEqualTo("00");
        assertThat(w.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("a declined original gives nothing back")
    void reverse_declinedOriginal_noGiveBack() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setResponseCode("51");
        original.setHoldReference("CARD-should-not-release");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));

        service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x");
        verifyNoInteractions(backendRestTemplate);
        verify(settlementService, never()).markReversed(any());
    }

    // ── Clearing (backlog 15a) ────────────────────────────────────────────────

    @Test
    @DisplayName("an approved debit purchase (0100) goes into the batch PENDING, to be cleared at close")
    void debitPurchase_addsPendingSettlementItem() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);

        service.authorize(req(new BigDecimal("2500"), "000000", false));

        verify(settlementService).addAuthorization(any(AuthorizationLog.class), eq(card),
                eq(CardAuthorizationService.holdReference(card, req(new BigDecimal("2500"), "000000", false))), eq(false));
    }

    @Test
    @DisplayName("0200 ATM cash debits at once via card-debits (cash=true), no hold; the item starts completed")
    void singleMessage_debitsNow() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                true, UUID.randomUUID(), "ref", "CAPTURED", null, BigDecimal.ZERO))))
                .when(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), any(HttpEntity.class),
                        any(ParameterizedTypeReference.class));
        CardAuthRequest atm = new CardAuthRequest(PAN, "010000", new BigDecimal("20000"), "840", "000002",
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0002", "VISA", true, true, true, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("00");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), captor.capture(),
                any(ParameterizedTypeReference.class));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat((BigDecimal) body.get("amount")).isEqualByComparingTo("200.00");
        assertThat(body.get("cashWithdrawal")).isEqualTo(true);
        verify(backendRestTemplate, never()).exchange(contains("/card-holds"), any(HttpMethod.class), any(),
                any(ParameterizedTypeReference.class));
        verify(settlementService).addAuthorization(any(AuthorizationLog.class), eq(card), any(String.class), eq(true));
    }

    @Test
    @DisplayName("0200 refused by the backend (INSUFFICIENT_FUNDS) declines 51 and adds no item")
    void singleMessage_insufficientFunds() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                false, null, "ref", null, "INSUFFICIENT_FUNDS", BigDecimal.ZERO))))
                .when(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), any(HttpEntity.class),
                        any(ParameterizedTypeReference.class));
        CardAuthRequest atm = new CardAuthRequest(PAN, "010000", new BigDecimal("20000"), "840", "000003",
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0003", "VISA", true, true, true, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("51");
        verify(settlementService, never()).addAuthorization(any(), any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("an approved prepaid purchase goes into the batch completed (the wallet already paid)")
    void prepaid_addsCompletedItem() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("500")));

        assertThat(service.authorize(req(new BigDecimal("2500"), "000000", false)).responseCode()).isEqualTo("00");
        verify(settlementService).addAuthorization(any(AuthorizationLog.class), eq(card), isNull(), eq(true));
    }

    @Test
    @DisplayName("a balance inquiry reserves nothing and adds no item")
    void inquiry_noItem() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("500")));

        service.authorize(req(null, "310000", false));
        verify(settlementService, never()).addAuthorization(any(), any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("a partial reversal sends its own amount (major units) and leaves the settlement item in place")
    void reverse_partialSendsAmount() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setAmount(new BigDecimal("20000"));   // 200.00 USD in minor units
        original.setHoldReference("CARD-partial");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");

        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x")).isEqualTo("00");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(backendRestTemplate).exchange(contains("/card-holds/CARD-partial/release"), eq(HttpMethod.POST),
                captor.capture(), any(ParameterizedTypeReference.class));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat((BigDecimal) body.get("amount")).isEqualByComparingTo("50.00");
        assertThat(body.get("currencyCode")).isEqualTo("USD");
        verify(settlementService, never()).markReversed(any());
    }

    @Test
    @DisplayName("a reversal amount ≤ 0 or with fractional minor units is refused: RC 13, nothing given back")
    void reverse_invalidAmount_13() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setWalletDebit(new BigDecimal("30.00"));
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));

        assertThat(service.reverse(PAN, new BigDecimal("-3000"), REV_STAN, "x")).isEqualTo("13");
        assertThat(service.reverse(PAN, BigDecimal.ZERO, REV_STAN, "x")).isEqualTo("13");
        assertThat(service.reverse(PAN, new BigDecimal("12.5"), REV_STAN, "x")).isEqualTo("13");
        verify(walletRepository, never()).findByCardIdForUpdate(any());
        verify(authLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("partial reversal of a purchase not yet cleared lowers the item to clear — the hold stays, no backend call")
    void reverse_partialUncleared_keepsHold() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        original.setAmount(new BigDecimal("20000"));
        original.setHoldReference("CARD-uncleared");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(settlementService.reducePendingItem(original.getId(), new BigDecimal("5000")))
                .thenReturn(com.cba.card.settlement.SettlementService.PendingReduction.REDUCED);

        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x")).isEqualTo("00");
        verifyNoInteractions(backendRestTemplate);
        verify(settlementService, never()).markReversed(any());
    }

    @Test
    @DisplayName("partial reversal while the purchase is being cleared: 96 (retry), nothing released or recorded")
    void reverse_partialWhileClearing_96() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        original.setAmount(new BigDecimal("20000"));
        original.setHoldReference("CARD-clearing");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(settlementService.reducePendingItem(original.getId(), new BigDecimal("5000")))
                .thenReturn(com.cba.card.settlement.SettlementService.PendingReduction.IN_CLEARING);

        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x")).isEqualTo("96");
        verifyNoInteractions(backendRestTemplate);
        verify(authLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("full reversal while the purchase is being cleared: 96 (retry), nothing released or recorded")
    void reverse_fullWhileClearing_96() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        original.setAmount(new BigDecimal("20000"));
        original.setHoldReference("CARD-clearing-full");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(settlementService.markReversed(original.getId()))
                .thenReturn(com.cba.card.settlement.SettlementService.ItemReversal.IN_CLEARING);

        assertThat(service.reverse(PAN, new BigDecimal("20000"), REV_STAN, "x")).isEqualTo("96");
        verifyNoInteractions(backendRestTemplate);
        verify(authLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("the reversal key is stable for a retry and differs for another STAN or amount")
    void reversalReference_stable() {
        String a = CardAuthorizationService.reversalReference("CARD-h", "000123", new BigDecimal("5000"));
        assertThat(a).startsWith("REV-").hasSize(44)
                .isEqualTo(CardAuthorizationService.reversalReference("CARD-h", "000123", new BigDecimal("5000.00")));
        assertThat(a).isNotEqualTo(CardAuthorizationService.reversalReference("CARD-h", "000124", new BigDecimal("5000")))
                .isNotEqualTo(CardAuthorizationService.reversalReference("CARD-h", "000123", new BigDecimal("4000")));
    }

    @Test
    @DisplayName("the backend refuses the refund (amount over captured): RC 13, nothing recorded")
    void reverse_refundRefused_13() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setHoldReference("CARD-refused");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                false, UUID.randomUUID(), "CARD-refused", "CAPTURED", "AMOUNT_EXCEEDS_CAPTURED", BigDecimal.ZERO))))
                .when(backendRestTemplate).exchange(contains("/release"), eq(HttpMethod.POST), any(),
                        any(ParameterizedTypeReference.class));

        assertThat(service.reverse(PAN, new BigDecimal("10000"), REV_STAN, "x")).isEqualTo("13");
        verify(authLogRepository, never()).save(any());
        // marked before the release; the refusal rolls the reversal's transaction back (CardClearingIntegrationTest)
        verify(settlementService).markReversed(original.getId());
    }

    @Test
    @DisplayName("0200 call times out: card-service releases at once (refunds a debit the backend may have taken)")
    void singleMessage_timeout_compensates() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        when(backendRestTemplate.exchange(contains("/card-debits"), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("read timed out"));
        CardAuthRequest atm = new CardAuthRequest(PAN, "010000", new BigDecimal("20000"), "840", "000004",
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0004", "VISA", true, true, true, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("91");
        verify(backendRestTemplate).exchange(contains("/card-holds/" + CardAuthorizationService.holdReference(card, atm)
                + "/release"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
        verify(authLogRepository).save(argThat(a -> "91".equals(a.getResponseCode()) && a.getHoldReference() == null));
    }

    @Test
    @DisplayName("0200 times out and the compensating release can't reach the backend either: reference kept for reconciliation")
    void singleMessage_timeout_releaseFails_keepsReference() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        when(backendRestTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("down"));
        CardAuthRequest atm = new CardAuthRequest(PAN, "010000", new BigDecimal("20000"), "840", "000005",
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0005", "VISA", true, true, true, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("91");
        String ref = CardAuthorizationService.holdReference(card, atm);
        verify(authLogRepository).save(argThat(a -> "91".equals(a.getResponseCode()) && ref.equals(a.getHoldReference())));
        verify(settlementService, never()).addAuthorization(any(), any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("reversing an approved original marks its settlement item REVERSED")
    void reverse_marksItemReversed() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));

        assertThat(service.reverse(PAN, new BigDecimal("100.00"), REV_STAN, "x")).isEqualTo("00");
        verify(settlementService).markReversed(original.getId());
    }
}
