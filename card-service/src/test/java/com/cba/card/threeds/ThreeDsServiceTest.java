package com.cba.card.threeds;

import com.cba.card.card.CardRepository;
import com.cba.card.common.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ThreeDsService#verifyChallenge} — the 3DS OTP challenge state
 * machine. A real {@link CavvGenerator} is used so the OTP-hash round-trip and
 * CAVV generation are genuine, not mocked away.
 */
@ExtendWith(MockitoExtension.class)
class ThreeDsServiceTest {

    private static final String HMAC_KEY = "unit-test-pan-hmac-key";

    @Mock ThreeDsSessionRepository sessionRepo;
    @Mock ThreeDsOtpTokenRepository otpTokenRepo;
    @Mock CardRepository cardRepository;

    private final CavvGenerator cavvGenerator = new CavvGenerator();
    private ThreeDsService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cavvGenerator, "masterKey", "unit-test-cavv-key");
        service = new ThreeDsService(sessionRepo, otpTokenRepo, cardRepository, cavvGenerator);
        ReflectionTestUtils.setField(service, "frictionlessLimits", Map.of("840", 5000L, "default", 5000L));
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10);
        ReflectionTestUtils.setField(service, "maxOtpAttempts", 3);
        ReflectionTestUtils.setField(service, "acsBaseUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(service, "panHmacKey", HMAC_KEY);
    }

    private ThreeDsSession challengeSession() {
        ThreeDsSession s = new ThreeDsSession();
        s.setAcsTransId(UUID.randomUUID());
        s.setCardId(UUID.randomUUID());
        s.setAmount(new BigDecimal("100.00"));
        s.setCurrency("840");
        s.setStatus(ThreeDsStatus.CHALLENGE_REQUIRED);
        s.setChallengeAttempts(0);
        return s;
    }

    private ThreeDsOtpToken tokenFor(String otp, OffsetDateTime expiry) {
        ThreeDsOtpToken t = new ThreeDsOtpToken();
        t.setOtpHash(cavvGenerator.hmacHex(HMAC_KEY.getBytes(StandardCharsets.UTF_8), otp));
        t.setExpiresAt(expiry);
        return t;
    }

    @Test
    @DisplayName("correct OTP authenticates the session and yields a CAVV")
    void correctOtpAuthenticates() {
        ThreeDsSession session = challengeSession();
        ThreeDsOtpToken token = tokenFor("123456", OffsetDateTime.now().plusMinutes(5));
        when(sessionRepo.findByAcsTransId(session.getAcsTransId())).thenReturn(Optional.of(session));
        when(otpTokenRepo.findTopBySessionIdAndVerifiedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.of(token));

        ChallengeVerifyResponse resp = service.verifyChallenge(session.getAcsTransId(), "123456");

        assertThat(resp.status()).isEqualTo("AUTHENTICATED");
        assertThat(resp.authenticationValue()).isNotBlank();
        assertThat(session.getStatus()).isEqualTo(ThreeDsStatus.AUTHENTICATED);
        assertThat(token.isVerified()).isTrue();
    }

    @Test
    @DisplayName("an incorrect OTP fails and consumes an attempt")
    void wrongOtpFails() {
        ThreeDsSession session = challengeSession();
        ThreeDsOtpToken token = tokenFor("123456", OffsetDateTime.now().plusMinutes(5));
        when(sessionRepo.findByAcsTransId(session.getAcsTransId())).thenReturn(Optional.of(session));
        when(otpTokenRepo.findTopBySessionIdAndVerifiedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.of(token));

        ChallengeVerifyResponse resp = service.verifyChallenge(session.getAcsTransId(), "000000");

        assertThat(resp.status()).isEqualTo("FAILED");
        assertThat(session.getChallengeAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("an expired OTP fails")
    void expiredOtpFails() {
        ThreeDsSession session = challengeSession();
        ThreeDsOtpToken token = tokenFor("123456", OffsetDateTime.now().minusMinutes(1));
        when(sessionRepo.findByAcsTransId(session.getAcsTransId())).thenReturn(Optional.of(session));
        when(otpTokenRepo.findTopBySessionIdAndVerifiedFalseOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.of(token));

        assertThat(service.verifyChallenge(session.getAcsTransId(), "123456").status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("an already-authenticated session returns AUTHENTICATED idempotently")
    void alreadyAuthenticated() {
        ThreeDsSession session = challengeSession();
        session.setStatus(ThreeDsStatus.AUTHENTICATED);
        session.setEciIndicator("05");
        session.setAuthenticationValue("existing-cavv");
        when(sessionRepo.findByAcsTransId(session.getAcsTransId())).thenReturn(Optional.of(session));

        ChallengeVerifyResponse resp = service.verifyChallenge(session.getAcsTransId(), "irrelevant");

        assertThat(resp.status()).isEqualTo("AUTHENTICATED");
        assertThat(resp.authenticationValue()).isEqualTo("existing-cavv");
    }

    @Test
    @DisplayName("exceeding the max attempts locks the session")
    void maxAttemptsLocks() {
        ThreeDsSession session = challengeSession();
        session.setChallengeAttempts(3); // == maxOtpAttempts
        when(sessionRepo.findByAcsTransId(session.getAcsTransId())).thenReturn(Optional.of(session));

        ChallengeVerifyResponse resp = service.verifyChallenge(session.getAcsTransId(), "123456");

        assertThat(resp.status()).isEqualTo("LOCKED");
        assertThat(session.getStatus()).isEqualTo(ThreeDsStatus.FAILED);
    }

    @Test
    @DisplayName("an unknown session throws")
    void sessionNotFound() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findByAcsTransId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyChallenge(id, "123456")).isInstanceOf(CbaException.class);
    }
}
