package com.cba.fep.auth;

import com.cba.fep.scheme.SchemeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for {@link AuthorizationRequest} compact-constructor defaults and the Lombok {@code @With} copy. */
class AuthorizationRequestTest {

    @Test
    @DisplayName("null scheme defaults to UNKNOWN")
    void nullSchemeDefaultsToUnknown() {
        AuthorizationRequest req = AuthorizationRequest.builder()
                .pan("4111111111111111")
                .scheme(null)
                .build();
        assertThat(req.scheme()).isEqualTo(SchemeType.UNKNOWN);
    }

    @Test
    @DisplayName("null schemeData defaults to an empty (non-null) map")
    void nullSchemeDataDefaultsToEmptyMap() {
        AuthorizationRequest req = AuthorizationRequest.builder()
                .pan("4111111111111111")
                .schemeData(null)
                .build();
        assertThat(req.schemeData()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("explicit values are preserved through the builder")
    void explicitValuesPreserved() {
        AuthorizationRequest req = AuthorizationRequest.builder()
                .pan("5111111111111118")
                .amount("1000")
                .currencyCode("840")
                .scheme(SchemeType.MASTERCARD)
                .schemeData(Map.of("DE48", "abc"))
                .build();
        assertThat(req.scheme()).isEqualTo(SchemeType.MASTERCARD);
        assertThat(req.amount()).isEqualTo("1000");
        assertThat(req.schemeData()).containsEntry("DE48", "abc");
    }

    @Test
    @DisplayName("@With produces a modified copy, leaving the original untouched")
    void withCopiesImmutably() {
        AuthorizationRequest original = AuthorizationRequest.builder()
                .pan("4111111111111111")
                .arqcValid(false)
                .build();

        AuthorizationRequest updated = original.withArqcValid(true);

        assertThat(original.arqcValid()).isFalse();
        assertThat(updated.arqcValid()).isTrue();
        assertThat(updated.pan()).isEqualTo("4111111111111111");
    }
}
