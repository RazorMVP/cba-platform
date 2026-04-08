package com.cba.user;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    public record GenerateTokenRequest(UUID userId, TwoFactorToken.DeliveryMethod deliveryMethod) {}
    public record VerifyTokenRequest(String token) {}

    private static final int    TOKEN_LENGTH  = 6;
    private static final int    EXPIRY_MINS   = 10;
    private static final SecureRandom RANDOM  = new SecureRandom();

    private final TwoFactorTokenRepository tokenRepository;
    private final PlatformUserRepository   userRepository;
    private final AuditLogService          auditLogService;

    @Transactional
    public TwoFactorToken generateToken(GenerateTokenRequest req) {
        PlatformUser user = userRepository.findById(req.userId())
            .orElseThrow(() -> CbaException.notFound("PlatformUser", req.userId()));

        String code = String.format("%0" + TOKEN_LENGTH + "d",
            RANDOM.nextInt((int) Math.pow(10, TOKEN_LENGTH)));

        TwoFactorToken t = new TwoFactorToken();
        t.setUser(user);
        t.setToken(code);
        t.setDeliveryMethod(req.deliveryMethod() != null
            ? req.deliveryMethod() : TwoFactorToken.DeliveryMethod.EMAIL);
        t.setExpiresAt(OffsetDateTime.now().plusMinutes(EXPIRY_MINS));

        TwoFactorToken saved = tokenRepository.save(t);
        auditLogService.log("TwoFactorToken", saved.getId().toString(), "GENERATE", null, saved);
        return saved;
    }

    @Transactional
    public TwoFactorToken verifyToken(VerifyTokenRequest req) {
        TwoFactorToken t = tokenRepository.findByTokenAndVerifiedFalse(req.token())
            .orElseThrow(() -> CbaException.badRequest("TOKEN_NOT_FOUND",
                "Token not found or already verified"));

        if (t.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw CbaException.badRequest("TOKEN_EXPIRED", "Two-factor token has expired");
        }

        t.setVerified(true);
        TwoFactorToken saved = tokenRepository.save(t);
        auditLogService.log("TwoFactorToken", saved.getId().toString(), "VERIFY", null, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TwoFactorToken> listTokens(UUID userId) {
        return tokenRepository.findByUserId(userId);
    }
}
