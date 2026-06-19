package com.cba.card.token;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TokenService} — the simulated EMVCo token vault (DPAN ↔ PAN).
 * {@code CardService} is concrete (Java 25 mock fix); {@code tokenBinPrefix} is a
 * {@code @Value} field, set via reflection.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock TokenVaultRepository tokenVaultRepository;
    @Mock CardService cardService; // concrete class
    @InjectMocks TokenService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tokenBinPrefix", "9999");
    }

    private static Card card() {
        Card c = new Card();
        c.setPanSuffix("1111");
        c.setExpiryDate("2612");
        c.setPanHash("pan-hash");
        c.setPanEncrypted("4111111111111111");
        return c;
    }

    @Test
    @DisplayName("tokenize generates a 16-digit DPAN in the token BIN range, preserving last 4")
    void tokenizeGeneratesDpan() {
        UUID cardId = UUID.randomUUID();
        when(cardService.findById(cardId)).thenReturn(card());
        when(cardService.hashPan(any())).thenReturn("dpan-hash");
        when(tokenVaultRepository.save(any(TokenVault.class))).thenAnswer(i -> i.getArgument(0));

        TokenService.TokenResponse res = service.tokenize(cardId, UUID.randomUUID());

        assertThat(res.dpan()).hasSize(16).startsWith("9999").endsWith("1111");
        assertThat(res.tokenRef()).isNotBlank();
        assertThat(res.expiryDate()).isEqualTo("2612");
    }

    @Test
    @DisplayName("detokenize resolves an ACTIVE token's DPAN back to the real PAN")
    void detokenizeActive() {
        when(cardService.hashPan("9999000000001111")).thenReturn("dpan-hash");
        TokenVault token = new TokenVault();
        token.setStatus("ACTIVE");
        token.setPanHash("pan-hash");
        when(tokenVaultRepository.findByDpanHash("dpan-hash")).thenReturn(Optional.of(token));
        when(cardService.findByPanHash("pan-hash")).thenReturn(card());

        assertThat(service.detokenize("9999000000001111")).isEqualTo("4111111111111111");
    }

    @Test
    @DisplayName("detokenize rejects a non-ACTIVE token")
    void detokenizeInactive() {
        when(cardService.hashPan(any())).thenReturn("dpan-hash");
        TokenVault token = new TokenVault();
        token.setStatus("SUSPENDED");
        when(tokenVaultRepository.findByDpanHash("dpan-hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.detokenize("9999000000001111"))
                .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("detokenize throws when no token matches the DPAN")
    void detokenizeNotFound() {
        when(cardService.hashPan(any())).thenReturn("dpan-hash");
        when(tokenVaultRepository.findByDpanHash("dpan-hash")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detokenize("9999000000001111"))
                .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("suspend sets the token status to SUSPENDED")
    void suspend() {
        TokenVault token = new TokenVault();
        token.setStatus("ACTIVE");
        when(tokenVaultRepository.findByTokenRef("ref-1")).thenReturn(Optional.of(token));

        service.suspend("ref-1");

        assertThat(token.getStatus()).isEqualTo("SUSPENDED");
        verify(tokenVaultRepository).save(token);
    }
}
