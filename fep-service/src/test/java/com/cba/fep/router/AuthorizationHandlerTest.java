package com.cba.fep.router;

import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.emv.ArpcGenerator;
import com.cba.fep.emv.ArqcValidator;
import com.cba.fep.emv.EmvData;
import com.cba.fep.emv.EmvDataParser;
import com.cba.fep.emv.EmvTag;
import com.cba.fep.hsm.HsmAdapter;
import com.cba.fep.iso.IsoField;
import com.cba.fep.iso.IsoMessageFactory;
import com.cba.fep.scheme.SchemeAdapter;
import com.cba.fep.scheme.SchemeAdapterFactory;
import com.cba.fep.scheme.SchemeType;
import com.cba.fep.scheme.VisaSchemeAdapter;
import org.jpos.iso.ISOMsg;

import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cba.fep.auth.AuthorizationRequest;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Happy-path tests for {@link AuthorizationHandler} (0100/0120) with a non-EMV,
 * non-PIN, non-token transaction. The scheme adapter is mocked (interface); the
 * concrete card-service client + EMV/HSM collaborators are mocked via the Java 25
 * Mockito config added to fep-service/pom.xml. (Full PIN/EMV/detokenize flows and
 * the Netty socket round-trip are integration territory.)
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationHandlerTest {

    @Mock CardServiceClient cardServiceClient;
    @Mock SchemeAdapterFactory schemeAdapterFactory;
    @Mock HsmAdapter hsmAdapter;
    @Mock EmvDataParser emvDataParser;
    @Mock ArqcValidator arqcValidator;
    @Mock ArpcGenerator arpcGenerator;
    @Mock SchemeAdapter adapter;

    @InjectMocks AuthorizationHandler handler;

    private static ISOMsg authRequest() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0100");
        msg.set(IsoField.PAN, "4111111111111111");
        msg.set(IsoField.PROCESSING_CODE, "000000");
        msg.set(IsoField.AMOUNT_TRANSACTION, "000000010000");
        msg.set(IsoField.CURRENCY_TRANSACTION, "840");
        msg.set(IsoField.STAN, "000001");
        msg.set(IsoField.TERMINAL_ID, "TERM0001");
        return msg;
    }

    @Test
    @DisplayName("approved 0100 → 0110 with RC=00 and the auth code in DE38")
    void approved() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA)).thenReturn(adapter);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));

        ISOMsg resp = handler.handleRequest(authRequest());

        assertThat(resp.getMTI()).isEqualTo("0110");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
        assertThat(resp.getString(IsoField.AUTH_ID_RESPONSE)).isEqualTo("A1B2C3");
    }

    @Test
    @DisplayName("declined 0100 → 0110 with the decline RC and no auth code")
    void declined() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA)).thenReturn(adapter);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.decline("05"));

        ISOMsg resp = handler.handleRequest(authRequest());

        assertThat(resp.getMTI()).isEqualTo("0110");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("05");
        assertThat(resp.hasField(IsoField.AUTH_ID_RESPONSE)).isFalse();
    }

    @Test
    @DisplayName("0120 advice → 0130 carrying the recorded result's RC")
    void advice() throws Exception {
        ISOMsg msg = authRequest();
        msg.setMTI("0120");
        when(cardServiceClient.recordAdvice(any(), any(), any()))
                .thenReturn(AuthorizationResult.approve("00"));

        ISOMsg resp = handler.handleAdvice(msg);

        assertThat(resp.getMTI()).isEqualTo("0130");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
    }

    @Test
    @DisplayName("DE19 acquiring country is forwarded to card-service for the international control")
    void forwardsAcquiringCountry() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA)).thenReturn(adapter);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));
        ISOMsg msg = authRequest();
        msg.set(IsoField.ACQUIRING_COUNTRY, "566");

        handler.handleRequest(msg);

        ArgumentCaptor<AuthorizationRequest> sent = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(cardServiceClient).authorize(sent.capture());
        assertThat(sent.getValue().acquiringCountry()).isEqualTo("566");
    }

    @Test
    @DisplayName("DE6 / DE51 / DE10 are forwarded to card-service (backlog 17)")
    void forwardsBilling() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA)).thenReturn(adapter);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));
        ISOMsg msg = authRequest();
        msg.set(IsoField.AMOUNT_BILLING, "000000001150");
        msg.set(IsoField.CURRENCY_BILLING, "840");
        msg.set(IsoField.CONVERSION_RATE_BILLING, "61150000");

        handler.handleRequest(msg);

        ArgumentCaptor<AuthorizationRequest> sent = ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(cardServiceClient).authorize(sent.capture());
        assertThat(sent.getValue().billingAmount()).isEqualTo("000000001150");
        assertThat(sent.getValue().billingCurrencyCode()).isEqualTo("840");
        assertThat(sent.getValue().billingConversionRate()).isEqualTo("61150000");
    }

    // --- Response DE55 = tag 91 only (backlog 4, decision D8a) ---

    private static final byte[] ARQC = HexFormat.of().parseHex("1122334455667788");
    private static final byte[] ARPC = HexFormat.of().parseHex("A1A2A3A4A5A6A7A8");
    private static final byte[] REQUEST_ICC = HexFormat.of().parseHex("9F26081122334455667788" + "9F270180");

    /** A real Visa adapter so the DE55 bytes on the response are the ones a terminal would receive. */
    private void chipTransactionAnswering(AuthorizationResult result) {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA))
                .thenReturn(new VisaSchemeAdapter(mock(IsoMessageFactory.class)));
        when(emvDataParser.parse(REQUEST_ICC)).thenReturn(new EmvData(Map.of(EmvTag.ARQC, ARQC)));
        when(arqcValidator.validate(any(), anyString(), any())).thenReturn(true);
        when(cardServiceClient.authorize(any())).thenReturn(result);
    }

    private static ISOMsg chipRequest() throws Exception {
        ISOMsg msg = authRequest();
        msg.set(IsoField.ICC_DATA, REQUEST_ICC);
        return msg;
    }

    @Test
    @DisplayName("approved chip 0100 → 0110 DE55 is exactly 91 0A <ARPC || \"00\">")
    void approvalCarriesTag91() throws Exception {
        chipTransactionAnswering(AuthorizationResult.approve("A1B2C3"));
        when(arpcGenerator.generate(ARQC, "00")).thenReturn(ARPC);

        ISOMsg resp = handler.handleRequest(chipRequest());

        assertThat(HexFormat.of().withUpperCase().formatHex(resp.getBytes(IsoField.ICC_DATA)))
                .isEqualTo("910A" + "A1A2A3A4A5A6A7A8" + "3030");
    }

    @Test
    @DisplayName("declined chip 0100 still gets tag 91, with the decline code as ARC")
    void declineCarriesTag91() throws Exception {
        chipTransactionAnswering(AuthorizationResult.decline("05"));
        when(arpcGenerator.generate(ARQC, "05")).thenReturn(ARPC);

        ISOMsg resp = handler.handleRequest(chipRequest());

        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("05");
        assertThat(HexFormat.of().withUpperCase().formatHex(resp.getBytes(IsoField.ICC_DATA)))
                .isEqualTo("910A" + "A1A2A3A4A5A6A7A8" + "3035");
    }

    @Test
    @DisplayName("no ARPC (generator refused) → no DE55 at all, the request ICC data is not echoed")
    void noArpcNoDe55() throws Exception {
        chipTransactionAnswering(AuthorizationResult.approve("A1B2C3"));
        when(arpcGenerator.generate(ARQC, "00")).thenReturn(new byte[0]);

        ISOMsg resp = handler.handleRequest(chipRequest());

        assertThat(resp.hasField(IsoField.ICC_DATA)).isFalse();
    }

    @Test
    @DisplayName("DE55 without an ARQC → response has no DE55 and no ARPC is generated")
    void iccWithoutArqc() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA)).thenReturn(adapter);
        when(emvDataParser.parse(any())).thenReturn(new EmvData(Map.of()));
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));

        ISOMsg resp = handler.handleRequest(chipRequest());

        assertThat(resp.hasField(IsoField.ICC_DATA)).isFalse();
        verifyNoInteractions(arpcGenerator);
    }

    @Test
    @DisplayName("no DE55 in the request → none in the 0110")
    void magstripeHasNoDe55() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(schemeAdapterFactory.getAdapter(SchemeType.VISA)).thenReturn(adapter);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("A1B2C3"));

        ISOMsg resp = handler.handleRequest(authRequest());

        assertThat(resp.hasField(IsoField.ICC_DATA)).isFalse();
        verifyNoInteractions(arpcGenerator, emvDataParser);
    }

    @Test
    @DisplayName("0130 advice response does not echo the request's ICC data")
    void adviceDoesNotEchoIcc() throws Exception {
        ISOMsg msg = chipRequest();
        msg.setMTI("0120");
        when(cardServiceClient.recordAdvice(any(), any(), any())).thenReturn(AuthorizationResult.approve("00"));

        assertThat(handler.handleAdvice(msg).hasField(IsoField.ICC_DATA)).isFalse();
    }
}
