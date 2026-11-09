package com.cba.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CallbackSignature — HMAC over timestamp + body, 5-minute window")
class CallbackSignatureTest {

    private static final String SECRET = "test-callback-secret";
    private static final byte[] BODY = "{\"reference\":\"EXT-1\",\"status\":\"SETTLED\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("the gateway's signature verifies")
    void ok() {
        Instant now = Instant.parse("2026-11-09T12:00:00Z");
        String ts = String.valueOf(now.getEpochSecond());
        assertThat(CallbackSignature.verify(SECRET, ts, CallbackSignature.sign(SECRET, ts, BODY), BODY, now))
                .isEqualTo(CallbackSignature.Verdict.OK);
    }

    @Test
    @DisplayName("a changed body, another secret, or a missing header fails")
    void mismatch() {
        Instant now = Instant.now();
        String ts = String.valueOf(now.getEpochSecond());
        String sig = CallbackSignature.sign(SECRET, ts, BODY);
        byte[] tampered = "{\"reference\":\"EXT-1\",\"status\":\"RETURNED\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(CallbackSignature.verify(SECRET, ts, sig, tampered, now)).isEqualTo(CallbackSignature.Verdict.MISMATCH);
        assertThat(CallbackSignature.verify("other", ts, sig, BODY, now)).isEqualTo(CallbackSignature.Verdict.MISMATCH);
        assertThat(CallbackSignature.verify(SECRET, null, sig, BODY, now)).isEqualTo(CallbackSignature.Verdict.MISSING);
        assertThat(CallbackSignature.verify(SECRET, ts, "deadbeef", BODY, now)).isEqualTo(CallbackSignature.Verdict.MISSING);
        assertThat(CallbackSignature.verify(SECRET, "yesterday", sig, BODY, now)).isEqualTo(CallbackSignature.Verdict.MISSING);
    }

    @Test
    @DisplayName("a signature older (or newer) than 5 minutes is stale — a captured callback can't be replayed")
    void stale() {
        Instant signedAt = Instant.parse("2026-11-09T12:00:00Z");
        String ts = String.valueOf(signedAt.getEpochSecond());
        String sig = CallbackSignature.sign(SECRET, ts, BODY);

        assertThat(CallbackSignature.verify(SECRET, ts, sig, BODY, signedAt.plusSeconds(301)))
                .isEqualTo(CallbackSignature.Verdict.STALE);
        assertThat(CallbackSignature.verify(SECRET, ts, sig, BODY, signedAt.minusSeconds(301)))
                .isEqualTo(CallbackSignature.Verdict.STALE);
        assertThat(CallbackSignature.verify(SECRET, ts, sig, BODY, signedAt.plusSeconds(299)))
                .isEqualTo(CallbackSignature.Verdict.OK);
    }
}
