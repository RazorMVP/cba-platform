package com.cba.fep.router;

import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.emv.ArpcGenerator;
import com.cba.fep.emv.ArqcValidator;
import com.cba.fep.emv.EmvDataParser;
import com.cba.fep.hsm.HsmAdapter;
import com.cba.fep.iso.IsoField;
import com.cba.fep.scheme.SchemeAdapter;
import com.cba.fep.scheme.SchemeAdapterFactory;
import com.cba.fep.scheme.SchemeType;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
}
