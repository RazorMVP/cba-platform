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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
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
    @Mock com.cba.card.settlement.BackendClearingClient clearingClient;
    @Mock com.cba.card.interchange.InterchangeQualificationEngine interchangeEngine;

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
                pinVerified, true, false, null, null);
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
    @DisplayName("a prepaid card never loaded (no wallet) declines RC 51 — no funds, not an outage (backlog 15c)")
    void prepaidWalletMissing() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.empty());

        assertThat(service.authorize(req(new BigDecimal("100"), "000000", false)).responseCode())
                .isEqualTo("51");
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
                "RRN0001", "VISA", false, true, false, null, null))).isNotEqualTo(ref);
        assertThat(CardAuthorizationService.holdReference(card, new CardAuthRequest(PAN, "000000",
                new BigDecimal("2500"), "840", "000001", "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00",
                null, "VISA", false, true, false, null, null))).isNotEqualTo(ref);
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
                "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false, null, null);
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
                "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false, null, null);
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
        when(settlementService.reversePrepaidItem(original.getId(), new BigDecimal("3000"), true))
                .thenReturn(new SettlementService.PrepaidItemReversal(SettlementService.PrepaidItemReversal.Outcome.NOT_CLEARED, null));

        assertThat(service.reverse(PAN, new BigDecimal("3000"), REV_STAN, "x")).isEqualTo("00");
        assertThat(w.getBalance()).isEqualByComparingTo("50.00");
        verifyNoInteractions(clearingClient);   // not cleared yet: nothing on the backend
        verify(settlementService, never()).markReversed(any());
    }

    private AuthorizationLog prepaidOriginal(Card card) {
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setWalletDebit(new BigDecimal("30.00"));
        original.setAmount(new BigDecimal("3000"));
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        return original;
    }

    @Test
    @DisplayName("prepaid reversal after clearing: the backend refunds first (DR 2300 / CR 2500), then the wallet is credited")
    void reverse_prepaidCleared_refundsThenRestores() {
        Card card = prepaidCard();
        AuthorizationLog original = prepaidOriginal(card);
        PrepaidWallet w = wallet("20.00");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        when(settlementService.reversePrepaidItem(original.getId(), new BigDecimal("3000"), true))
                .thenReturn(new SettlementService.PrepaidItemReversal(SettlementService.PrepaidItemReversal.Outcome.CLEARED, "PPD-abc"));
        when(clearingClient.prepaidRefund(eq("PPD-abc"), anyString(), eq(new BigDecimal("30.00")), eq("USD")))
                .thenReturn(new com.cba.card.settlement.BackendClearingClient.PrepaidPost(true, false, "R", null, "J",
                        null, "EARNED", null, new BigDecimal("0.30"), new BigDecimal("0.05")));

        assertThat(service.reverse(PAN, new BigDecimal("3000"), REV_STAN, "x")).isEqualTo("00");
        assertThat(w.getBalance()).isEqualByComparingTo("50.00");
        verify(settlementService).recordRefund(eq(original.getId()), startsWith("REV-"), eq(new BigDecimal("3000")), any());
    }

    @Test
    @DisplayName("prepaid reversal after clearing with the backend unreachable → 96, wallet untouched, nothing recorded")
    void reverse_prepaidCleared_backendDown() {
        Card card = prepaidCard();
        AuthorizationLog original = prepaidOriginal(card);
        when(settlementService.reversePrepaidItem(original.getId(), new BigDecimal("3000"), true))
                .thenReturn(new SettlementService.PrepaidItemReversal(SettlementService.PrepaidItemReversal.Outcome.CLEARED, "PPD-abc"));
        when(clearingClient.prepaidRefund(any(), any(), any(), any())).thenReturn(null);

        assertThat(service.reverse(PAN, new BigDecimal("3000"), REV_STAN, "x")).isEqualTo("96");
        verify(walletRepository, never()).save(any());
        verify(authLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("prepaid reversal while the item is being cleared → 96 (retry)")
    void reverse_prepaidInClearing() {
        Card card = prepaidCard();
        AuthorizationLog original = prepaidOriginal(card);
        when(settlementService.reversePrepaidItem(original.getId(), new BigDecimal("3000"), true))
                .thenReturn(new SettlementService.PrepaidItemReversal(SettlementService.PrepaidItemReversal.Outcome.IN_CLEARING, null));

        assertThat(service.reverse(PAN, new BigDecimal("3000"), REV_STAN, "x")).isEqualTo("96");
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("prepaid reversal of an expired item: the wallet already had it back — not given twice")
    void reverse_prepaidExpired() {
        Card card = prepaidCard();
        AuthorizationLog original = prepaidOriginal(card);
        when(settlementService.reversePrepaidItem(original.getId(), new BigDecimal("3000"), true))
                .thenReturn(new SettlementService.PrepaidItemReversal(SettlementService.PrepaidItemReversal.Outcome.ALREADY_RETURNED, null));

        assertThat(service.reverse(PAN, new BigDecimal("3000"), REV_STAN, "x")).isEqualTo("00");
        verify(walletRepository, never()).save(any());
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
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0002", "VISA", true, true, true, null, null);

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
        verify(settlementService).addAuthorization(any(AuthorizationLog.class), eq(card), any(String.class), eq(true),
                any(com.cba.card.interchange.IssuerCharges.class));
    }

    @Test
    @DisplayName("15b: 0200 ATM cash sends the interchange the bank pays (PAID) and its fees with card-debits")
    void atm0200_sendsPaidInterchange() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        when(interchangeEngine.quote(eq("VISA"), eq(card.getCardType()), eq("010000"), eq("CHIP"), eq("6011"), any(), any()))
                .thenReturn(new com.cba.card.interchange.IssuerCharges("VISA", new BigDecimal("2.00"),
                        com.cba.card.interchange.InterchangeDirection.PAID, new BigDecimal("0.24"), "TEST"));
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                true, UUID.randomUUID(), "ref", "CAPTURED", null, BigDecimal.ZERO,
                new BigDecimal("2.00"), "PAID", new BigDecimal("0.24"), null, null))))
                .when(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), any(HttpEntity.class),
                        any(ParameterizedTypeReference.class));
        CardAuthRequest atm = new CardAuthRequest(PAN, "010000", new BigDecimal("20000"), "840", "000002",
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0002", "VISA", true, true, true, null, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("00");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), captor.capture(),
                any(ParameterizedTypeReference.class));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat((BigDecimal) body.get("interchangeAmount")).isEqualByComparingTo("2.00");
        assertThat(body.get("interchangeDirection")).isEqualTo("PAID");
        assertThat((BigDecimal) body.get("schemeFeeAmount")).isEqualByComparingTo("0.24");
        assertThat(body.get("scheme")).isEqualTo("VISA");
        verify(settlementService).addAuthorization(any(AuthorizationLog.class), eq(card), any(String.class), eq(true),
                org.mockito.ArgumentMatchers.<com.cba.card.interchange.IssuerCharges>argThat(
                        c -> c.interchange().compareTo(new BigDecimal("2.00")) == 0));
    }

    @Test
    @DisplayName("15b: an interchange engine failure never declines — the 0200 still debits, with no fees")
    void engineFailure_stillApproves() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        when(interchangeEngine.quote(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("rates unavailable"));
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                true, UUID.randomUUID(), "ref", "CAPTURED", null, BigDecimal.ZERO))))
                .when(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), any(HttpEntity.class),
                        any(ParameterizedTypeReference.class));
        CardAuthRequest atm = new CardAuthRequest(PAN, "010000", new BigDecimal("20000"), "840", "000003",
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0003", "VISA", true, true, true, null, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("00");
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(backendRestTemplate).exchange(contains("/card-debits"), eq(HttpMethod.POST), captor.capture(),
                any(ParameterizedTypeReference.class));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) captor.getValue().getBody();
        assertThat(body).doesNotContainKeys("interchangeAmount", "schemeFeeAmount");
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
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0003", "VISA", true, true, true, null, null);

        assertThat(service.authorize(atm).responseCode()).isEqualTo("51");
        verify(settlementService, never()).addAuthorization(any(), any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("an approved prepaid purchase goes into the batch PENDING under a PPD- reference: batch close clears it (backlog 15c)")
    void prepaid_addsCompletedItem() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("500")));

        assertThat(service.authorize(req(new BigDecimal("2500"), "000000", false)).responseCode()).isEqualTo("00");
        verify(settlementService).addAuthorization(any(AuthorizationLog.class), eq(card), startsWith("PPD-"), eq(false));
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
    @DisplayName("15b: a partial reversal of a cleared debit records the refund's reversed interchange and fees "
            + "(a backend from before 15d: no refund block, status REFUNDED)")
    void partialReversalOfCleared_recordsRefundFigures() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        original.setAmount(new BigDecimal("10000"));
        original.setHoldReference("CARD-cleared");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(settlementService.reducePendingItem(original.getId(), new BigDecimal("4000")))
                .thenReturn(com.cba.card.settlement.SettlementService.PendingReduction.NOT_PENDING);
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                true, UUID.randomUUID(), "CARD-cleared", "REFUNDED", null, BigDecimal.ZERO,
                new BigDecimal("1.50"), "EARNED", new BigDecimal("0.13"), new BigDecimal("0.60"), new BigDecimal("0.05")))))
                .when(backendRestTemplate).exchange(contains("/release"), eq(HttpMethod.POST), any(),
                        any(ParameterizedTypeReference.class));

        assertThat(service.reverse(PAN, new BigDecimal("4000"), REV_STAN, "x")).isEqualTo("00");

        verify(settlementService).recordRefund(eq(original.getId()), startsWith("REV-"), eq(new BigDecimal("4000")),
                org.mockito.ArgumentMatchers.<com.cba.card.interchange.IssuerCharges>argThat(c ->
                        c.interchange().compareTo(new BigDecimal("0.60")) == 0
                                && c.schemeFee().compareTo(new BigDecimal("0.05")) == 0
                                && c.direction() == com.cba.card.interchange.InterchangeDirection.EARNED));
    }

    @Test
    @DisplayName("15d: a second partial reversal (its own STAN, DE90 naming the original) refunds again — the backend's "
            + "refund block, keyed by this reversal, is what card-service records; the hold stays CAPTURED")
    void secondPartialReversal_recordsFromRefundBlock() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), "000777", "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        original.setAmount(new BigDecimal("10000"));
        original.setHoldReference("CARD-twice");
        // DE90: original MTI 0100 + original STAN (REV_STAN) + date …
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(settlementService.reducePendingItem(original.getId(), new BigDecimal("3000")))
                .thenReturn(com.cba.card.settlement.SettlementService.PendingReduction.NOT_PENDING);
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        String key = CardAuthorizationService.reversalReference("CARD-twice", "000777", new BigDecimal("3000"));
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                true, UUID.randomUUID(), "CARD-twice", "CAPTURED", null, BigDecimal.ZERO,
                new BigDecimal("1.50"), "EARNED", new BigDecimal("0.13"), new BigDecimal("1.05"), new BigDecimal("0.09"),
                new BigDecimal("70.00"), new CardAuthorizationService.BackendHold.Refund(key, new BigDecimal("30.00"),
                        new BigDecimal("0.45"), new BigDecimal("0.04"), false)))))
                .when(backendRestTemplate).exchange(contains("/release"), eq(HttpMethod.POST), any(),
                        any(ParameterizedTypeReference.class));

        assertThat(service.reverse(PAN, new BigDecimal("3000"), "000777", "0100" + REV_STAN + "1113120000")).isEqualTo("00");

        verify(settlementService).recordRefund(eq(original.getId()), eq(key), eq(new BigDecimal("3000")),
                org.mockito.ArgumentMatchers.<com.cba.card.interchange.IssuerCharges>argThat(c ->
                        c.interchange().compareTo(new BigDecimal("0.45")) == 0      // this refund's share, not the totals
                                && c.schemeFee().compareTo(new BigDecimal("0.04")) == 0));
    }

    @Test
    @DisplayName("15d: a refund block for another key (not this reversal's) records nothing")
    void refundedCharges_onlyForThisKey() {
        var other = new CardAuthorizationService.BackendHold(true, UUID.randomUUID(), "CARD-x", "CAPTURED", null,
                BigDecimal.ZERO, null, null, null, null, null, new BigDecimal("10"),
                new CardAuthorizationService.BackendHold.Refund("REV-other", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, true));
        assertThat(CardAuthorizationService.refundedCharges(other, "REV-mine", "VISA")).isNull();
        assertThat(CardAuthorizationService.refundedCharges(other, "REV-other", "VISA")).isNotNull();
        var released = new CardAuthorizationService.BackendHold(true, UUID.randomUUID(), "CARD-x", "RELEASED", null, BigDecimal.ZERO);
        assertThat(CardAuthorizationService.refundedCharges(released, "REV-mine", "VISA")).isNull();
    }

    @Test
    @DisplayName("15d: the original is found by DE90's STAN; without a well-formed DE90, by the reversal's STAN")
    void originalStan_fromDe90() {
        assertThat(CardAuthorizationService.originalStan("000777", "0100000123111312000000000000000")).isEqualTo("000123");
        assertThat(CardAuthorizationService.originalStan("000777", "0200000456")).isEqualTo("000456");
        assertThat(CardAuthorizationService.originalStan("000777", null)).isEqualTo("000777");
        assertThat(CardAuthorizationService.originalStan("000777", "x")).isEqualTo("000777");
        assertThat(CardAuthorizationService.originalStan("000777", "0100ABCDEF")).isEqualTo("000777");
    }

    @Test
    @DisplayName("15d: a partial reversal for more than is left of a pending purchase → RC 13, nothing released or recorded")
    void reverse_pendingExceeds_13() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setId(UUID.randomUUID());
        original.setAmount(new BigDecimal("10000"));
        original.setHoldReference("CARD-pending");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(settlementService.reducePendingItem(original.getId(), new BigDecimal("5000")))
                .thenReturn(com.cba.card.settlement.SettlementService.PendingReduction.EXCEEDS);

        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x")).isEqualTo("13");
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
    @DisplayName("the backend refuses the release without a reason: fails closed (RC 13), nothing recorded")
    void reverse_refusedWithoutReason_13() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        AuthorizationLog original = originalAuth(card.getId());
        original.setHoldReference("CARD-noreason");
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        doReturn(ResponseEntity.ok(new CardAuthorizationService.Envelope<>(new CardAuthorizationService.BackendHold(
                false, UUID.randomUUID(), "CARD-noreason", "CAPTURED", null, BigDecimal.ZERO))))
                .when(backendRestTemplate).exchange(contains("/release"), eq(HttpMethod.POST), any(),
                        any(ParameterizedTypeReference.class));

        assertThat(service.reverse(PAN, new BigDecimal("10000"), REV_STAN, "x")).isEqualTo("13");
        verify(authLogRepository, never()).save(any());
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
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0004", "VISA", true, true, true, null, null);

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
                "ATM00001", "MERCH01", "ATM", "6011", "CHIP", "00", "RRN0005", "VISA", true, true, true, null, null);

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

    // ── Cardholder controls (backlog 5) ────────────────────────────────────────

    private static CardAuthRequest controlled(String entryMode, String acquiringCountry) {
        return new CardAuthRequest(PAN, "000000", new BigDecimal("100"), "840", "000001",
                "TERM01", "MERCH01", "Shop", "5411", entryMode, "00", "RRN0001", "VISA",
                false, true, false, null, acquiringCountry);
    }

    private Card cardWithClearFraud(CardType type) {
        Card card = prepaidCard();
        card.setCardType(type);
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        return card;
    }

    private void assertDeclinedWithNothingReserved(CardAuthRequest request) {
        assertThat(service.authorize(request).responseCode()).isEqualTo("57");
        verify(walletRepository, never()).findByCardIdForUpdate(any());
        verifyNoInteractions(backendRestTemplate);
    }

    @Test
    @DisplayName("contactless off: a tap (DE22 071) declines 57 before any funds are reserved")
    void contactlessOffDeclinesTap() {
        Card card = cardWithClearFraud(CardType.DEBIT);
        card.setContactlessEnabled(false);
        assertDeclinedWithNothingReserved(controlled("071", null));
    }

    @Test
    @DisplayName("contactless off: contactless magstripe (DE22 911) declines too, chip insert is approved")
    void contactlessOffAllowsChip() {
        Card card = cardWithClearFraud(CardType.PREPAID);
        card.setContactlessEnabled(false);
        assertThat(service.authorize(controlled("911", null)).responseCode()).isEqualTo("57");

        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("1000")));
        assertThat(service.authorize(controlled("051", null)).responseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("card-not-present off: manual entry, e-commerce and a blank DE22 all decline 57")
    void cnpOffDeclinesCardNotPresent() {
        Card card = cardWithClearFraud(CardType.DEBIT);
        card.setCnpEnabled(false);
        assertThat(service.authorize(controlled("010", null)).responseCode()).isEqualTo("57");
        assertThat(service.authorize(controlled("812", null)).responseCode()).isEqualTo("57");
        assertThat(service.authorize(controlled(null, null)).responseCode()).isEqualTo("57");
        verifyNoInteractions(backendRestTemplate);
    }

    @Test
    @DisplayName("card-not-present off: a chip transaction (DE22 051) is approved")
    void cnpOffAllowsCardPresent() {
        Card card = cardWithClearFraud(CardType.PREPAID);
        card.setCnpEnabled(false);
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("1000")));
        assertThat(service.authorize(controlled("051", null)).responseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("international off: an acquirer in the card's home country is approved")
    void internationalOffAllowsDomestic() {
        Card card = cardWithClearFraud(CardType.PREPAID);
        card.setInternationalEnabled(false);
        when(cardService.issuingCountry(card)).thenReturn("USA");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("1000")));
        assertThat(service.authorize(controlled("051", "840")).responseCode()).isEqualTo("00");
    }

    @Test
    @DisplayName("international off: an acquirer abroad (DE19 566 on a USA card) declines 57")
    void internationalOffDeclinesAbroad() {
        Card card = cardWithClearFraud(CardType.DEBIT);
        card.setInternationalEnabled(false);
        when(cardService.issuingCountry(card)).thenReturn("USA");
        assertDeclinedWithNothingReserved(controlled("051", "566"));
    }

    @Test
    @DisplayName("international off fails closed: no DE19, or a code that isn't ISO 3166, declines 57")
    void internationalOffFailsClosed() {
        Card card = cardWithClearFraud(CardType.DEBIT);
        card.setInternationalEnabled(false);
        when(cardService.issuingCountry(card)).thenReturn("USA");
        assertThat(service.authorize(controlled("051", null)).responseCode()).isEqualTo("57");
        assertThat(service.authorize(controlled("051", "999")).responseCode()).isEqualTo("57");
        verifyNoInteractions(backendRestTemplate);
    }

    @Test
    @DisplayName("every control on: no country lookup, and taps and e-commerce go through")
    void allControlsOnChangeNothing() {
        Card card = cardWithClearFraud(CardType.PREPAID);
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(wallet("1000")));
        assertThat(service.authorize(controlled("071", null)).responseCode()).isEqualTo("00");
        assertThat(service.authorize(controlled("812", null)).responseCode()).isEqualTo("00");
        verify(cardService, never()).issuingCountry(any());
    }

    // ── Cardholder billing currency — DE6/DE51 (backlog 17) ──────────────────────────────────────

    /** A EUR purchase (DE4 1000 = 10.00 EUR) that the scheme converted to 11.50 USD (DE6 1150, DE51 840). */
    private static CardAuthRequest eurPurchaseBilledInUsd(String billingAmount, String billingCurrency) {
        return new CardAuthRequest(PAN, "000000", new BigDecimal("1000"), "978", "000001",
                "TERM01", "MERCH01", "Paris Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false,
                null, "250", billingAmount == null ? null : new BigDecimal(billingAmount), billingCurrency, "61150000");
    }

    @Test
    @DisplayName("billing: EUR purchase on a USD debit card holds the DE6 amount in USD and approves")
    void billing_debitHoldInCardCurrency() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);

        CardAuthResponse r = service.authorize(eurPurchaseBilledInUsd("1150", "840"));

        assertThat(r.responseCode()).isEqualTo("00");
        var body = postedHold();
        assertThat((BigDecimal) body.get("amount")).isEqualByComparingTo("11.50");
        assertThat(body.get("currencyCode")).isEqualTo("USD");
        verify(authLogRepository).save(argThat(a -> "978".equals(a.getCurrencyCode())
                && a.getAmount().compareTo(new BigDecimal("1000")) == 0
                && a.getBillingAmount().compareTo(new BigDecimal("1150")) == 0
                && "840".equals(a.getBillingCurrencyCode()) && "61150000".equals(a.getConversionRate())));
    }

    @Test
    @DisplayName("billing: DE6 without DE51, DE51 without DE6, or a fractional DE6 → RC 30, nothing reserved")
    void billing_incomplete_30() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);

        assertThat(service.authorize(eurPurchaseBilledInUsd("1150", null)).responseCode()).isEqualTo("30");
        assertThat(service.authorize(eurPurchaseBilledInUsd(null, "840")).responseCode()).isEqualTo("30");
        assertThat(service.authorize(eurPurchaseBilledInUsd("11.5", "840")).responseCode()).isEqualTo("30");
        verify(backendRestTemplate, never()).exchange(contains("/card-holds"), any(), any(), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("billing: a USD prepaid wallet pays a EUR purchase in its billing amount")
    void billing_prepaidWallet() {
        Card card = prepaidCard();
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        PrepaidWallet w = wallet("50");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));

        assertThat(service.authorize(eurPurchaseBilledInUsd("1150", "840")).responseCode()).isEqualTo("00");
        assertThat(w.getBalance()).isEqualByComparingTo("38.50");
    }

    @Test
    @DisplayName("billing: without DE6 a EUR purchase on a USD account still declines 57 (unchanged)")
    void billing_absent_unchanged() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(false, "CURRENCY_MISMATCH");

        assertThat(service.authorize(eurPurchaseBilledInUsd(null, null)).responseCode()).isEqualTo("57");
        assertThat((String) postedHold().get("currencyCode")).isEqualTo("EUR");
    }

    private AuthorizationLog billedOriginal(Card card) {
        AuthorizationLog original = originalAuth(card.getId());
        original.setMti("0100");
        original.setAmount(new BigDecimal("10000"));          // 100.00 EUR
        original.setCurrencyCode("978");
        original.setBillingAmount(new BigDecimal("11500"));   // 115.00 USD
        original.setBillingCurrencyCode("840");
        original.setHoldReference("CARD-billed");
        when(authLogRepository.existsByCardIdAndStanAndMti(card.getId(), REV_STAN, "0400")).thenReturn(false);
        when(authLogRepository.findFirstByCardIdAndStanAndMtiNotOrderByCreatedAtDesc(card.getId(), REV_STAN, "0400"))
                .thenReturn(Optional.of(original));
        when(restClientConfig.getBackendBaseUrl()).thenReturn("http://localhost:8080");
        return original;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> releaseBody() {
        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(backendRestTemplate).exchange(contains("/card-holds/CARD-billed/release"), eq(HttpMethod.POST),
                captor.capture(), any(ParameterizedTypeReference.class));
        return (java.util.Map<String, Object>) captor.getValue().getBody();
    }

    @Test
    @DisplayName("billing reversal: a partial 0400 without DE6 gives back the pro-rated USD amount")
    void billing_partialReversal_proRata() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        billedOriginal(card);
        when(settlementService.reducePendingItem(any(), any())).thenReturn(SettlementService.PendingReduction.NOT_PENDING);

        // 50.00 EUR of 100.00 EUR → 57.50 USD of 115.00 USD
        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x")).isEqualTo("00");

        var body = releaseBody();
        assertThat((BigDecimal) body.get("amount")).isEqualByComparingTo("57.50");
        assertThat(body.get("currencyCode")).isEqualTo("USD");
    }

    @Test
    @DisplayName("billing reversal: a 0400 with DE6 gives back exactly that; DE51 ≠ the original's → RC 13")
    void billing_reversalWithDe6() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        billedOriginal(card);
        when(settlementService.reducePendingItem(any(), any())).thenReturn(SettlementService.PendingReduction.NOT_PENDING);

        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x", new BigDecimal("5800"), "978"))
                .isEqualTo("13");
        assertThat(service.reverse(PAN, new BigDecimal("5000"), REV_STAN, "x", new BigDecimal("5800"), "840"))
                .isEqualTo("00");
        assertThat((BigDecimal) releaseBody().get("amount")).isEqualByComparingTo("58.00");
    }

    @Test
    @DisplayName("reversalCardMinor: no billing → DE4; billing → DE6 pro-rata, HALF_UP, full at or above the original")
    void reversalCardMinor_rules() {
        AuthorizationLog plain = new AuthorizationLog();
        plain.setAmount(new BigDecimal("1000"));
        assertThat(CardAuthorizationService.reversalCardMinor(plain, new BigDecimal("400"), null)).isEqualByComparingTo("400");

        AuthorizationLog billed = new AuthorizationLog();
        billed.setAmount(new BigDecimal("300"));
        billed.setBillingAmount(new BigDecimal("1000"));
        assertThat(CardAuthorizationService.reversalCardMinor(billed, new BigDecimal("100"), null)).isEqualByComparingTo("333");
        assertThat(CardAuthorizationService.reversalCardMinor(billed, new BigDecimal("200"), null)).isEqualByComparingTo("667");
        assertThat(CardAuthorizationService.reversalCardMinor(billed, new BigDecimal("300"), null)).isEqualByComparingTo("1000");
        assertThat(CardAuthorizationService.reversalCardMinor(billed, null, null)).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("billing checks: DE6 needs DE10; DE6 far from DE4 × DE10, or ≠ DE4 in the same currency → RC 30, before fraud")
    void billing_consistency_30() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);

        CardAuthRequest noRate = new CardAuthRequest(PAN, "000000", new BigDecimal("1000"), "978", "000001",
                "TERM01", "MERCH01", "Paris Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false,
                null, "250", new BigDecimal("1150"), "840", null);
        CardAuthRequest inflated = new CardAuthRequest(PAN, "000000", new BigDecimal("1000"), "978", "000001",
                "TERM01", "MERCH01", "Paris Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false,
                null, "250", new BigDecimal("2000"), "840", "61150000");
        CardAuthRequest sameCurrency = new CardAuthRequest(PAN, "000000", new BigDecimal("1000"), "840", "000001",
                "TERM01", "MERCH01", "Shop", "5411", "CHIP", "00", "RRN0001", "VISA", false, true, false,
                null, "840", new BigDecimal("900"), "840", "61000000");

        assertThat(service.authorize(noRate).responseCode()).isEqualTo("30");
        assertThat(service.authorize(inflated).responseCode()).isEqualTo("30");
        assertThat(service.authorize(sameCurrency).responseCode()).isEqualTo("30");
        verify(fraudEngine, never()).evaluate(any());
    }

    @Test
    @DisplayName("billing: the fraud context carries DE6/DE51 next to DE4/DE49")
    void billing_fraudSeesCardAmount() {
        Card card = debitCard(UUID.randomUUID());
        when(cardService.findByPanHash(PAN)).thenReturn(card);
        when(fraudEngine.evaluate(any(FraudContext.class))).thenReturn(approve());
        stubBackendHold(true, null);

        service.authorize(eurPurchaseBilledInUsd("1150", "840"));

        verify(fraudEngine).evaluate(argThat(c -> c.amount().compareTo(new BigDecimal("1000")) == 0
                && "978".equals(c.currencyCode())
                && c.cardAmount().compareTo(new BigDecimal("1150")) == 0 && "840".equals(c.cardCurrencyCode())));
    }

    @Test
    @DisplayName("billing reversal: a DE6 above what the original moved is capped at it")
    void billing_reversalCapped() {
        AuthorizationLog billed = new AuthorizationLog();
        billed.setAmount(new BigDecimal("10000"));
        billed.setBillingAmount(new BigDecimal("11500"));
        assertThat(CardAuthorizationService.reversalCardMinor(billed, new BigDecimal("10000"), new BigDecimal("99999")))
                .isEqualByComparingTo("11500");
    }
}
