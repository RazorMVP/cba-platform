package com.cba.notification.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NoOpSmsProvider — default simulated SMS provider")
class NoOpSmsProviderTest {

    private final NoOpSmsProvider provider = new NoOpSmsProvider();

    @Test
    @DisplayName("send accepts every message with a synthetic id")
    void send_accepts() {
        SmsProvider.SmsResult r = provider.send("+254700000000", "hello");
        assertThat(r.accepted()).isTrue();
        assertThat(r.providerMessageId()).startsWith("noop-");
        assertThat(r.errorCode()).isNull();
    }

    @Test
    @DisplayName("send tolerates a null body (length 0)")
    void send_nullBody() {
        assertThat(provider.send("+254700000000", null).accepted()).isTrue();
    }

    @Test
    @DisplayName("providerId is NONE")
    void providerId() {
        assertThat(provider.providerId()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("mask keeps only the last 4 digits and never leaks a full number")
    void mask_hidesPii() {
        assertThat(NoOpSmsProvider.mask("+254700123456")).isEqualTo("****3456");
        assertThat(NoOpSmsProvider.mask("123")).isEqualTo("****");
        assertThat(NoOpSmsProvider.mask(null)).isEqualTo("****");
    }
}
