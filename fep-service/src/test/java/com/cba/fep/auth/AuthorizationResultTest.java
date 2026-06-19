package com.cba.fep.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for the {@link AuthorizationResult} factory methods that map to ISO 8583 DE39/DE38. */
class AuthorizationResultTest {

    @Test
    @DisplayName("approve() yields RC 00, approved=true, and carries the auth code")
    void approve() {
        AuthorizationResult r = AuthorizationResult.approve("A1B2C3");
        assertThat(r.responseCode()).isEqualTo("00");
        assertThat(r.approved()).isTrue();
        assertThat(r.authorizationCode()).isEqualTo("A1B2C3");
        assertThat(r.standIn()).isFalse();
        assertThat(r.availableBalance()).isNull();
    }

    @Test
    @DisplayName("decline() carries the given RC, approved=false, no auth code")
    void decline() {
        AuthorizationResult r = AuthorizationResult.decline("51"); // insufficient funds
        assertThat(r.responseCode()).isEqualTo("51");
        assertThat(r.approved()).isFalse();
        assertThat(r.authorizationCode()).isNull();
    }

    @Test
    @DisplayName("systemError() yields RC 96 and approved=false")
    void systemError() {
        AuthorizationResult r = AuthorizationResult.systemError();
        assertThat(r.responseCode()).isEqualTo("96");
        assertThat(r.approved()).isFalse();
        assertThat(r.authorizationCode()).isNull();
    }
}
