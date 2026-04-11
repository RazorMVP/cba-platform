package com.cba.card.threeds;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 3D Secure 2.x Access Control Server (ACS) endpoints.
 *
 * <p>These endpoints are NOT protected by JWT — they are called by:
 * <ul>
 *   <li>{@code POST /3ds/acs/areq} — Directory Server (Visa/MC infrastructure),
 *       authenticates via mTLS in production; open in dev.</li>
 *   <li>{@code GET  /3ds/acs/challenge/{id}} — cardholder's browser, no auth.</li>
 *   <li>{@code POST /3ds/acs/challenge/{id}/verify} — cardholder's browser form post.</li>
 * </ul>
 *
 * <p>Security is handled by the dedicated {@code @Order(0)} filter chain in
 * {@code SecurityConfig} that matches {@code /3ds/acs/**} and permits all.
 */
@Slf4j
@RestController
@RequestMapping("/3ds/acs")
@RequiredArgsConstructor
public class ThreeDsController {

    private final ThreeDsService threeDsService;

    // -------------------------------------------------------------------------
    // AReq — called by Directory Server
    // -------------------------------------------------------------------------

    /**
     * Receive Authentication Request (AReq) from the Directory Server.
     * Returns an Authentication Response (ARes) with transStatus Y/N/C.
     *
     * <p>POST body is JSON matching the EMVCo 3DS 2.3 AReq specification.
     */
    @PostMapping(value = "/areq",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AResMessage> receiveAReq(@Valid @RequestBody AReqMessage req) {
        log.info("3DS AReq received: serverTransId={} merchant={}",
                req.threeDSServerTransID(), req.merchantName());
        AResMessage ares = threeDsService.processAReq(req);
        log.info("3DS ARes: acsTransId={} transStatus={}",
                ares.acsTransID(), ares.transStatus());
        return ResponseEntity.ok(ares);
    }

    // -------------------------------------------------------------------------
    // Challenge page — served to cardholder's browser
    // -------------------------------------------------------------------------

    /**
     * Render the OTP challenge page for the cardholder's browser.
     *
     * <p>Returns HTML because the cardholder's browser is redirected here
     * mid-checkout by the Directory Server. A JSON 200 would appear as raw
     * text in the browser and break the 3DS flow.
     *
     * <p>The HTML page submits the OTP via a standard form POST to
     * {@code /3ds/acs/challenge/{acsTransId}/verify}.
     */
    @GetMapping(value = "/challenge/{acsTransId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderChallengePage(@PathVariable UUID acsTransId) {
        ThreeDsSession session;
        try {
            session = threeDsService.getSession(acsTransId);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPage("Session not found",
                    "This authentication session is invalid or has expired."));
        }

        if (session.getStatus() == ThreeDsStatus.AUTHENTICATED) {
            return ResponseEntity.ok(successPage(session.getEciIndicator()));
        }
        if (session.getStatus() == ThreeDsStatus.FAILED
                || session.getStatus() == ThreeDsStatus.REJECTED) {
            return ResponseEntity.ok(errorPage("Authentication Failed",
                    "Your session has been locked due to too many incorrect attempts."));
        }

        return ResponseEntity.ok(challengeFormPage(acsTransId, session));
    }

    // -------------------------------------------------------------------------
    // Challenge OTP verification — called by form POST from challenge page
    // -------------------------------------------------------------------------

    /**
     * Verify the OTP submitted by the cardholder.
     *
     * <p>Accepts both {@code application/json} (REST clients, mobile 3DS SDK)
     * and {@code application/x-www-form-urlencoded} (browser form post).
     * Returns JSON in all cases — the challenge HTML page's JavaScript reads
     * the JSON response and refreshes accordingly.
     */
    @PostMapping(value = "/challenge/{acsTransId}/verify",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChallengeVerifyResponse> verifyChallenge(
            @PathVariable UUID acsTransId,
            @Valid @RequestBody ChallengeSubmitRequest request) {

        ChallengeVerifyResponse response = threeDsService.verifyChallenge(acsTransId, request.otp());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // HTML page templates (inline — avoids Thymeleaf dependency in card-service)
    // -------------------------------------------------------------------------

    private String challengeFormPage(UUID acsTransId, ThreeDsSession session) {
        String merchant = session.getMerchantName() != null ? session.getMerchantName() : "Merchant";
        String amountStr = session.getAmount() != null
                ? session.getAmount().toPlainString()
                : "";
        String currency = session.getCurrency() != null ? session.getCurrency() : "";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Secure Authentication — CBA</title>
                  <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #040609;
                           display: flex; align-items: center; justify-content: center;
                           min-height: 100vh; }
                    .card { background: #fff; border-radius: 12px; padding: 40px 32px;
                             max-width: 420px; width: 100%%; box-shadow: 0 8px 32px rgba(0,0,0,.4); }
                    .logo { font-size: 22px; font-weight: 700; color: #1e2833; margin-bottom: 4px; }
                    .subtitle { font-size: 13px; color: #888; margin-bottom: 28px; }
                    .merchant-info { background: #f5f7fa; border-radius: 8px; padding: 14px 16px;
                                     margin-bottom: 24px; }
                    .merchant-label { font-size: 11px; color: #888; text-transform: uppercase;
                                      letter-spacing: .5px; }
                    .merchant-name { font-size: 16px; font-weight: 600; color: #1e2833; margin-top: 4px; }
                    .merchant-amount { font-size: 13px; color: #444; margin-top: 2px; }
                    label { display: block; font-size: 13px; font-weight: 600; color: #1e2833;
                             margin-bottom: 6px; }
                    input[type=text] { width: 100%%; padding: 12px 14px; border: 1.5px solid #dde1e7;
                                       border-radius: 8px; font-size: 22px; letter-spacing: 8px;
                                       text-align: center; outline: none; color: #1e2833; }
                    input[type=text]:focus { border-color: #1e2833; }
                    .hint { font-size: 12px; color: #888; margin-top: 6px; margin-bottom: 24px; }
                    button { width: 100%%; background: #1e2833; color: #fff; border: none;
                              border-radius: 8px; padding: 14px; font-size: 15px;
                              font-weight: 600; cursor: pointer; }
                    button:hover { background: #2c3e50; }
                    #error-msg { color: #d32f2f; font-size: 13px; margin-top: 10px; display: none; }
                    #success-msg { color: #388e3c; font-size: 13px; margin-top: 10px; display: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="logo">CBA Secure</div>
                    <div class="subtitle">3D Secure Authentication</div>
                    <div class="merchant-info">
                      <div class="merchant-label">You are paying</div>
                      <div class="merchant-name">%s</div>
                      <div class="merchant-amount">%s %s</div>
                    </div>
                    <label for="otp">Enter the OTP sent to your registered phone</label>
                    <input type="text" id="otp" name="otp" maxlength="8"
                           autocomplete="one-time-code" inputmode="numeric"
                           placeholder="••••••" autofocus>
                    <div class="hint">The code is valid for 10 minutes. Do not share it with anyone.</div>
                    <button onclick="submitOtp()">Verify</button>
                    <div id="error-msg"></div>
                    <div id="success-msg"></div>
                  </div>
                  <script>
                    async function submitOtp() {
                      const otp = document.getElementById('otp').value.trim();
                      const errEl = document.getElementById('error-msg');
                      const okEl  = document.getElementById('success-msg');
                      errEl.style.display = 'none';
                      okEl.style.display  = 'none';
                      if (!otp) { errEl.textContent = 'Please enter the OTP.'; errEl.style.display = 'block'; return; }
                      const resp = await fetch('/3ds/acs/challenge/%s/verify', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ otp })
                      });
                      const data = await resp.json();
                      if (data.status === 'AUTHENTICATED') {
                        okEl.textContent = data.message; okEl.style.display = 'block';
                        setTimeout(() => { window.location.reload(); }, 1500);
                      } else if (data.status === 'LOCKED') {
                        errEl.textContent = data.message; errEl.style.display = 'block';
                        document.querySelector('button').disabled = true;
                      } else {
                        errEl.textContent = data.message; errEl.style.display = 'block';
                      }
                    }
                  </script>
                </body>
                </html>
                """.formatted(merchant, amountStr, currency, acsTransId);
    }

    private String successPage(String eci) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <title>Authentication Successful — CBA</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #040609; display: flex;
                           align-items: center; justify-content: center; min-height: 100vh; }
                    .card { background: #fff; border-radius: 12px; padding: 48px 40px;
                             max-width: 380px; text-align: center; }
                    .icon { font-size: 56px; margin-bottom: 16px; }
                    h2 { color: #388e3c; margin-bottom: 8px; }
                    p  { color: #666; font-size: 14px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">&#10003;</div>
                    <h2>Authentication Successful</h2>
                    <p>Your payment has been verified. You can close this window.</p>
                    <p style="margin-top:12px; font-size:12px; color:#aaa;">ECI: %s</p>
                  </div>
                </body>
                </html>
                """.formatted(eci != null ? eci : "");
    }

    private String errorPage(String title, String message) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <title>%s — CBA</title>
                  <style>
                    body { font-family: Arial, sans-serif; background: #040609; display: flex;
                           align-items: center; justify-content: center; min-height: 100vh; }
                    .card { background: #fff; border-radius: 12px; padding: 48px 40px;
                             max-width: 380px; text-align: center; }
                    .icon { font-size: 56px; margin-bottom: 16px; }
                    h2 { color: #d32f2f; margin-bottom: 8px; }
                    p  { color: #666; font-size: 14px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">&#10007;</div>
                    <h2>%s</h2>
                    <p>%s</p>
                  </div>
                </body>
                </html>
                """.formatted(title, title, message);
    }
}
