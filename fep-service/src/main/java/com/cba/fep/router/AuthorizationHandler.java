package com.cba.fep.router;

import com.cba.fep.auth.AuthorizationRequest;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles MTI 0100 (Authorization Request) and 0120 (Authorization Advice).
 *
 * <p>Authorization flow:
 * <ol>
 *   <li>Extract PAN, amount, terminal data from incoming ISOMsg</li>
 *   <li>BIN lookup → resolve scheme → apply scheme-specific field rules</li>
 *   <li>If DE52 present → decrypt PIN block via HSM (Thales DC command)</li>
 *   <li>If DE55 present → validate ARQC via HSM / EMV parser</li>
 *   <li>De-tokenize DPAN → PAN if token BIN prefix 9999xx detected</li>
 *   <li>Forward enriched auth request to card-service REST API</li>
 *   <li>Generate ARPC and populate DE55 in response if chip transaction</li>
 *   <li>Return 0110 response with authorization code (DE38) or decline RC (DE39)</li>
 * </ol>
 *
 * <p>For 0120 advice (single-message; low-value contactless), the transaction
 * has already been completed at the terminal. We record it and return 0130.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationHandler {

    private static final String TOKEN_BIN_PREFIX = "9999";
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MMddHHmmss");

    private final CardServiceClient    cardServiceClient;
    private final SchemeAdapterFactory schemeAdapterFactory;
    private final HsmAdapter           hsmAdapter;
    private final EmvDataParser        emvDataParser;
    private final ArqcValidator        arqcValidator;
    private final ArpcGenerator        arpcGenerator;

    public ISOMsg handleRequest(ISOMsg request) throws ISOException {
        String stan = request.getString(IsoField.STAN);
        String pan  = request.getString(IsoField.PAN);
        log.info("AUTH 0100: STAN={} PAN={}****", stan, pan != null && pan.length() > 6 ? pan.substring(0, 6) : pan);

        SchemeType scheme  = schemeAdapterFactory.detectScheme(pan);
        SchemeAdapter adapter = schemeAdapterFactory.getAdapter(scheme);

        // Re-pack message with scheme-specific packager for correct DE parsing
        adapter.applyPackager(request);

        // De-tokenize if DPAN (token BIN prefix 9999xx)
        String effectivePan = (pan != null && pan.startsWith(TOKEN_BIN_PREFIX))
                ? cardServiceClient.detokenize(pan)
                : pan;

        // Build authorization request DTO
        AuthorizationRequest authReq = AuthorizationRequest.builder()
                .pan(effectivePan)
                .processingCode(request.getString(IsoField.PROCESSING_CODE))
                .amount(request.getString(IsoField.AMOUNT_TRANSACTION))
                .currencyCode(request.getString(IsoField.CURRENCY_TRANSACTION))
                .stan(stan)
                .terminalId(request.getString(IsoField.TERMINAL_ID))
                .merchantId(request.getString(IsoField.CARD_ACCEPTOR_ID))
                .merchantName(request.getString(IsoField.CARD_ACCEPTOR_NAME))
                .mcc(request.getString(IsoField.MCC))
                .posEntryMode(request.getString(IsoField.POS_ENTRY_MODE))
                .posConditionCode(request.getString(IsoField.POS_CONDITION_CODE))
                .rrn(request.getString(IsoField.RETRIEVAL_REF_NUMBER))
                .scheme(scheme)
                .build();

        // PIN verification (DE52 present = PIN-based transaction)
        if (request.hasField(IsoField.PIN_DATA)) {
            byte[] pinBlock = request.getBytes(IsoField.PIN_DATA);
            authReq = authReq.withPinVerified(hsmAdapter.verifyPin(pinBlock, effectivePan));
        }

        // EMV ARQC validation (DE55 present = chip transaction)
        // Pass the scheme adapter's cryptogram algorithm so UnionPay domestic cards
        // (QPBOC/SM4) are validated with SM4 CBC-MAC rather than 3DES.
        byte[] arqcBytes = null;
        if (request.hasField(IsoField.ICC_DATA)) {
            byte[] iccData = request.getBytes(IsoField.ICC_DATA);
            var emvTags = emvDataParser.parse(iccData);
            arqcBytes = emvTags.getTag("9F26");
            boolean arqcValid = arqcValidator.validate(
                    emvTags, effectivePan, adapter.getCryptogramAlgorithm());
            authReq = authReq.withEmvData(emvTags).withArqcValid(arqcValid);
        }

        // Forward to card-service
        AuthorizationResult result = cardServiceClient.authorize(authReq);

        // Build 0110 response
        ISOMsg response = buildAuthResponse(request, result);

        // Generate and embed ARPC for chip transactions
        if (arqcBytes != null && result.approved()) {
            byte[] arpc = arpcGenerator.generate(arqcBytes, result.authorizationCode());
            adapter.embedArpc(response, arpc);
        }

        adapter.finalizeResponse(response, result, scheme);
        return response;
    }

    public ISOMsg handleAdvice(ISOMsg request) throws ISOException {
        // 0120 single-message advice: record and acknowledge
        String stan = request.getString(IsoField.STAN);
        log.info("AUTH 0120 advice: STAN={}", stan);

        AuthorizationResult result = cardServiceClient.recordAdvice(
                request.getString(IsoField.PAN),
                request.getString(IsoField.AMOUNT_TRANSACTION),
                stan);

        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0130");
        response.set(IsoField.RESPONSE_CODE, result.responseCode());
        setTransmissionDateTime(response);
        return response;
    }

    private ISOMsg buildAuthResponse(ISOMsg request, AuthorizationResult result) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0110");
        response.set(IsoField.RESPONSE_CODE, result.responseCode());
        if (result.approved()) {
            response.set(IsoField.AUTH_ID_RESPONSE, result.authorizationCode());
        }
        setTransmissionDateTime(response);
        return response;
    }

    private void setTransmissionDateTime(ISOMsg msg) throws ISOException {
        msg.set(IsoField.TRANSMISSION_DATETIME, LocalDateTime.now().format(DATETIME_FMT));
    }
}
