package com.cba.card.wallet;

import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.card.Card;
import com.cba.card.card.CardProduct;
import com.cba.card.card.CardProductRepository;
import com.cba.card.card.CardService;
import com.cba.card.card.CardStatus;
import com.cba.card.card.CardType;
import com.cba.card.common.CbaException;
import com.cba.card.settlement.BackendClearingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Prepaid wallet loads and unloads (backlog 15c). The money comes from (or goes back to) the cardholder's own
 * backend account, and the backend posts it — DR deposit / CR 2500 for a load, the reverse for an unload.
 * Like a scheme report, each movement is claimed first (PENDING, in its own transaction), the backend is
 * called outside any transaction, and the answer is written in a second transaction:
 * <ul>
 *   <li>approved — a load credits the wallet; COMPLETED;</li>
 *   <li>refused — FAILED with the backend's reason; an unload gives its amount back to the wallet;</li>
 *   <li>no answer — stays PENDING; {@link #retryDue()} sends it again with the same reference (the backend is
 *       idempotent on it) until it is answered. It is never failed automatically: the backend may have posted it.</li>
 * </ul>
 * An unload takes the money out of the wallet in the claim, so card spend can't use it while it is in flight.
 *
 * <p>Loads are limited (decision D4a): the claim takes a per-card advisory lock, then refuses a load that would
 * take balance + pending loads above the maximum balance ({@code WALLET_BALANCE_LIMIT}) or today's loads above the
 * daily limit ({@code DAILY_LOAD_LIMIT}). Limits come from the card product, else {@code card.prepaid.limits} for
 * the wallet's currency (NGN: the CBN stored-value limits). Nothing that gives money back — a reversal, a refund,
 * an expired item, a refused unload — is ever limited: it is already the holder's money (CBN 2012 §3.7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final CardService cardService;
    private final PrepaidWalletRepository walletRepository;
    private final WalletMovementRepository movementRepository;
    private final BackendClearingClient backend;
    private final PlatformTransactionManager transactionManager;
    private final CardProductRepository productRepository;
    private final PrepaidLimitProperties limitProperties;

    /** A wallet, its load limits and its last 50 movements. */
    public record WalletView(UUID cardId, UUID walletId, BigDecimal balance, String currencyCode, String status,
                             WalletLimits limits, List<WalletMovement> movements) {}

    /**
     * Load limits in the wallet's currency, major units; {@code maxBalance} / {@code dailyLoad} null = not limited.
     * {@code loadedToday}: loads claimed today (pending or completed) in the limit zone's calendar day.
     */
    public record WalletLimits(BigDecimal maxBalance, BigDecimal dailyLoad, BigDecimal loadedToday) {}

    @Transactional(readOnly = true)
    public WalletView wallet(UUID cardId) {
        Card card = prepaidCard(cardId);
        PrepaidWallet w = walletRepository.findByCardId(card.getId()).orElse(null);
        WalletLimits configured = limits(card.getId(), w == null ? null : w.getCurrencyCode());
        WalletLimits limits = new WalletLimits(configured.maxBalance(), configured.dailyLoad(),
                nz(movementRepository.sumLoadsSince(card.getId(), startOfToday())));
        return new WalletView(card.getId(), w == null ? null : w.getId(), w == null ? BigDecimal.ZERO : w.getBalance(),
                w == null ? null : w.getCurrencyCode(), w == null ? null : w.getStatus(), limits,
                movementRepository.findTop50ByCardIdOrderByCreatedAtDesc(card.getId()));
    }

    /** Wallet balances per currency (major units) — the backend reconciles GL 2500 against it. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> totals() {
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (Object[] row : walletRepository.sumBalancesByCurrency()) {
            totals.put(String.valueOf(row[0]), (BigDecimal) row[1]);
        }
        return totals;
    }

    /**
     * Loads the wallet from {@code accountId}. {@code currencyCode} is needed only for a card with no wallet yet
     * (the wallet is created in it); otherwise it must be the wallet's.
     */
    public WalletMovement load(UUID cardId, UUID accountId, BigDecimal amount, String currencyCode, String actor) {
        Card card = loadableCard(cardId);
        PrepaidWallet existing = walletRepository.findByCardId(card.getId()).orElse(null);
        String currency = existing != null ? existing.getCurrencyCode() : alpha(currencyCode);
        if (currency == null) {
            throw CbaException.badRequest("CURRENCY_REQUIRED", "The card has no wallet yet: give the currency to open it in");
        }
        if (currencyCode != null && !currencyCode.isBlank() && !currency.equals(alpha(currencyCode))) {
            throw CbaException.badRequest("CURRENCY_MISMATCH", "The wallet is in " + currency);
        }
        checkAmount(amount, currency);
        if (accountId == null) throw CbaException.badRequest("ACCOUNT_REQUIRED", "Choose the account to load from");

        WalletMovement claimed = tx().execute(s -> {
            movementRepository.lockCardLoads(card.getId());   // until commit: concurrent loads queue here
            checkLoadLimits(card.getId(), currency, amount);
            return movementRepository.save(
                    movement(card, existing == null ? null : existing.getId(), WalletMovement.LOAD, accountId, amount, currency, actor));
        });
        return send(claimed, card);
    }

    /**
     * Refuses a load over the wallet's limits. Runs under the card's advisory lock, so the sums include every
     * load claimed before this one; FAILED loads moved nothing and don't count.
     */
    private void checkLoadLimits(UUID cardId, String currency, BigDecimal amount) {
        WalletLimits limits = limits(cardId, currency);
        if (limits.maxBalance() != null) {
            BigDecimal balance = walletRepository.findByCardId(cardId).map(PrepaidWallet::getBalance).orElse(BigDecimal.ZERO);
            BigDecimal pending = nz(movementRepository.sumPendingLoads(cardId));
            if (balance.add(pending).add(amount).compareTo(limits.maxBalance()) > 0) {
                throw CbaException.badRequest("WALLET_BALANCE_LIMIT", "The wallet may hold at most "
                        + limits.maxBalance().stripTrailingZeros().toPlainString() + " " + currency + "; it holds "
                        + balance.stripTrailingZeros().toPlainString()
                        + (pending.signum() > 0 ? " with " + pending.stripTrailingZeros().toPlainString() + " still loading" : ""));
            }
        }
        if (limits.dailyLoad() != null) {
            BigDecimal today = nz(movementRepository.sumLoadsSince(cardId, startOfToday()));
            if (today.add(amount).compareTo(limits.dailyLoad()) > 0) {
                throw CbaException.badRequest("DAILY_LOAD_LIMIT", "At most "
                        + limits.dailyLoad().stripTrailingZeros().toPlainString() + " " + currency
                        + " may be loaded a day; " + today.stripTrailingZeros().toPlainString() + " loaded today");
            }
        }
    }

    /** The product's limits, each falling back to the currency's ({@code loadedToday} not filled). */
    private WalletLimits limits(UUID cardId, String currency) {
        CardProduct product = productRepository.findByCardId(cardId).orElse(null);
        PrepaidLimitProperties.Limit byCurrency = limitProperties.forCurrency(currency);
        BigDecimal max = product != null && product.getMaxWalletBalance() != null ? product.getMaxWalletBalance()
                : byCurrency == null ? null : byCurrency.getMaxBalance();
        BigDecimal daily = product != null && product.getDailyWalletLoad() != null ? product.getDailyWalletLoad()
                : byCurrency == null ? null : byCurrency.getDailyLoad();
        return new WalletLimits(max, daily, null);
    }

    /** Midnight today in the limit zone. */
    private OffsetDateTime startOfToday() {
        ZoneId zone = limitProperties.zone();
        return LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Redeems {@code amount} of the wallet to {@code accountId}. */
    public WalletMovement unload(UUID cardId, UUID accountId, BigDecimal amount, String actor) {
        Card card = prepaidCard(cardId);
        if (accountId == null) throw CbaException.badRequest("ACCOUNT_REQUIRED", "Choose the account to pay into");
        WalletMovement claimed = tx().execute(s -> {
            PrepaidWallet w = walletRepository.findByCardIdForUpdate(card.getId())
                    .orElseThrow(() -> CbaException.badRequest("NO_WALLET", "The card has no wallet"));
            checkAmount(amount, w.getCurrencyCode());
            if (w.getBalance().compareTo(amount) < 0) {
                throw CbaException.badRequest("INSUFFICIENT_WALLET_BALANCE", "The wallet holds " + w.getBalance());
            }
            w.setBalance(w.getBalance().subtract(amount));
            walletRepository.save(w);
            return movementRepository.save(movement(card, w.getId(), WalletMovement.UNLOAD, accountId, amount,
                    w.getCurrencyCode(), actor));
        });
        return send(claimed, card);
    }

    /** PENDING movements whose answer was lost: the same request again (idempotent on the reference). */
    @Scheduled(fixedDelayString = "${card.wallet.retry-ms:60000}", initialDelayString = "${card.wallet.retry-initial-ms:60000}")
    public void retryDue() {
        for (WalletMovement m : movementRepository.findDue(OffsetDateTime.now())) {
            try {
                send(m, cardService.findById(m.getCardId()));
            } catch (RuntimeException e) {
                log.warn("Wallet movement {} retry failed: {}", m.getReference(), e.getMessage());
            }
        }
    }

    private WalletMovement send(WalletMovement m, Card card) {
        BackendClearingClient.PrepaidPost post = WalletMovement.LOAD.equals(m.getMovementType())
                ? backend.prepaidLoad(m.getReference(), card.getCustomerId(), card.getId(), m.getAccountId(), m.getAmount(), m.getCurrencyCode())
                : backend.prepaidUnload(m.getReference(), card.getCustomerId(), card.getId(), m.getAccountId(), m.getAmount(), m.getCurrencyCode());
        try {
            return tx().execute(s -> finish(m.getId(), card, post));
        } catch (DataIntegrityViolationException e) {
            // Two first loads for one card raced to create its wallet: the backend has posted this one; the
            // next retry (same reference, a repeat on the backend) credits the wallet the other one created.
            log.warn("Wallet movement {} posted but not applied yet: {}", m.getReference(), e.getMessage());
            return movementRepository.findById(m.getId()).orElse(m);
        }
    }

    private WalletMovement finish(UUID movementId, Card card, BackendClearingClient.PrepaidPost post) {
        WalletMovement m = movementRepository.findByIdForUpdate(movementId).orElseThrow();
        if (!WalletMovement.PENDING.equals(m.getStatus())) return m;   // answered by another attempt
        if (post == null) {
            m.setAttempts(m.getAttempts() + 1);
            m.setNextRetryAt(OffsetDateTime.now().plusSeconds(Math.min(1800L, 60L << Math.min(m.getAttempts(), 5))));
            if (m.getAttempts() >= 10) {
                log.error("Wallet movement {} unanswered after {} attempts — check the backend", m.getReference(), m.getAttempts());
            }
            return movementRepository.save(m);
        }
        boolean load = WalletMovement.LOAD.equals(m.getMovementType());
        if (!post.approved()) {
            m.setStatus(WalletMovement.FAILED);
            m.setFailureReason(post.reason());
            if (!load) {
                // The unload took the money out of the wallet at the claim: give it back.
                walletRepository.findByCardIdForUpdate(m.getCardId()).ifPresent(w -> {
                    w.setBalance(w.getBalance().add(m.getAmount()));
                    walletRepository.save(w);
                });
            }
            log.info("Wallet {} {} refused by the backend: {}", m.getMovementType(), m.getReference(), post.reason());
            return movementRepository.save(m);
        }
        if (load) {
            PrepaidWallet w = walletRepository.findByCardIdForUpdate(m.getCardId()).orElseGet(() -> newWallet(card, m.getCurrencyCode()));
            w.setBalance(w.getBalance().add(m.getAmount()));
            PrepaidWallet saved = walletRepository.saveAndFlush(w);
            m.setWalletId(saved.getId());
        }
        m.setStatus(WalletMovement.COMPLETED);
        m.setNextRetryAt(null);
        return movementRepository.save(m);
    }

    private static PrepaidWallet newWallet(Card card, String currency) {
        PrepaidWallet w = new PrepaidWallet();
        w.setCard(card);
        w.setCustomerId(card.getCustomerId());
        w.setCurrencyCode(currency);
        w.setBalance(BigDecimal.ZERO);
        return w;
    }

    private static WalletMovement movement(Card card, UUID walletId, String type, UUID accountId, BigDecimal amount,
                                           String currency, String actor) {
        WalletMovement m = new WalletMovement();
        m.setCardId(card.getId());
        m.setWalletId(walletId);
        m.setMovementType(type);
        m.setReference((WalletMovement.LOAD.equals(type) ? "WLD-" : "WUL-") + UUID.randomUUID());
        m.setAccountId(accountId);
        m.setAmount(amount);
        m.setCurrencyCode(currency);
        m.setStatus(WalletMovement.PENDING);
        m.setNextRetryAt(OffsetDateTime.now().plusMinutes(1));
        m.setCreatedBy(actor);
        return m;
    }

    private Card prepaidCard(UUID cardId) {
        Card card = cardService.findById(cardId);
        if (card.getCardType() != CardType.PREPAID) {
            throw CbaException.badRequest("NOT_A_PREPAID_CARD", "Only prepaid cards have a wallet");
        }
        return card;
    }

    /** A prepaid card that may take money: not cancelled or expired. */
    private Card loadableCard(UUID cardId) {
        Card card = prepaidCard(cardId);
        if (card.getStatus() == CardStatus.CANCELLED || card.getStatus() == CardStatus.EXPIRED) {
            throw CbaException.badRequest("CARD_NOT_LOADABLE", "A " + card.getStatus() + " card can't be loaded");
        }
        return card;
    }

    private static void checkAmount(BigDecimal amount, String currency) {
        Currency ccy = CardAuthorizationService.currency(currency);
        if (amount == null || amount.signum() <= 0 || ccy == null
                || amount.stripTrailingZeros().scale() > Math.max(ccy.getDefaultFractionDigits(), 0)) {
            throw CbaException.badRequest("INVALID_AMOUNT", "The amount must be positive, in the currency's minor units");
        }
    }

    private static String alpha(String code) {
        Currency c = CardAuthorizationService.currency(code);
        return c == null ? null : c.getCurrencyCode().toUpperCase(Locale.ROOT);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}
