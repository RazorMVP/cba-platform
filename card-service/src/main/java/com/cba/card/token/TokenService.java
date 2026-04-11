package com.cba.card.token;

import com.cba.card.card.CardService;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Simulated EMVCo Token Service Provider (TSP) — internal token vault.
 *
 * <p>Generates a DPAN (Device PAN) in the token BIN range (9999xx prefix).
 * The DPAN has the same length as the real PAN. When FEP detects a 9999
 * prefix in DE2, it calls {@code /api/v1/internal/detokenize} to resolve
 * the real PAN before authorization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    @Value("${card.token.bin-prefix:9999}")
    private String tokenBinPrefix;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenVaultRepository tokenVaultRepository;
    private final CardService          cardService;

    @Transactional
    public TokenResponse tokenize(UUID cardId, UUID customerId) {
        var card = cardService.findById(cardId);

        String dpan     = generateDpan(card.getPanSuffix());
        String tokenRef = UUID.randomUUID().toString();
        String dpanHash = cardService.hashPan(dpan);
        String panHash  = card.getPanHash();

        TokenVault token = new TokenVault();
        token.setDpanEncrypted(dpan);   // Jasypt encrypts at persistence
        token.setDpanHash(dpanHash);
        token.setPanHash(panHash);
        token.setTokenRef(tokenRef);
        token.setStatus("ACTIVE");
        token.setCustomerId(customerId);
        token.setCardId(cardId);
        token.setExpiresAt(OffsetDateTime.now().plusYears(3));
        tokenVaultRepository.save(token);

        log.info("Token issued: card={} tokenRef={} dpan={}****", cardId, tokenRef,
                dpan.substring(0, 6));
        return new TokenResponse(dpan, card.getExpiryDate(), tokenRef);
    }

    /** Resolve DPAN → real PAN. Called by FEP de-tokenization endpoint. */
    @Transactional(readOnly = true)
    public String detokenize(String dpan) {
        String dpanHash = cardService.hashPan(dpan);
        TokenVault token = tokenVaultRepository.findByDpanHash(dpanHash)
                .orElseThrow(() -> CbaException.notFound("TOKEN_NOT_FOUND",
                        "No active token found for DPAN"));
        if (!"ACTIVE".equals(token.getStatus())) {
            throw CbaException.badRequest("TOKEN_INACTIVE",
                    "Token is " + token.getStatus() + " and cannot be used");
        }
        // Resolve real PAN via card pan_hash → card → decrypt pan_encrypted
        var card = cardService.findByPanHash(token.getPanHash());
        return card.getPanEncrypted(); // Jasypt decrypts on read
    }

    @Transactional
    public void suspend(String tokenRef) {
        updateStatus(tokenRef, "SUSPENDED");
    }

    @Transactional
    public void delete(String tokenRef) {
        updateStatus(tokenRef, "DELETED");
    }

    private void updateStatus(String tokenRef, String status) {
        TokenVault token = tokenVaultRepository.findByTokenRef(tokenRef)
                .orElseThrow(() -> CbaException.notFound("TOKEN_NOT_FOUND", "Token not found: " + tokenRef));
        token.setStatus(status);
        tokenVaultRepository.save(token);
    }

    private String generateDpan(String originalSuffix) {
        // DPAN: tokenBinPrefix (4 digits) + 2 random + 10 random digits = 16 digits total
        // Preserve last 4 to match display expectations
        StringBuilder sb = new StringBuilder(tokenBinPrefix);
        for (int i = 0; i < 8; i++) sb.append(RANDOM.nextInt(10));
        sb.append(originalSuffix.length() == 4 ? originalSuffix : "0000");
        return sb.toString();
    }

    public record TokenResponse(String dpan, String expiryDate, String tokenRef) {}
}
