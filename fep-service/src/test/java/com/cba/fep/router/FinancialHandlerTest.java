package com.cba.fep.router;

import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.iso.IsoField;
import com.cba.fep.scheme.SchemeAdapterFactory;
import com.cba.fep.scheme.SchemeType;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Tests for {@link FinancialHandler} (0200/0220) — ATM/financial single-message flow. */
@ExtendWith(MockitoExtension.class)
class FinancialHandlerTest {

    @Mock CardServiceClient cardServiceClient;
    @Mock SchemeAdapterFactory schemeAdapterFactory;
    @InjectMocks FinancialHandler handler;

    private static ISOMsg financialRequest(String proc) throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.setMTI("0200");
        msg.set(IsoField.PAN, "4111111111111111");
        msg.set(IsoField.PROCESSING_CODE, proc);
        msg.set(IsoField.AMOUNT_TRANSACTION, "000000010000");
        msg.set(IsoField.CURRENCY_TRANSACTION, "840");
        msg.set(IsoField.STAN, "000001");
        msg.set(IsoField.TERMINAL_ID, "ATM00001");
        return msg;
    }

    @Test
    @DisplayName("approved 0200 → 0210 RC=00 with auth code")
    void approved() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.approve("AUTH01"));

        ISOMsg resp = handler.handleRequest(financialRequest("010000")); // cash withdrawal

        assertThat(resp.getMTI()).isEqualTo("0210");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
        assertThat(resp.getString(IsoField.AUTH_ID_RESPONSE)).isEqualTo("AUTH01");
    }

    @Test
    @DisplayName("declined 0200 → 0210 with decline RC and no auth code")
    void declined() throws Exception {
        when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(cardServiceClient.authorize(any())).thenReturn(AuthorizationResult.decline("51"));

        ISOMsg resp = handler.handleRequest(financialRequest("010000"));

        assertThat(resp.getMTI()).isEqualTo("0210");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("51");
        assertThat(resp.hasField(IsoField.AUTH_ID_RESPONSE)).isFalse();
    }

    @Test
    @DisplayName("balance inquiry (310000) populates DE54 with the available balance")
    void balanceInquiryPopulatesDe54() throws Exception {
        lenient().when(schemeAdapterFactory.detectScheme(anyString())).thenReturn(SchemeType.VISA);
        when(cardServiceClient.authorize(any())).thenReturn(
                new AuthorizationResult("00", "AUTH01", true, false, new BigDecimal("500.00"), "840", null));

        ISOMsg resp = handler.handleRequest(financialRequest("310000"));

        // DE54 = type(40) + currency(840) + sign(C) + amount(12, minor units)
        assertThat(resp.getString(IsoField.ADDITIONAL_AMOUNTS)).isEqualTo("40840C000000050000");
    }

    @Test
    @DisplayName("0220 advice → 0230 carrying the recorded RC")
    void advice() throws Exception {
        ISOMsg msg = financialRequest("010000");
        msg.setMTI("0220");
        when(cardServiceClient.recordAdvice(any(), any(), any()))
                .thenReturn(AuthorizationResult.approve("00"));

        ISOMsg resp = handler.handleAdvice(msg);

        assertThat(resp.getMTI()).isEqualTo("0230");
        assertThat(resp.getString(IsoField.RESPONSE_CODE)).isEqualTo("00");
    }
}
