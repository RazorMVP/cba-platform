package com.cba.card.wallet;

import com.cba.card.card.Card;
import com.cba.card.card.CardProduct;
import com.cba.card.card.CardProductRepository;
import com.cba.card.card.CardService;
import com.cba.card.card.CardStatus;
import com.cba.card.card.CardType;
import com.cba.card.settlement.BackendClearingClient;
import com.cba.card.settlement.BackendClearingClient.PrepaidPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService — prepaid loads and unloads through the backend (backlog 15c)")
class WalletServiceTest {

    @Mock CardService cardService;
    @Mock PrepaidWalletRepository walletRepository;
    @Mock WalletMovementRepository movementRepository;
    @Mock BackendClearingClient backend;
    @Mock PlatformTransactionManager transactionManager;   // TransactionTemplate runs the callback
    @Mock CardProductRepository productRepository;
    @Spy PrepaidLimitProperties limitProperties = new PrepaidLimitProperties();

    @InjectMocks WalletService service;

    private Card card;
    private final UUID account = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        card = new Card();
        card.setId(UUID.randomUUID());
        card.setCardType(CardType.PREPAID);
        card.setStatus(CardStatus.ACTIVE);
        card.setCustomerId(UUID.randomUUID());
        lenient().when(cardService.findById(card.getId())).thenReturn(card);
        lenient().when(movementRepository.save(any(WalletMovement.class))).thenAnswer(i -> {
            WalletMovement m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            lenient().when(movementRepository.findByIdForUpdate(m.getId())).thenReturn(Optional.of(m));
            return m;
        });
        lenient().when(walletRepository.saveAndFlush(any(PrepaidWallet.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static PrepaidPost approved() {
        return new PrepaidPost(true, false, "R", null, "J", null, null, null, null, null);
    }

    private PrepaidWallet wallet(String balance) {
        PrepaidWallet w = new PrepaidWallet();
        w.setId(UUID.randomUUID());
        w.setCard(card);
        w.setCurrencyCode("USD");
        w.setBalance(new BigDecimal(balance));
        return w;
    }

    @Test
    @DisplayName("first load opens the wallet in the given currency and credits it once the backend posted")
    void firstLoadOpensWallet() {
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.empty());
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.empty());
        when(backend.prepaidLoad(startsWith("WLD-"), eq(card.getCustomerId()), eq(card.getId()), eq(account),
                eq(new BigDecimal("40.00")), eq("USD"))).thenReturn(approved());

        WalletMovement m = service.load(card.getId(), account, new BigDecimal("40.00"), "USD", "teller1");

        assertThat(m.getStatus()).isEqualTo(WalletMovement.COMPLETED);
        verify(walletRepository).saveAndFlush(argThat(w -> w.getBalance().compareTo(new BigDecimal("40.00")) == 0
                && "USD".equals(w.getCurrencyCode()) && card.getCustomerId().equals(w.getCustomerId())));
    }

    @Test
    @DisplayName("load: backend unreachable → stays PENDING with a retry time; refused → FAILED with the reason, wallet untouched")
    void loadPendingAndRefused() {
        PrepaidWallet w = wallet("10.00");
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(w));
        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(null,
                new PrepaidPost(false, false, "R", "INSUFFICIENT_FUNDS", null, null, null, null, null, null));

        WalletMovement pending = service.load(card.getId(), account, new BigDecimal("5.00"), null, "t");
        assertThat(pending.getStatus()).isEqualTo(WalletMovement.PENDING);
        assertThat(pending.getAttempts()).isEqualTo(1);
        assertThat(pending.getNextRetryAt()).isNotNull();

        WalletMovement refused = service.load(card.getId(), account, new BigDecimal("5.00"), null, "t");
        assertThat(refused.getStatus()).isEqualTo(WalletMovement.FAILED);
        assertThat(refused.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(w.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("retry: a PENDING load sent again with the same reference completes it")
    void retryCompletes() {
        PrepaidWallet w = wallet("10.00");
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(w));
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(null);
        WalletMovement pending = service.load(card.getId(), account, new BigDecimal("5.00"), null, "t");
        String reference = pending.getReference();

        when(movementRepository.findDue(any())).thenReturn(List.of(pending));
        when(backend.prepaidLoad(eq(reference), any(), any(), any(), any(), any())).thenReturn(approved());
        service.retryDue();

        assertThat(pending.getStatus()).isEqualTo(WalletMovement.COMPLETED);
        assertThat(w.getBalance()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("unload takes the money out first; a refusal gives it back")
    void unloadRefusedGivesBack() {
        PrepaidWallet w = wallet("30.00");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        when(backend.prepaidUnload(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PrepaidPost(false, false, "R", "ACCOUNT_NOT_ACTIVE", null, null, null, null, null, null));

        WalletMovement m = service.unload(card.getId(), account, new BigDecimal("20.00"), "t");

        assertThat(m.getStatus()).isEqualTo(WalletMovement.FAILED);
        assertThat(w.getBalance()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("unload approved: the wallet stays down by the amount")
    void unloadApproved() {
        PrepaidWallet w = wallet("30.00");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        when(backend.prepaidUnload(any(), any(), any(), any(), any(), any())).thenReturn(approved());

        assertThat(service.unload(card.getId(), account, new BigDecimal("20.00"), "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);
        assertThat(w.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("refusals before anything is claimed: not prepaid, no currency, wrong currency, over the balance, bad amount, cancelled")
    void validation() {
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.load(card.getId(), account, BigDecimal.ONE, null, "t"))
                .hasFieldOrPropertyWithValue("code", "CURRENCY_REQUIRED");
        assertThatThrownBy(() -> service.load(card.getId(), account, new BigDecimal("1.001"), "USD", "t"))
                .hasFieldOrPropertyWithValue("code", "INVALID_AMOUNT");

        PrepaidWallet w = wallet("5.00");
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(w));
        assertThatThrownBy(() -> service.load(card.getId(), account, BigDecimal.ONE, "EUR", "t"))
                .hasFieldOrPropertyWithValue("code", "CURRENCY_MISMATCH");
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        assertThatThrownBy(() -> service.unload(card.getId(), account, new BigDecimal("6.00"), "t"))
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_WALLET_BALANCE");

        card.setStatus(CardStatus.CANCELLED);
        assertThatThrownBy(() -> service.load(card.getId(), account, BigDecimal.ONE, null, "t"))
                .hasFieldOrPropertyWithValue("code", "CARD_NOT_LOADABLE");
        card.setCardType(CardType.DEBIT);
        assertThatThrownBy(() -> service.wallet(card.getId()))
                .hasFieldOrPropertyWithValue("code", "NOT_A_PREPAID_CARD");
        verifyNoInteractions(backend);
    }

    // ── Load limits (decision D4a) ────────────────────────────────────────────

    private PrepaidWallet ngnWallet(String balance) {
        PrepaidWallet w = wallet(balance);
        w.setCurrencyCode("NGN");
        lenient().when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(w));
        lenient().when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        return w;
    }

    private void ngnDefaults() {
        PrepaidLimitProperties.Limit ngn = new PrepaidLimitProperties.Limit();
        ngn.setMaxBalance(new BigDecimal("250000"));
        ngn.setDailyLoad(new BigDecimal("50000"));
        limitProperties.getLimits().put("NGN", ngn);
    }

    private void loadedToday(String amount) {
        lenient().when(movementRepository.sumLoadsSince(eq(card.getId()), any())).thenReturn(new BigDecimal(amount));
    }

    @Test
    @DisplayName("balance limit: balance + this load above the maximum → WALLET_BALANCE_LIMIT, nothing claimed or sent")
    void balanceLimit() {
        ngnDefaults();
        ngnWallet("230000.00");
        loadedToday("0");

        assertThatThrownBy(() -> service.load(card.getId(), account, new BigDecimal("20000.01"), null, "t"))
                .hasFieldOrPropertyWithValue("code", "WALLET_BALANCE_LIMIT");
        verify(movementRepository).lockCardLoads(card.getId());
        verify(movementRepository, never()).save(any());
        verifyNoInteractions(backend);

        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(approved());
        assertThat(service.load(card.getId(), account, new BigDecimal("20000.00"), null, "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);   // exactly at the maximum is allowed
    }

    @Test
    @DisplayName("balance limit counts loads still pending (claimed, not answered yet)")
    void pendingLoadsCounted() {
        ngnDefaults();
        ngnWallet("200000.00");
        loadedToday("0");
        when(movementRepository.sumPendingLoads(card.getId())).thenReturn(new BigDecimal("40000.00"));

        assertThatThrownBy(() -> service.load(card.getId(), account, new BigDecimal("10000.01"), null, "t"))
                .hasFieldOrPropertyWithValue("code", "WALLET_BALANCE_LIMIT")
                .hasMessageContaining("still loading");
        verifyNoInteractions(backend);
    }

    @Test
    @DisplayName("daily limit: today's loads (pending or completed) + this load above it → DAILY_LOAD_LIMIT; FAILED loads aren't in the sum")
    void dailyLimit() {
        ngnDefaults();
        ngnWallet("0.00");
        loadedToday("45000.00");   // the repository sums only non-FAILED loads since midnight in the limit zone

        assertThatThrownBy(() -> service.load(card.getId(), account, new BigDecimal("5000.01"), null, "t"))
                .hasFieldOrPropertyWithValue("code", "DAILY_LOAD_LIMIT");
        verifyNoInteractions(backend);

        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(approved());
        assertThat(service.load(card.getId(), account, new BigDecimal("5000.00"), null, "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);
        // the day starts at midnight in Africa/Lagos
        verify(movementRepository, atLeastOnce()).sumLoadsSince(eq(card.getId()), argThat(since ->
                since.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) && since.getOffset().getTotalSeconds() == 3600));
    }

    @Test
    @DisplayName("a refused (FAILED) load doesn't use up the day: the sum the limit reads leaves it out")
    void failedLoadsNotCounted() {
        ngnDefaults();
        ngnWallet("0.00");
        // First load refused by the backend; the repository's sum (status <> FAILED) stays 0.
        loadedToday("0");
        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(
                new PrepaidPost(false, false, "R", "INSUFFICIENT_FUNDS", null, null, null, null, null, null), approved());

        assertThat(service.load(card.getId(), account, new BigDecimal("50000"), null, "t").getStatus())
                .isEqualTo(WalletMovement.FAILED);
        assertThat(service.load(card.getId(), account, new BigDecimal("50000"), null, "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);
    }

    @Test
    @DisplayName("product override wins in both directions: a full-KYC product lifts the NGN default, another tightens it")
    void productOverride() {
        ngnDefaults();
        PrepaidWallet w = ngnWallet("0.00");
        loadedToday("0");
        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(approved());

        CardProduct fullKyc = new CardProduct();
        fullKyc.setMaxWalletBalance(new BigDecimal("5000000"));
        fullKyc.setDailyWalletLoad(new BigDecimal("1000000"));
        when(productRepository.findByCardId(card.getId())).thenReturn(Optional.of(fullKyc));
        assertThat(service.load(card.getId(), account, new BigDecimal("900000"), null, "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);

        w.setBalance(BigDecimal.ZERO);
        CardProduct tight = new CardProduct();
        tight.setDailyWalletLoad(new BigDecimal("1000"));   // only the daily limit; the balance limit stays NGN's
        when(productRepository.findByCardId(card.getId())).thenReturn(Optional.of(tight));
        assertThatThrownBy(() -> service.load(card.getId(), account, new BigDecimal("1000.01"), null, "t"))
                .hasFieldOrPropertyWithValue("code", "DAILY_LOAD_LIMIT");
        assertThat(service.wallet(card.getId()).limits())
                .isEqualTo(new WalletService.WalletLimits(new BigDecimal("250000"), new BigDecimal("1000"), BigDecimal.ZERO));
    }

    @Test
    @DisplayName("currencies without a configured limit (USD) are not limited; the view shows nulls")
    void otherCurrenciesUnlimited() {
        ngnDefaults();
        PrepaidWallet w = wallet("9999999.00");   // USD
        when(walletRepository.findByCardId(card.getId())).thenReturn(Optional.of(w));
        when(walletRepository.findByCardIdForUpdate(card.getId())).thenReturn(Optional.of(w));
        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any())).thenReturn(approved());

        assertThat(service.load(card.getId(), account, new BigDecimal("1000000"), null, "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);
        verify(movementRepository, never()).sumPendingLoads(any());
        WalletService.WalletLimits limits = service.wallet(card.getId()).limits();
        assertThat(limits.maxBalance()).isNull();
        assertThat(limits.dailyLoad()).isNull();
    }

    @Test
    @DisplayName("unloads are never limited — and leave the load sums alone")
    void unloadNotLimited() {
        ngnDefaults();
        PrepaidWallet w = ngnWallet("300000.00");   // above the maximum already (e.g. after a reversal)
        when(backend.prepaidUnload(any(), any(), any(), any(), any(), any())).thenReturn(approved());

        assertThat(service.unload(card.getId(), account, new BigDecimal("1000.00"), "t").getStatus())
                .isEqualTo(WalletMovement.COMPLETED);
        assertThat(w.getBalance()).isEqualByComparingTo("299000.00");
        verify(movementRepository, never()).lockCardLoads(any());
        verify(movementRepository, never()).sumLoadsSince(any(), any());
    }
}
