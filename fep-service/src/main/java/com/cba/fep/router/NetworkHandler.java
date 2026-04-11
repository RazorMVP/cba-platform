package com.cba.fep.router;

import com.cba.fep.iso.IsoField;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles MTI 0800 (Network Management Request) and 0820 (Network Management Response).
 *
 * <p>Network management messages control the FEP-to-terminal session:
 * <ul>
 *   <li>DE70 = 001 — Sign-On (terminal initiates session; FEP activates the link)</li>
 *   <li>DE70 = 002 — Sign-Off (terminal closes session gracefully)</li>
 *   <li>DE70 = 301 — Echo Test / Key Change (keep-alive; FEP responds with same DE70)</li>
 *   <li>DE70 = 161 — Logon (alternate sign-on for some ATM implementations)</li>
 * </ul>
 *
 * <p>Network messages do not carry financial data.
 * They establish that the terminal is alive and the FEP is ready to accept transactions.
 * The FEP responds with 0810 and RC=00 for all recognised management codes.
 */
@Slf4j
@Component
public class NetworkHandler {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MMddHHmmss");

    public ISOMsg handleRequest(ISOMsg request) throws ISOException {
        String stan = request.getString(IsoField.STAN);
        String code = request.getString(IsoField.NETWORK_MANAGEMENT_CODE);
        log.info("NETWORK 0800: STAN={} code={}", stan, code);

        String action = switch (code != null ? code : "") {
            case "001" -> { log.info("Terminal signed on: TID={}", request.getString(IsoField.TERMINAL_ID)); yield "SIGN_ON"; }
            case "002" -> { log.info("Terminal signed off: TID={}", request.getString(IsoField.TERMINAL_ID)); yield "SIGN_OFF"; }
            case "301" -> "ECHO";
            case "161" -> "LOGON";
            default    -> {
                log.warn("Unknown network management code: {}", code);
                yield "UNKNOWN";
            }
        };

        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0810");
        response.set(IsoField.RESPONSE_CODE, "00");
        response.set(IsoField.TRANSMISSION_DATETIME, LocalDateTime.now().format(DATETIME_FMT));

        log.debug("Network {} acknowledged for TID={}", action, request.getString(IsoField.TERMINAL_ID));
        return response;
    }

    public ISOMsg handleResponse(ISOMsg response) {
        // 0820 is a response from the switch (issuer side network management)
        // Log and do not respond — this is a one-way message in that direction
        log.info("NETWORK 0820 received: STAN={} RC={}",
                safeGet(response, IsoField.STAN),
                safeGet(response, IsoField.RESPONSE_CODE));
        return null;
    }

    private String safeGet(ISOMsg msg, int field) {
        try { return msg.getString(field); } catch (Exception e) { return "?"; }
    }
}
