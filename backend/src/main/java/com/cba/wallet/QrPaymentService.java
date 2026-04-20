package com.cba.wallet;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.common.exception.CbaException;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrPaymentService {

    private final QrPaymentTokenRepository tokenRepository;
    private final AccountRepository accountRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record GenerateQrRequest(
            UUID accountId,
            BigDecimal presetAmount,   // optional
            String reference,          // optional label shown to payer
            Integer expiryMinutes      // optional; default 60
    ) {}

    public record QrResponse(
            UUID tokenId,
            String qrBase64,           // base64 PNG — use as <img src="data:image/png;base64,{qrBase64}">
            String payload,            // raw JSON payload string (for debugging / re-encoding)
            String accountNumber,
            String accountName,
            BigDecimal presetAmount,
            String currency,
            OffsetDateTime expiresAt
    ) {}

    public record DecodeAndPayRequest(
            String token,
            UUID payerAccountId,
            BigDecimal amount          // required only when QR has no preset amount
    ) {}

    // ── Generate ──────────────────────────────────────────────────────────────

    @Transactional
    public QrResponse generateQr(GenerateQrRequest req) {
        Account account = accountRepository.findById(req.accountId())
                .orElseThrow(() -> CbaException.notFound("Account", req.accountId().toString()));

        if (account.getStatus() == null || !account.getStatus().name().equals("ACTIVE")) {
            throw CbaException.badRequest("ACCOUNT_NOT_ACTIVE", "Only ACTIVE accounts can receive QR payments");
        }

        int expiryMinutes = req.expiryMinutes() != null && req.expiryMinutes() > 0 ? req.expiryMinutes() : 60;
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(expiryMinutes);

        // Build the payload map that will be encoded into the QR
        Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
        payloadMap.put("v", "1");
        payloadMap.put("bank", "NUBBANK");
        payloadMap.put("accountId", account.getId().toString());
        payloadMap.put("accountNumber", account.getAccountNumber());
        payloadMap.put("currency", account.getCurrencyCode());
        if (req.presetAmount() != null) payloadMap.put("amount", req.presetAmount());
        if (req.reference() != null && !req.reference().isBlank()) payloadMap.put("ref", req.reference());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize QR payload", e);
        }

        // Persist token for server-side validation at decode time
        QrPaymentToken token = new QrPaymentToken();
        token.setToken(payloadJson);
        token.setAccountId(account.getId());
        token.setPresetAmount(req.presetAmount());
        token.setReference(req.reference());
        token.setExpiresAt(expiresAt);
        token = tokenRepository.save(token);

        String qrBase64 = renderQrBase64(payloadJson);

        return new QrResponse(
                token.getId(), qrBase64, payloadJson,
                account.getAccountNumber(),
                account.getCustomer() != null ? account.getCustomer().getId().toString() : null,
                req.presetAmount(), account.getCurrencyCode(), expiresAt
        );
    }

    // ── Decode & Pay ──────────────────────────────────────────────────────────

    @Transactional
    public Object decodeAndPay(DecodeAndPayRequest req, String initiatedBy) {
        QrPaymentToken token = tokenRepository.findByToken(req.token())
                .orElseThrow(() -> CbaException.badRequest("INVALID_QR", "QR code not recognised or already used"));

        if (token.isUsed()) {
            throw CbaException.badRequest("QR_ALREADY_USED", "This QR code has already been used");
        }
        if (token.getExpiresAt() != null && OffsetDateTime.now().isAfter(token.getExpiresAt())) {
            throw CbaException.badRequest("QR_EXPIRED", "This QR code has expired");
        }

        BigDecimal amount = token.getPresetAmount() != null ? token.getPresetAmount() : req.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw CbaException.badRequest("AMOUNT_REQUIRED",
                    "This QR has no preset amount — provide amount in the request body");
        }

        // Mark used before transfer to prevent race-condition double-spend
        token.setUsed(true);
        tokenRepository.save(token);

        TransferRequest transfer = new TransferRequest(
                req.payerAccountId(),
                token.getAccountId(),
                amount,
                token.getReference() != null ? "QR: " + token.getReference() : "QR Payment",
                null
        );
        return paymentService.transfer(transfer, initiatedBy);
    }

    // ── Refresh (re-generate QR for same account, invalidate old) ─────────────

    @Transactional
    public QrResponse refreshQr(UUID accountId, Integer expiryMinutes) {
        return generateQr(new GenerateQrRequest(accountId, null, null, expiryMinutes));
    }

    // ── ZXing render ──────────────────────────────────────────────────────────

    private String renderQrBase64(String content) {
        try {
            var writer = new QRCodeWriter();
            var matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
            var stream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", stream);
            return Base64.getEncoder().encodeToString(stream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("QR rendering failed", e);
        }
    }
}
