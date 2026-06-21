package com.cba.card.terminal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TerminalSimulatorService} — builds an ISO 8583 request, sends
 * it via the Netty {@link FepIso8583Client}, and decodes the response. The client
 * is mocked with canned response frames so the full build→send→decode path runs
 * without a live FEP (no Docker needed). Validates the simulator's own ISO 8583
 * response decoder (MTI, DE39 response code, DE38 auth code, approval flag).
 */
@ExtendWith(MockitoExtension.class)
class TerminalSimulatorServiceTest {

    @Mock FepIso8583Client fepClient;
    @InjectMocks TerminalSimulatorService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultSimulatorCurrency", "840");
    }

    private static SimulateRequest req() {
        return new SimulateRequest("4111111111111111", "2612", new BigDecimal("25.00"), "840",
                "TERM0001", "MERCHANT0000001", "Test Shop", "5411", "CHIP", null, null, null, "0301");
    }

    /**
     * Build a minimal ISO 8583 response: [MTI(4)][primary bitmap(8)][DE38?][DE39].
     * DE38 (auth code, byte 4 bit 5 → 0x04) and DE39 (response code, byte 4 bit 6 → 0x02).
     */
    private static byte[] canned(String mti, String responseCode, String authCode) {
        byte[] bitmap = new byte[8];
        StringBuilder fields = new StringBuilder();
        if (authCode != null) {
            bitmap[4] |= 0x04;                                   // DE38 present
            fields.append(String.format("%-6s", authCode), 0, 6);
        }
        bitmap[4] |= 0x02;                                       // DE39 present
        fields.append(responseCode);

        byte[] mtiB = mti.getBytes(StandardCharsets.US_ASCII);
        byte[] f = fields.toString().getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[4 + 8 + f.length];
        System.arraycopy(mtiB, 0, out, 0, 4);
        System.arraycopy(bitmap, 0, out, 4, 8);
        System.arraycopy(f, 0, out, 12, f.length);
        return out;
    }

    @Test
    @DisplayName("approved purchase: 0100 → decodes 0110 RC=00 with auth code")
    void purchaseApproved() {
        when(fepClient.send(any(byte[].class))).thenReturn(canned("0110", "00", "AUTH99"));

        SimulateResponse resp = service.purchase(req());

        assertThat(resp.approved()).isTrue();
        assertThat(resp.responseCode()).isEqualTo("00");
        assertThat(resp.authCode()).isEqualTo("AUTH99");
        assertThat(resp.responseMti()).isEqualTo("0110");
        assertThat(resp.requestMti()).isEqualTo("0100");
    }

    @Test
    @DisplayName("declined purchase: 0110 RC=05, not approved, no auth code")
    void purchaseDeclined() {
        when(fepClient.send(any(byte[].class))).thenReturn(canned("0110", "05", null));

        SimulateResponse resp = service.purchase(req());

        assertThat(resp.approved()).isFalse();
        assertThat(resp.responseCode()).isEqualTo("05");
        assertThat(resp.authCode()).isNull();
    }

    @Test
    @DisplayName("withdrawal: 0200 → decodes 0210")
    void withdrawal() {
        when(fepClient.send(any(byte[].class))).thenReturn(canned("0210", "00", "AUTH88"));

        SimulateResponse resp = service.withdrawal(req());

        assertThat(resp.requestMti()).isEqualTo("0200");
        assertThat(resp.responseMti()).isEqualTo("0210");
        assertThat(resp.approved()).isTrue();
    }

    @Test
    @DisplayName("network management: 0800 → decodes 0810 RC=00")
    void networkManagement() {
        when(fepClient.send(any(byte[].class))).thenReturn(canned("0810", "00", null));

        SimulateResponse resp = service.networkManagement(req());

        assertThat(resp.responseMti()).isEqualTo("0810");
        assertThat(resp.approved()).isTrue();
    }

    @Test
    @DisplayName("FEP unreachable → RC=91 issuer-unavailable response")
    void fepUnavailable() {
        when(fepClient.send(any(byte[].class)))
                .thenThrow(new FepIso8583Client.FepConnectionException("connection refused", null));

        SimulateResponse resp = service.purchase(req());

        assertThat(resp.approved()).isFalse();
        assertThat(resp.responseCode()).isEqualTo("91");
    }
}
