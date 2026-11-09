package com.cba.payment;

import com.cba.common.exception.CbaException;
import com.cba.common.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The external payments gateway's settlement callback (backlog 1). No user token: the gateway
 * signs the raw body ({@link CallbackSignature}) with {@code EXTERNAL_PAYMENT_CALLBACK_SECRET}.
 * Unset secret → 503 (fails closed); a bad or stale signature → 401. Every callback is stored,
 * whatever happens to it.
 *
 * <p>Body: {@code {"reference":"EXT-…","networkReference":"…","status":"SETTLED|RETURNED|REJECTED","reason":"…"}}
 * — {@code reference} (ours, sent with the submit) or {@code networkReference} names the payment.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/external/callbacks")
@Tag(name = "Payments", description = "Internal transfers and payment operations")
public class ExternalPaymentCallbackController {

    private final ExternalPaymentOutcomeService outcomes;
    private final ExternalPaymentCallbackRepository callbacks;
    private final ObjectMapper objectMapper;
    private final String secret;

    public ExternalPaymentCallbackController(ExternalPaymentOutcomeService outcomes,
                                             ExternalPaymentCallbackRepository callbacks,
                                             ObjectMapper objectMapper,
                                             @Value("${app.payments.external.callback-secret:}") String secret) {
        this.outcomes = outcomes;
        this.callbacks = callbacks;
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "External payment settlement callback (signed by the gateway)",
            description = "Headers X-Gateway-Timestamp (epoch seconds, within 5 minutes) and "
                    + "X-Gateway-Signature: sha256=hex(HMAC-SHA256(secret, timestamp + '.' + body)). "
                    + "SETTLED completes a PROCESSING payment; RETURNED/REJECTED credit the source back.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> callback(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Gateway-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Gateway-Signature", required = false) String signature,
            HttpServletRequest request) {

        ExternalPaymentCallback row = new ExternalPaymentCallback();
        row.setRemoteAddress(request.getRemoteAddr());

        if (secret == null || secret.isBlank()) {
            log.error("External payment callback refused: EXTERNAL_PAYMENT_CALLBACK_SECRET is not set");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(
                    "CALLBACK_NOT_CONFIGURED", "External payment callbacks are not configured"));
        }
        CallbackSignature.Verdict verdict = CallbackSignature.verify(secret, timestamp, signature, body, Instant.now());
        JsonNode json = parse(body);
        if (json != null) {
            row.setReference(text(json, "reference", 50));
            row.setNetworkReference(text(json, "networkReference", 64));
            row.setOutcome(text(json, "status", 20));
            row.setReason(text(json, "reason", 2000));
        }
        if (verdict != CallbackSignature.Verdict.OK) {
            record(row, false, "INVALID_SIGNATURE");
            log.warn("External payment callback with {} signature from {}", verdict, request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                    "INVALID_SIGNATURE", "The callback signature is missing, stale or wrong"));
        }
        if (json == null || (row.getReference() == null && row.getNetworkReference() == null)) {
            record(row, true, "INVALID_CALLBACK");
            throw CbaException.badRequest("INVALID_CALLBACK", "The body must name reference or networkReference");
        }
        ExternalPaymentOutcomeService.Outcome outcome = switch (String.valueOf(row.getOutcome()).toUpperCase(Locale.ROOT)) {
            case "SETTLED" -> ExternalPaymentOutcomeService.Outcome.SETTLED;
            case "RETURNED" -> ExternalPaymentOutcomeService.Outcome.RETURNED;
            case "REJECTED", "FAILED" -> ExternalPaymentOutcomeService.Outcome.FAILED;
            default -> null;
        };
        if (outcome == null) {
            record(row, true, "INVALID_CALLBACK");
            throw CbaException.badRequest("INVALID_CALLBACK", "status must be SETTLED, RETURNED or REJECTED");
        }

        ExternalPaymentOutcomeService.Result result;
        try {
            result = outcomes.applyCallback(row.getReference(), row.getNetworkReference(), outcome, row.getReason());
        } catch (CbaException e) {
            record(row, true, e.getErrorCode());
            throw e;
        }
        row.setPaymentId(result.payment().id());
        record(row, true, result.repeat() ? "REPEAT" : "APPLIED");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", result.repeat() ? "REPEAT" : "APPLIED");
        data.put("paymentId", result.payment().id());
        data.put("reference", result.payment().referenceNumber());
        data.put("status", result.payment().status());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private void record(ExternalPaymentCallback row, boolean signatureOk, String result) {
        row.setSignatureOk(signatureOk);
        row.setResult(result.length() > 40 ? result.substring(0, 40) : result);
        try {
            callbacks.save(row);
        } catch (RuntimeException e) {
            log.error("Could not store external payment callback ({}): {}", result, e.getMessage());
        }
    }

    private JsonNode parse(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node != null && node.isObject() ? node : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String text(JsonNode json, String field, int max) {
        JsonNode v = json.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        String s = v.asText().trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
