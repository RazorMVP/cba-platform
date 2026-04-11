package com.cba.fep.router;

import com.cba.fep.auth.CardServiceClient;
import com.cba.fep.iso.IsoField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles MTI 0400 (Reversal Request) and 0420 (Reversal Advice).
 *
 * <p>Reversals are sent when:
 * <ul>
 *   <li>ATM dispenses cash but cannot confirm to the host (communication timeout)</li>
 *   <li>Terminal times out waiting for an 0110 response and the transaction is ambiguous</li>
 *   <li>Customer cancels before completing a chip transaction (partial reversal)</li>
 * </ul>
 *
 * <p>DE90 (Original Data Elements) carries the original MTI + STAN + date + IDs
 * that identify which transaction is being reversed.
 *
 * <p>The response MTI is 0410. The card-service is responsible for
 * idempotent reversal handling — duplicate reversals must not double-credit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReversalHandler {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MMddHHmmss");

    private final CardServiceClient cardServiceClient;

    public ISOMsg handleRequest(ISOMsg request) throws ISOException {
        String stan        = request.getString(IsoField.STAN);
        String originalDe90 = request.getString(IsoField.ORIGINAL_DATA_ELEMENTS);
        log.info("REVERSAL 0400: STAN={} OriginalData={}", stan, originalDe90);

        String responseCode = cardServiceClient.reverse(
                request.getString(IsoField.PAN),
                request.getString(IsoField.AMOUNT_TRANSACTION),
                stan,
                originalDe90);

        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0410");
        response.set(IsoField.RESPONSE_CODE, responseCode);
        response.set(IsoField.TRANSMISSION_DATETIME, LocalDateTime.now().format(DATETIME_FMT));
        return response;
    }

    public ISOMsg handleAdvice(ISOMsg request) throws ISOException {
        // 0420 — acquirer-originated reversal advice; record and acknowledge
        String stan = request.getString(IsoField.STAN);
        log.info("REVERSAL 0420 advice: STAN={}", stan);

        cardServiceClient.reverse(
                request.getString(IsoField.PAN),
                request.getString(IsoField.AMOUNT_TRANSACTION),
                stan,
                request.getString(IsoField.ORIGINAL_DATA_ELEMENTS));

        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0430");
        response.set(IsoField.RESPONSE_CODE, "00");
        response.set(IsoField.TRANSMISSION_DATETIME, LocalDateTime.now().format(DATETIME_FMT));
        return response;
    }
}
