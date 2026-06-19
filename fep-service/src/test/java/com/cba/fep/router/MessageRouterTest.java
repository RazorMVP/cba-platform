package com.cba.fep.router;

import com.cba.fep.iso.IsoField;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MessageRouter} MTI handling. Only the validation and
 * unknown-MTI branches are exercised here — these never dereference a handler,
 * so the four handler collaborators can be {@code null}. (The happy-path MTIs
 * delegate to handlers whose own collaborators need a running card-service, so
 * those are integration-test territory, not unit tests.)
 */
class MessageRouterTest {

    private final MessageRouter router = new MessageRouter(null, null, null, null);

    private static ISOMsg withRawMti(String mti) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.set(IsoField.MTI, mti); // set field 0 directly to allow malformed values
        msg.set(IsoField.STAN, "000001");
        return msg;
    }

    @Test
    @DisplayName("a malformed (non-4-char) MTI is dropped with a null response")
    void malformedMtiDropped() throws Exception {
        assertThat(router.route(withRawMti("00"))).isNull();
    }

    @Test
    @DisplayName("an unknown request MTI yields RC=30 and flips the function digit 0→1")
    void unknownRequestMti() throws Exception {
        ISOMsg response = router.route(withRawMti("0300")); // file-action request, unsupported
        assertThat(response).isNotNull();
        assertThat(response.getString(IsoField.RESPONSE_CODE)).isEqualTo("30");
        assertThat(response.getMTI()).isEqualTo("0310");
    }

    @Test
    @DisplayName("an unknown advice MTI yields RC=30 and flips the function digit 2→3")
    void unknownAdviceMti() throws Exception {
        ISOMsg response = router.route(withRawMti("0320")); // file-action advice, unsupported
        assertThat(response).isNotNull();
        assertThat(response.getString(IsoField.RESPONSE_CODE)).isEqualTo("30");
        assertThat(response.getMTI()).isEqualTo("0330");
    }
}
