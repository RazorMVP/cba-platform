package com.cba.card.wallet;

import com.cba.card.card.Card;
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
}
