package com.cba.fep.router;

import com.cba.fep.auth.AuthorizationRequest;
import com.cba.fep.auth.AuthorizationResult;
import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.emv.ArpcGenerator;
import com.cba.fep.emv.ArqcValidator;
import com.cba.fep.emv.EmvDataParser;
import com.cba.fep.emv.EmvTag;
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
 *   <li>Chip transaction (request had an ARQC, tag 9F26): response DE55 = tag 91 only, ARPC || ARC
 *       (ARC = DE39), for approvals and declines; otherwise the response has no DE55</li>
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
                .acquiringCountry(request.getString(IsoField.ACQUIRING_COUNTRY))
                .billingAmount(request.getString(IsoField.AMOUNT_BILLING))
                .billingCurrencyCode(request.getString(IsoField.CURRENCY_BILLING))
                .billingConversionRate(request.getString(IsoField.CONVERSION_RATE_BILLING))
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
            arqcBytes = emvTags.getTag(EmvTag.ARQC);
            boolean arqcValid = arqcValidator.validate(
                    emvTags, effectivePan, adapter.getCryptogramAlgorithm());
            authReq = authReq.withEmvData(emvTags).withArqcValid(arqcValid);
        }

        // Forward to card-service
        AuthorizationResult result = cardServiceClient.authorize(authReq);

        // Build 0110 response (DE55 cleared — the request's ICC data is never echoed)
        ISOMsg response = buildAuthResponse(request, result);
        adapter.finalizeResponse(response, result, scheme);

        // Chip transaction: DE55 = tag 91 (ARPC || ARC), for declines as well as approvals. The ARC is
        // the final DE39, so it's computed after finalizeResponse (which may still set the RC).
        if (arqcBytes != null) {
            String responseCode = response.getString(IsoField.RESPONSE_CODE);
            byte[] arc  = ArpcGenerator.arc(responseCode);
            byte[] arpc = arpcGenerator.generate(arqcBytes, responseCode);
            if (arc != null && arpc != null && arpc.length == 8) {
                adapter.embedArpc(response, arpc, arc);
            } else {
                log.warn("AUTH 0110: STAN={} no ARPC for RC={} — DE55 omitted", stan, responseCode);
            }
        }
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
        response.unset(IsoField.ICC_DATA);
        response.setMTI("0130");
        response.set(IsoField.RESPONSE_CODE, result.responseCode());
        setTransmissionDateTime(response);
        return response;
    }

    private ISOMsg buildAuthResponse(ISOMsg request, AuthorizationResult result) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.unset(IsoField.ICC_DATA);
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
