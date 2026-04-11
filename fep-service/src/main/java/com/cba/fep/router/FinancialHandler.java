package com.cba.fep.router;

import com.cba.fep.auth.AuthorizationRequest;
import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.iso.IsoField;
import com.cba.fep.scheme.SchemeAdapterFactory;
import com.cba.fep.scheme.SchemeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles MTI 0200 (Financial Request) and 0220 (Financial Advice).
 *
 * <p>Financial messages represent transactions that must complete in a single
 * message — they carry both the authorization and the capture in one request.
 * Typical uses:
 * <ul>
 *   <li>ATM cash withdrawals (always 0200 — debit-authorise-and-capture)</li>
 *   <li>PIN-change transactions (0200 with processing code 940000)</li>
 *   <li>Balance inquiry (0200 with processing code 310000)</li>
 * </ul>
 *
 * <p>The response MTI is 0210.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialHandler {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MMddHHmmss");

    private final CardServiceClient    cardServiceClient;
    private final SchemeAdapterFactory schemeAdapterFactory;

    public ISOMsg handleRequest(ISOMsg request) throws ISOException {
        String stan = request.getString(IsoField.STAN);
        String pan  = request.getString(IsoField.PAN);
        String proc = request.getString(IsoField.PROCESSING_CODE);
        log.info("FINANCIAL 0200: STAN={} PROC={} PAN={}****",
                stan, proc, pan != null && pan.length() > 6 ? pan.substring(0, 6) : pan);

        SchemeType scheme = schemeAdapterFactory.detectScheme(pan);

        AuthorizationRequest authReq = AuthorizationRequest.builder()
                .pan(pan)
                .processingCode(proc)
                .amount(request.getString(IsoField.AMOUNT_TRANSACTION))
                .currencyCode(request.getString(IsoField.CURRENCY_TRANSACTION))
                .stan(stan)
                .terminalId(request.getString(IsoField.TERMINAL_ID))
                .merchantId(request.getString(IsoField.CARD_ACCEPTOR_ID))
                .rrn(request.getString(IsoField.RETRIEVAL_REF_NUMBER))
                .scheme(scheme)
                .isFinancial(true)
                .build();

        AuthorizationResult result = cardServiceClient.authorize(authReq);

        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0210");
        response.set(IsoField.RESPONSE_CODE, result.responseCode());
        if (result.approved()) {
            response.set(IsoField.AUTH_ID_RESPONSE, result.authorizationCode());
            // For balance inquiries, populate DE54 (Additional Amounts)
            if ("310000".equals(proc) && result.availableBalance() != null) {
                response.set(IsoField.ADDITIONAL_AMOUNTS, buildBalanceDE54(result));
            }
        }
        response.set(IsoField.TRANSMISSION_DATETIME, LocalDateTime.now().format(DATETIME_FMT));
        return response;
    }

    public ISOMsg handleAdvice(ISOMsg request) throws ISOException {
        String stan = request.getString(IsoField.STAN);
        log.info("FINANCIAL 0220 advice: STAN={}", stan);

        AuthorizationResult result = cardServiceClient.recordAdvice(
                request.getString(IsoField.PAN),
                request.getString(IsoField.AMOUNT_TRANSACTION),
                stan);

        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0230");
        response.set(IsoField.RESPONSE_CODE, result.responseCode());
        response.set(IsoField.TRANSMISSION_DATETIME, LocalDateTime.now().format(DATETIME_FMT));
        return response;
    }

    /**
     * Build DE54 (Additional Amounts) for balance inquiry responses.
     * Format per ISO 8583: amount type (2) + currency code (3) + sign (1) + amount (12)
     * Amount type 40 = available balance, 41 = ledger balance.
     */
    private String buildBalanceDE54(AuthorizationResult result) {
        String currency = result.currencyCode() != null ? result.currencyCode() : "000";
        String balance  = String.format("%012.0f", result.availableBalance().movePointRight(2));
        return "40" + currency + "C" + balance;
    }
}
