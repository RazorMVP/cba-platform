package com.cba.card.card;

import com.cba.card.common.CbaException;
import com.cba.card.limits.CardLimit;
import com.cba.card.limits.CardLimitRepository;
import com.cba.card.openbanking.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository            cardRepository;
    private final CardProductRepository     cardProductRepository;
    private final PhysicalCardOrderRepository physicalCardOrderRepository;
    private final CardLimitRepository       cardLimitRepository;

    @Value("${card.pan.hmac-key}")
    private String panHmacKey;

    @Value("${card.simulator.default-currency:840}")
    private String defaultCurrency;

    /** Injected lazily to avoid circular dependency with WebhookDeliveryService. */
    @Lazy @Autowired
    private WebhookService webhookService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Issue Card ────────────────────────────────────────────────────────────

    /**
     * BaaS-facing convenience overload — auto-generates PAN, expiry, CVV.
     * Use this from the Card API when the caller provides only product/customer/entity IDs.
     * In production this data comes from the card bureau; here we generate test values.
     */
    @Transactional
    public Card issueCard(UUID productId, UUID customerId, UUID linkedEntityId, boolean virtual) {
        CardProduct product = cardProductRepository.findById(productId)
                .orElseThrow(() -> CbaException.notFound("CARD_PRODUCT_NOT_FOUND",
                        "Card product not found: " + productId));
        String pan    = product.getBinRangeStart() + String.format("%08d",
                SECURE_RANDOM.nextInt(100_000_000));
        String expiry = YearMonth.now().plusYears(4).format(DateTimeFormatter.ofPattern("yyMM"));
        String cvv    = String.format("%03d", SECURE_RANDOM.nextInt(1000));
        Card saved    = issueCard(productId, customerId, linkedEntityId, virtual, pan, expiry, cvv, defaultCurrency);
        try {
            webhookService.publishEvent("CARD.ISSUED", Map.of("cardId", saved.getId(),
                    "customerId", customerId, "virtual", virtual));
        } catch (Exception e) {
            log.warn("Webhook publish failed for CARD.ISSUED: {}", e.getMessage());
        }
        return saved;
    }

    @Transactional
    public Card issueCard(UUID productId, UUID customerId, UUID linkedEntityId,
                          boolean virtual, String pan, String expiry, String cvv,
                          String currencyCode) {
        CardProduct product = cardProductRepository.findById(productId)
                .orElseThrow(() -> CbaException.notFound("CARD_PRODUCT_NOT_FOUND",
                        "Card product not found: " + productId));

        String panHash   = hashPan(pan);
        if (cardRepository.existsByPanHash(panHash)) {
            throw CbaException.conflict("CARD_ALREADY_EXISTS", "A card with this PAN is already registered");
        }

        Card card = new Card();
        card.setPanEncrypted(pan);  // Jasypt @EnableEncryptableProperties encrypts at persistence
        card.setPanHash(panHash);
        card.setPanPrefix(pan.substring(0, Math.min(8, pan.length())));
        card.setPanSuffix(pan.substring(Math.max(0, pan.length() - 4)));
        card.setExpiryDate(expiry);
        card.setCvvEncrypted(cvv);
        card.setCardType(product.getCardType());
        card.setStatus(virtual ? CardStatus.ACTIVE : CardStatus.ISSUED);
        card.setVirtualFlag(virtual);
        card.setCustomerId(customerId);
        card.setLinkedEntityId(linkedEntityId);
        card.setProduct(product);

        Card saved = cardRepository.save(card);

        // Create default limits
        CardLimit limit = new CardLimit();
        limit.setCard(saved);
        limit.setDailyPurchaseLimit(product.getDefaultDailyLimit());
        limit.setDailyWithdrawalLimit(product.getDefaultDailyLimit().multiply(BigDecimal.valueOf(0.4)));
        limit.setPerTxnLimit(product.getDefaultDailyLimit().multiply(BigDecimal.valueOf(0.2)));
        limit.setMonthlyLimit(product.getDefaultDailyLimit().multiply(BigDecimal.valueOf(4)));
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency code is required when issuing a card — the system is multi-currency");
        }
        limit.setCurrencyCode(currencyCode);
        cardLimitRepository.save(limit);

        // For physical cards, create an order record
        if (!virtual) {
            PhysicalCardOrder order = new PhysicalCardOrder();
            order.setCard(saved);
            order.setStatus("ORDERED");
            physicalCardOrderRepository.save(order);
        }

        log.info("Card issued: id={} type={} customer={} virtual={}",
                saved.getId(), product.getCardType(), customerId, virtual);
        return saved;
    }

    // ── Lifecycle Commands ────────────────────────────────────────────────────

    @Transactional
    public Card executeCommand(UUID cardId, String command) {
        Card card = findById(cardId);
        switch (command.toLowerCase()) {
            case "block"   -> block(card);
            case "unblock" -> unblock(card);
            case "cancel"  -> cancel(card);
            case "activate"-> activate(card);
            case "replace" -> replace(card);
            default        -> throw CbaException.badRequest("INVALID_COMMAND", "Unknown command: " + command);
        }
        Card saved = cardRepository.save(card);
        // Fire card lifecycle webhook
        try {
            String eventType = switch (command.toLowerCase()) {
                case "block"    -> "CARD.BLOCKED";
                case "unblock"  -> "CARD.UNBLOCKED";
                case "activate" -> "CARD.ACTIVATED";
                default         -> null;
            };
            if (eventType != null) {
                webhookService.publishEvent(eventType, Map.of("cardId", cardId, "status", saved.getStatus()));
            }
        } catch (Exception e) {
            log.warn("Webhook publish failed for card command {}: {}", command, e.getMessage());
        }
        return saved;
    }

    private void block(Card card) {
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw CbaException.badRequest("INVALID_STATE", "Only ACTIVE cards can be blocked");
        }
        card.setStatus(CardStatus.BLOCKED);
    }

    private void unblock(Card card) {
        if (card.getStatus() != CardStatus.BLOCKED) {
            throw CbaException.badRequest("INVALID_STATE", "Only BLOCKED cards can be unblocked");
        }
        card.setStatus(CardStatus.ACTIVE);
    }

    private void cancel(Card card) {
        if (card.getStatus() == CardStatus.CANCELLED) {
            throw CbaException.badRequest("INVALID_STATE", "Card is already cancelled");
        }
        card.setStatus(CardStatus.CANCELLED);
    }

    private void activate(Card card) {
        if (card.getStatus() != CardStatus.ACTIVATION_PENDING && card.getStatus() != CardStatus.ISSUED) {
            throw CbaException.badRequest("INVALID_STATE", "Card cannot be activated from state: " + card.getStatus());
        }
        card.setStatus(CardStatus.ACTIVE);
    }

    private void replace(Card card) {
        // Mark current card as cancelled — caller must issue a new card
        card.setStatus(CardStatus.CANCELLED);
        log.info("Card {} marked CANCELLED for replacement", card.getId());
    }

    // ── PIN Management ────────────────────────────────────────────────────────

    @Transactional
    public void incrementPinRetry(UUID cardId) {
        Card card = findById(cardId);
        card.setPinRetryCount((short) (card.getPinRetryCount() + 1));
        if (card.getPinRetryCount() >= 3) {
            card.setStatus(CardStatus.BLOCKED);
            log.warn("Card {} blocked: PIN retry limit exceeded", cardId);
        }
        cardRepository.save(card);
    }

    @Transactional
    public void resetPinRetry(UUID cardId) {
        Card card = findById(cardId);
        card.setPinRetryCount((short) 0);
        card.setPinSet(true);
        cardRepository.save(card);
    }

    // ── CoB — mark expired cards ──────────────────────────────────────────────

    @Transactional
    public int expireCards() {
        String currentYYMM = YearMonth.now().format(DateTimeFormatter.ofPattern("yyMM"));
        List<Card> active = cardRepository.findByStatus(CardStatus.ACTIVE);
        int expired = 0;
        for (Card card : active) {
            if (card.getExpiryDate().compareTo(currentYYMM) < 0) {
                card.setStatus(CardStatus.EXPIRED);
                cardRepository.save(card);
                try {
                    webhookService.publishEvent("CARD.EXPIRED",
                            Map.of("cardId", card.getId(), "expiryDate", card.getExpiryDate()));
                } catch (Exception e) {
                    log.warn("Webhook publish failed for CARD.EXPIRED: {}", e.getMessage());
                }
                expired++;
            }
        }
        log.info("CoB: expired {} cards", expired);
        return expired;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Card findById(UUID id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> CbaException.notFound("CARD_NOT_FOUND", "Card not found: " + id));
    }

    @Transactional(readOnly = true)
    public Card findByPanHash(String pan) {
        return cardRepository.findByPanHash(hashPan(pan))
                .orElseThrow(() -> CbaException.notFound("CARD_NOT_FOUND", "Card not found for PAN"));
    }

    @Transactional(readOnly = true)
    public List<Card> findAll() {
        return cardRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Card> findByCustomer(UUID customerId) {
        return cardRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // ── PAN Hashing ───────────────────────────────────────────────────────────

    public String hashPan(String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(panHmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(pan.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("PAN hashing failed", e);
        }
    }
}
