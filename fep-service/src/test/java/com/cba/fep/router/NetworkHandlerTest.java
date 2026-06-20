package com.cba.fep.router;

import com.cba.fep.iso.IsoField;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link NetworkHandler} — 0800 network-management (sign-on/off/echo).
 * No collaborators, so this is a pure unit test.
 */
class NetworkHandlerTest {

    private final NetworkHandler handler = new NetworkHandler();

    private static ISOMsg request(String networkCode) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0800");
        msg.set(IsoField.STAN, "000001");
        msg.set(IsoField.TERMINAL_ID, "TERM0001");
        if (networkCode != null) msg.set(IsoField.NETWORK_MANAGEMENT_CODE, networkCode);
        return msg;
    }

    @Test
    @DisplayName("sign-on (001) is acknowledged with 0810 RC=00")
    void signOn() throws Exception {
        ISOMsg resp = handler.handleRequest(request("001"));
        assertThat(resp.getMTI()).isEqualTo("0810");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
    }

    @Test
    @DisplayName("echo test (301) is acknowledged with 0810 RC=00")
    void echo() throws Exception {
        ISOMsg resp = handler.handleRequest(request("301"));
        assertThat(resp.getMTI()).isEqualTo("0810");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
    }

    @Test
    @DisplayName("an unrecognised management code is still acknowledged (0810 RC=00)")
    void unknownCode() throws Exception {
        ISOMsg resp = handler.handleRequest(request("999"));
        assertThat(resp.getMTI()).isEqualTo("0810");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
    }

    @Test
    @DisplayName("a 0820 response from the switch is logged and not replied to")
    void inboundResponseNotReplied() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0820");
        msg.set(IsoField.STAN, "000002");
        msg.set(IsoField.RESPONSE_CODE, "00");
        assertThat(handler.handleResponse(msg)).isNull();
    }
}
