package com.cba.card.terminal;

import com.cba.card.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Terminal simulator REST API.
 *
 * <p>Allows the Angular backoffice portal (and Postman/curl) to simulate
 * card transactions by building ISO 8583 messages and firing them directly
 * at the FEP TCP socket on port 8583.
 *
 * <p>All endpoints return a {@link SimulateResponse} that includes the
 * decoded response code, auth code, available balance, STAN, RRN, and
 * the raw hex dumps of both request and response for technical inspection.
 *
 * <p>Requires ADMIN or TELLER role — this is not a customer-facing API.
 */
@RestController
@RequestMapping("/api/v1/simulate")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TELLER')")
public class TerminalSimulatorController {

    private final TerminalSimulatorService simulatorService;

    /**
     * POST /api/v1/simulate/purchase
     *
     * <p>Sends MTI 0100 with DE3=000000 (purchase).
     * Required fields: cardNumber, expiryDate, amount, currency.
     */
    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<SimulateResponse>> purchase(
            @RequestBody SimulateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(simulatorService.purchase(req)));
    }

    /**
     * POST /api/v1/simulate/withdrawal
     *
     * <p>Sends MTI 0200 with DE3=010000 (ATM cash withdrawal).
     * Required fields: cardNumber, expiryDate, amount, currency, pinBlock (recommended).
     */
    @PostMapping("/withdrawal")
    public ResponseEntity<ApiResponse<SimulateResponse>> withdrawal(
            @RequestBody SimulateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(simulatorService.withdrawal(req)));
    }

    /**
     * POST /api/v1/simulate/balance
     *
     * <p>Sends MTI 0100 with DE3=310000 (balance enquiry).
     * The FEP returns available balance in DE54 of the response.
     */
    @PostMapping("/balance")
    public ResponseEntity<ApiResponse<SimulateResponse>> balance(
            @RequestBody SimulateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(simulatorService.balanceEnquiry(req)));
    }

    /**
     * POST /api/v1/simulate/reversal
     *
     * <p>Sends MTI 0400 (reversal of a prior transaction).
     * Required fields: cardNumber, amount, originalStan, originalRrn.
     */
    @PostMapping("/reversal")
    public ResponseEntity<ApiResponse<SimulateResponse>> reversal(
            @RequestBody SimulateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(simulatorService.reversal(req)));
    }

    /**
     * POST /api/v1/simulate/network/signon
     *
     * <p>Sends MTI 0800 with DE70=0001 (terminal sign-on).
     */
    @PostMapping("/network/signon")
    public ResponseEntity<ApiResponse<SimulateResponse>> signOn(
            @RequestBody(required = false) SimulateRequest req) {
        SimulateRequest actual = req != null ? req
                : new SimulateRequest(null, null, null, null,
                                      null, null, null, null, null, null, null, null, "0001");
        return ResponseEntity.ok(ApiResponse.ok(simulatorService.networkManagement(actual)));
    }

    /**
     * POST /api/v1/simulate/network/echo
     *
     * <p>Sends MTI 0800 with DE70=0301 (echo/heartbeat test).
     */
    @PostMapping("/network/echo")
    public ResponseEntity<ApiResponse<SimulateResponse>> echo(
            @RequestBody(required = false) SimulateRequest req) {
        SimulateRequest actual = req != null ? req
                : new SimulateRequest(null, null, null, null,
                                      null, null, null, null, null, null, null, null, "0301");
        return ResponseEntity.ok(ApiResponse.ok(simulatorService.networkManagement(actual)));
    }
}
