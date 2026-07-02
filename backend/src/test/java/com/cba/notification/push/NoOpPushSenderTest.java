package com.cba.notification.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NoOpPushSender — default simulated push")
class NoOpPushSenderTest {

    private final NoOpPushSender sender = new NoOpPushSender();

    @Test
    @DisplayName("send accepts with a synthetic id")
    void send_accepts() {
        PushSender.PushResult r = sender.send("device-token-abcdef", "Title", "Body", Map.of("k", "v"));
        assertThat(r.accepted()).isTrue();
        assertThat(r.messageId()).startsWith("noop-");
        assertThat(r.tokenInvalid()).isFalse();
    }

    @Test
    @DisplayName("tolerates null data")
    void send_nullData() {
        assertThat(sender.send("tok123456", "t", "b", null).accepted()).isTrue();
    }

    @Test
    @DisplayName("providerId is NONE")
    void providerId() {
        assertThat(sender.providerId()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("mask never leaks the full token")
    void mask() {
        assertThat(NoOpPushSender.mask("abcdefghijk")).isEqualTo("****fghijk"); // last 6 chars
        assertThat(NoOpPushSender.mask("abc")).isEqualTo("******");
        assertThat(NoOpPushSender.mask(null)).isEqualTo("******");
    }
}
