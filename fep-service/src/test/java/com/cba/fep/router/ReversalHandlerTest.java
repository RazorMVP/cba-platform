package com.cba.fep.router;

import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.iso.IsoField;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests for {@link ReversalHandler} (0400/0420) — reversal request/advice. */
@ExtendWith(MockitoExtension.class)
class ReversalHandlerTest {

    @Mock CardServiceClient cardServiceClient;
    @InjectMocks ReversalHandler handler;

    private static ISOMsg reversalRequest(String mti) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI(mti);
        msg.set(IsoField.PAN, "4111111111111111");
        msg.set(IsoField.AMOUNT_TRANSACTION, "000000010000");
        msg.set(IsoField.STAN, "000009");
        msg.set(IsoField.ORIGINAL_DATA_ELEMENTS, "0200000001....");
        return msg;
    }

    @Test
    @DisplayName("0400 reversal → 0410 carrying card-service's reversal RC")
    void reversalAccepted() throws Exception {
        when(cardServiceClient.reverse(any(), any(), any(), any())).thenReturn("00");

        ISOMsg resp = handler.handleRequest(reversalRequest("0400"));

        assertThat(resp.getMTI()).isEqualTo("0410");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
    }

    @Test
    @DisplayName("0400 reversal of an unknown original → 0410 RC=25")
    void reversalOriginalNotFound() throws Exception {
        when(cardServiceClient.reverse(any(), any(), any(), any())).thenReturn("25");

        ISOMsg resp = handler.handleRequest(reversalRequest("0400"));

        assertThat(resp.getMTI()).isEqualTo("0410");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("25");
    }

    @Test
    @DisplayName("0420 advice → 0430 RC=00 and still records the reversal")
    void reversalAdvice() throws Exception {
        when(cardServiceClient.reverse(any(), any(), any(), any())).thenReturn("00");

        ISOMsg resp = handler.handleAdvice(reversalRequest("0420"));

        assertThat(resp.getMTI()).isEqualTo("0430");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
        verify(cardServiceClient).reverse(any(), any(), any(), any());
    }
}
