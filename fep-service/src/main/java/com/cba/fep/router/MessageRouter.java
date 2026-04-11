package com.cba.fep.router;

import com.cba.fep.iso.IsoField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.springframework.stereotype.Component;

/**
 * Routes incoming ISO 8583 messages to the appropriate handler based on MTI.
 *
 * <p>MTI structure: {@code VTTF}
 * <ul>
 *   <li>V = Version (0 = 1987)</li>
 *   <li>T = Message Type (1=Authorization, 2=Financial, 3=File Action, 4=Reversal, 8=Network)</li>
 *   <li>T = Message Function (0=Request, 1=Request Response, 2=Advice, 3=Advice Response, 4=Notification)</li>
 *   <li>F = Message Origin (0=Acquirer, 2=Acquirer Repeat, 4=Issuer, 8=Other)</li>
 * </ul>
 *
 * <p>Supported MTIs:
 * <ul>
 *   <li>{@code 0100} — Authorization Request</li>
 *   <li>{@code 0120} — Authorization Advice (single-message; low-value contactless)</li>
 *   <li>{@code 0200} — Financial Request (PIN-based debit)</li>
 *   <li>{@code 0220} — Financial Advice</li>
 *   <li>{@code 0400} — Reversal Request</li>
 *   <li>{@code 0420} — Reversal Advice</li>
 *   <li>{@code 0800} — Network Management Request (sign-on/sign-off/echo)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRouter {

    private final AuthorizationHandler authorizationHandler;
    private final FinancialHandler     financialHandler;
    private final ReversalHandler      reversalHandler;
    private final NetworkHandler       networkHandler;

    /**
     * Route the incoming message and return the response.
     *
     * @param request decoded ISO 8583 message
     * @return response message, or {@code null} if no response is required
     */
    public ISOMsg route(ISOMsg request) throws ISOException {
        String mti = request.getMTI();
        if (mti == null || mti.length() != 4) {
            log.warn("Dropping message with invalid MTI: '{}'", mti);
            return null;
        }

        return switch (mti) {
            case "0100", "0101" -> authorizationHandler.handleRequest(request);
            case "0120", "0121" -> authorizationHandler.handleAdvice(request);
            case "0200", "0201" -> financialHandler.handleRequest(request);
            case "0220", "0221" -> financialHandler.handleAdvice(request);
            case "0400", "0401" -> reversalHandler.handleRequest(request);
            case "0420", "0421" -> reversalHandler.handleAdvice(request);
            case "0800"         -> networkHandler.handleRequest(request);
            case "0820"         -> networkHandler.handleResponse(request);
            default -> {
                log.warn("Unsupported MTI={}, STAN={}", mti, request.getString(IsoField.STAN));
                yield buildUnknownMtiResponse(request, mti);
            }
        };
    }

    /** Respond with RC=30 (Format Error) for unrecognised MTIs. */
    private ISOMsg buildUnknownMtiResponse(ISOMsg request, String mti) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        // Flip function digit: 0→1 (request→response), 2→3 (advice→advice response)
        char fn = mti.charAt(2);
        char responseFn = (fn == '0') ? '1' : (fn == '2') ? '3' : fn;
        response.setMTI("" + mti.charAt(0) + mti.charAt(1) + responseFn + mti.charAt(3));
        response.set(IsoField.RESPONSE_CODE, "30"); // Format Error
        return response;
    }
}
