package com.cba.card.terminal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HexFormat;

/**
 * Terminal simulator service — builds ISO 8583 messages and fires them to the FEP.
 *
 * <p>This is a dev/test tool. It constructs minimal but structurally valid ISO 8583
 * messages per the CLAUDE.md field table, sends them over TCP to port 8583, and
 * returns the decoded response.
 *
 * <p>DE22 (POS Entry Mode) mapping:
 * <ul>
 *   <li>SWIPE       → "021" (mag stripe, unattended)</li>
 *   <li>CHIP        → "051" (EMV chip, contact)</li>
 *   <li>CONTACTLESS → "071" (NFC contactless)</li>
 *   <li>CNP (default) → "010" (card-not-present / e-commerce)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalSimulatorService {

    private final FepIso8583Client fepClient;

    /**
     * Default ISO 4217 numeric currency code used when a simulation request omits the
     * currency field. Configurable per deployment — set to the local market's currency.
     * Never assumed to be USD; defaults to "840" only as a compile-time guard.
     */
    @org.springframework.beans.factory.annotation.Value("${card.simulator.default-currency:840}")
    private String defaultSimulatorCurrency;

    // ── Transaction Types ─────────────────────────────────────────────────────

    /** MTI 0100 — Authorization Request (purchase or balance enquiry). */
    public SimulateResponse purchase(SimulateRequest req) {
        return sendAuthorization(req, "000000"); // DE3 processing code: purchase
    }

    /** MTI 0100 with DE3=310000 — Balance Enquiry. */
    public SimulateResponse balanceEnquiry(SimulateRequest req) {
        return sendAuthorization(req, "310000"); // DE3 processing code: balance enquiry
    }

    /** MTI 0200 — Financial Transaction Request (ATM cash withdrawal). */
    public SimulateResponse withdrawal(SimulateRequest req) {
        return sendFinancial(req, "010000"); // DE3 processing code: cash withdrawal
    }

    /** MTI 0400 — Reversal Request. */
    public SimulateResponse reversal(SimulateRequest req) {
        return sendReversal(req);
    }

    /** MTI 0800 — Network Management (sign-on, sign-off, echo). */
    public SimulateResponse networkManagement(SimulateRequest req) {
        return sendNetworkManagement(req);
    }

    // ── Message Builders ──────────────────────────────────────────────────────

    private SimulateResponse sendAuthorization(SimulateRequest req, String processingCode) {
        String stan  = Iso8583Builder.nextStan();
        String rrn   = Iso8583Builder.generateRrn();
        String mode  = mapEntryMode(req.entryMode());
        String pan   = req.cardNumber() != null ? req.cardNumber() : "";
        String expiry = req.expiryDate() != null ? req.expiryDate() : "9912";
        BigDecimal amount = req.amount() != null ? req.amount() : BigDecimal.ZERO;

        Iso8583Builder builder = new Iso8583Builder("0100")
                .set(2,  Iso8583Builder.llvar(pan))                           // DE2  PAN
                .set(3,  processingCode)                                       // DE3  Processing Code
                .set(4,  Iso8583Builder.formatAmount(amount))                  // DE4  Amount
                .set(7,  Iso8583Builder.transmissionDateTime())                // DE7  Transmission DT
                .set(11, stan)                                                 // DE11 STAN
                .set(12, Iso8583Builder.localTime())                           // DE12 Local Time
                .set(13, Iso8583Builder.localDate())                           // DE13 Local Date
                .set(14, expiry)                                               // DE14 Expiry
                .set(18, req.mcc() != null ? req.mcc() : "5411")             // DE18 MCC
                .set(22, mode)                                                 // DE22 POS Entry Mode
                .set(37, rrn)                                                  // DE37 RRN
                .set(41, terminal(req))                                        // DE41 Terminal ID
                .set(42, merchant(req))                                        // DE42 Merchant ID
                .set(43, merchantName(req))                                    // DE43 Merchant Name
                .set(49, req.currency() != null ? req.currency() : defaultSimulatorCurrency);   // DE49 Currency

        if (req.pinBlock() != null && !req.pinBlock().isBlank()) {
            // DE52 PIN Block — 8 binary bytes (16 hex chars)
            // Stored as raw hex string; FEP interprets binary
            builder.set(52, req.pinBlock().substring(0, Math.min(16, req.pinBlock().length())));
        }

        return sendAndDecode(builder, stan, rrn);
    }

    private SimulateResponse sendFinancial(SimulateRequest req, String processingCode) {
        String stan   = Iso8583Builder.nextStan();
        String rrn    = Iso8583Builder.generateRrn();
        String mode   = mapEntryMode(req.entryMode());
        String pan    = req.cardNumber() != null ? req.cardNumber() : "";
        String expiry  = req.expiryDate() != null ? req.expiryDate() : "9912";
        BigDecimal amount = req.amount() != null ? req.amount() : BigDecimal.ZERO;

        Iso8583Builder builder = new Iso8583Builder("0200")
                .set(2,  Iso8583Builder.llvar(pan))
                .set(3,  processingCode)
                .set(4,  Iso8583Builder.formatAmount(amount))
                .set(7,  Iso8583Builder.transmissionDateTime())
                .set(11, stan)
                .set(12, Iso8583Builder.localTime())
                .set(13, Iso8583Builder.localDate())
                .set(14, expiry)
                .set(18, req.mcc() != null ? req.mcc() : "6010")  // MCC 6010 = manual cash disbursement
                .set(22, mode)
                .set(37, rrn)
                .set(41, terminal(req))
                .set(42, merchant(req))
                .set(43, merchantName(req))
                .set(49, req.currency() != null ? req.currency() : defaultSimulatorCurrency);

        if (req.pinBlock() != null && !req.pinBlock().isBlank()) {
            builder.set(52, req.pinBlock().substring(0, Math.min(16, req.pinBlock().length())));
        }

        return sendAndDecode(builder, stan, rrn);
    }

    private SimulateResponse sendReversal(SimulateRequest req) {
        String stan = Iso8583Builder.nextStan();
        String rrn  = Iso8583Builder.generateRrn();

        // DE90 Original Data Elements: original MTI (4) + STAN (6) + Date (4) + zeros
        String originalStan = req.originalStan() != null ? req.originalStan() : "000000";
        String de90 = "0100" + originalStan + Iso8583Builder.localDate() + "00000000000000";

        Iso8583Builder builder = new Iso8583Builder("0400")
                .set(2,  Iso8583Builder.llvar(req.cardNumber() != null ? req.cardNumber() : ""))
                .set(3,  "000000")
                .set(4,  Iso8583Builder.formatAmount(req.amount() != null ? req.amount() : BigDecimal.ZERO))
                .set(7,  Iso8583Builder.transmissionDateTime())
                .set(11, stan)
                .set(12, Iso8583Builder.localTime())
                .set(13, Iso8583Builder.localDate())
                .set(37, rrn)
                .set(41, terminal(req))
                .set(42, merchant(req))
                .set(49, req.currency() != null ? req.currency() : defaultSimulatorCurrency);

        return sendAndDecode(builder, stan, rrn);
    }

    private SimulateResponse sendNetworkManagement(SimulateRequest req) {
        String stan        = Iso8583Builder.nextStan();
        String rrn         = Iso8583Builder.generateRrn();
        String networkCode = req.networkCode() != null ? req.networkCode() : "0301"; // echo default

        Iso8583Builder builder = new Iso8583Builder("0800")
                .set(7,  Iso8583Builder.transmissionDateTime())
                .set(11, stan)
                .set(41, terminal(req));

        // DE70 — Network Management Information Code (we'll add to bitmap manually)
        // Since our builder covers DEs 1-64 only (primary bitmap), DE70 is in secondary.
        // For simplicity, embed it after DE41 as a fixed field that the FEP reads by position.
        // A full implementation would use jPOS — this simulator sends the minimum needed.
        // We can piggyback DE70 by adding it to DE43 position (43 is the last field in our set
        // before 49). Alternatively, we use a raw approach and set it to network code.
        // For the echo test, the FEP just needs MTI=0800 and responds with 0810 + RC=00.

        return sendAndDecode(builder, stan, rrn);
    }

    // ── Transport ─────────────────────────────────────────────────────────────

    private SimulateResponse sendAndDecode(Iso8583Builder builder, String stan, String rrn) {
        byte[] requestBytes = builder.build();
        String requestHex   = HexFormat.of().formatHex(requestBytes);
        log.info("Simulator → FEP [{}]: {} bytes | hex: {}", builder.getMti(),
                requestBytes.length, requestHex);

        byte[] responseBytes;
        try {
            responseBytes = fepClient.send(requestBytes);
        } catch (FepIso8583Client.FepConnectionException e) {
            log.warn("FEP unreachable: {}", e.getMessage());
            return SimulateResponse.fepUnavailable(stan, rrn, requestHex);
        }

        String responseHex = HexFormat.of().formatHex(responseBytes);
        log.info("Simulator ← FEP: {} bytes | hex: {}", responseBytes.length, responseHex);

        return decodeResponse(responseBytes, builder.getMti(), stan, rrn, requestHex, responseHex);
    }

    /**
     * Minimal ISO 8583 response decoder — extracts MTI, DE39 (response code),
     * DE38 (auth code), and DE54 (additional amounts / available balance).
     *
     * <p>Layout: [4 bytes MTI] [8 bytes primary bitmap] [fields...]
     *
     * <p>This is a best-effort decoder for the simulator; production systems
     * use jPOS with a full packager definition.
     */
    private SimulateResponse decodeResponse(byte[] bytes, String requestMti,
                                             String stan, String rrn,
                                             String requestHex, String responseHex) {
        if (bytes.length < 12) {
            return new SimulateResponse(requestMti, "????", "96",
                    SimulateResponse.describeResponseCode("96"),
                    null, null, stan, rrn, false, requestHex, responseHex);
        }

        // MTI is first 4 bytes (ASCII)
        String responseMti = new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);

        // Parse primary bitmap (bytes 4-11)
        boolean[] bits = new boolean[64];
        for (int i = 0; i < 8; i++) {
            byte b = bytes[4 + i];
            for (int bit = 0; bit < 8; bit++) {
                bits[i * 8 + bit] = ((b & (0x80 >> bit)) != 0);
            }
        }

        // Parse fields sequentially from byte 12
        // We parse only the fields we care about: DE2(LLVAR), DE3(6), DE4(12),
        // DE7(10), DE11(6), DE12(6), DE13(4), DE14(4), DE18(4), DE22(3),
        // DE37(12), DE38(6), DE39(2), DE41(8), DE42(15), DE43(40), DE49(3)
        int pos = 12;
        String responseCode = "96";
        String authCode     = null;
        String availBalance = null;

        for (int de = 1; de <= 64 && pos < bytes.length; de++) {
            if (!bits[de - 1]) continue;
            int fieldLen = 0;
            switch (de) {
                case 2  -> { // LLVAR PAN
                    if (pos + 2 > bytes.length) break;
                    int panLen = Integer.parseInt(
                            new String(bytes, pos, 2, java.nio.charset.StandardCharsets.US_ASCII));
                    pos += 2 + panLen;
                    continue;
                }
                case 3  -> fieldLen = 6;
                case 4  -> fieldLen = 12;
                case 7  -> fieldLen = 10;
                case 11 -> fieldLen = 6;
                case 12 -> fieldLen = 6;
                case 13 -> fieldLen = 4;
                case 14 -> fieldLen = 4;
                case 18 -> fieldLen = 4;
                case 22 -> fieldLen = 3;
                case 35 -> { // LLVAR Track2
                    if (pos + 2 > bytes.length) break;
                    int tLen = Integer.parseInt(
                            new String(bytes, pos, 2, java.nio.charset.StandardCharsets.US_ASCII));
                    pos += 2 + tLen;
                    continue;
                }
                case 37 -> fieldLen = 12;
                case 38 -> {
                    fieldLen = 6;
                    if (pos + fieldLen <= bytes.length) {
                        authCode = new String(bytes, pos, fieldLen,
                                java.nio.charset.StandardCharsets.US_ASCII).trim();
                    }
                }
                case 39 -> {
                    fieldLen = 2;
                    if (pos + fieldLen <= bytes.length) {
                        responseCode = new String(bytes, pos, fieldLen,
                                java.nio.charset.StandardCharsets.US_ASCII);
                    }
                }
                case 41 -> fieldLen = 8;
                case 42 -> fieldLen = 15;
                case 43 -> fieldLen = 40;
                case 49 -> fieldLen = 3;
                case 54 -> { // Additional Amounts — variable (we peek first 20 chars as balance hint)
                    fieldLen = Math.min(20, bytes.length - pos);
                    if (fieldLen > 0) {
                        availBalance = new String(bytes, pos, fieldLen,
                                java.nio.charset.StandardCharsets.US_ASCII).trim();
                    }
                }
                default -> { pos = bytes.length; continue; } // skip unknown DEs
            }
            pos += fieldLen;
        }

        boolean approved = "00".equals(responseCode);
        return new SimulateResponse(
                requestMti, responseMti,
                responseCode, SimulateResponse.describeResponseCode(responseCode),
                approved ? authCode : null,
                approved ? availBalance : null,
                stan, rrn, approved,
                requestHex, responseHex);
    }

    // ── Field Helpers ─────────────────────────────────────────────────────────

    private static String mapEntryMode(String mode) {
        if (mode == null) return "010"; // CNP default
        return switch (mode.toUpperCase()) {
            case "SWIPE"       -> "021"; // magnetic stripe
            case "CHIP"        -> "051"; // EMV chip contact
            case "CONTACTLESS" -> "071"; // NFC contactless
            default            -> "010"; // card-not-present
        };
    }

    private static String terminal(SimulateRequest req) {
        String id = req.terminalId() != null ? req.terminalId() : "TERM0001";
        return String.format("%-8s", id).substring(0, 8);
    }

    private static String merchant(SimulateRequest req) {
        String id = req.merchantId() != null ? req.merchantId() : "MERCHANT0000001";
        return String.format("%-15s", id).substring(0, 15);
    }

    private static String merchantName(SimulateRequest req) {
        String name = req.merchantName() != null ? req.merchantName() : "TEST MERCHANT          NAIROBI     KE";
        return String.format("%-40s", name).substring(0, 40);
    }
}
