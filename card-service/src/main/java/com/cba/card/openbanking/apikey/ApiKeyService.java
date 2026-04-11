package com.cba.card.openbanking.apikey;

import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Issue ─────────────────────────────────────────────────────────────────

    /**
     * Generate a new API key.
     *
     * @return {@link IssueResult} containing the saved {@link ApiKey} record
     *         AND the raw plaintext key — shown once, never retrievable again.
     */
    @Transactional
    public IssueResult issueKey(String name, UUID createdBy, List<String> scopes) {
        // Raw key: "cba_" prefix + 32 random bytes base64url-encoded
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String rawKey = "cba_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        ApiKey key = new ApiKey();
        key.setName(name);
        key.setKeyHash(sha256hex(rawKey));
        key.setCreatedBy(createdBy);
        key.setScopes(scopes);
        key = repository.save(key);

        log.info("API key issued: id={} name={} scopes={}", key.getId(), name, scopes);
        return new IssueResult(key, rawKey);
    }

    // ── Verify (called by filter) ─────────────────────────────────────────────

    /**
     * Verify a raw key presented in the {@code Authorization: ApiKey {key}} header.
     * Updates {@code last_used_at} on success.
     */
    @Transactional
    public Optional<ApiKey> verify(String rawKey) {
        String hash = sha256hex(rawKey);
        Optional<ApiKey> opt = repository.findByKeyHashAndActiveTrue(hash);
        opt.ifPresent(k -> {
            k.setLastUsedAt(OffsetDateTime.now());
            repository.save(k);
        });
        return opt;
    }

    // ── List / Revoke ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ApiKey> listActive() {
        return repository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKey key = repository.findById(id)
                .orElseThrow(() -> CbaException.notFound("API_KEY_NOT_FOUND", "API key not found: " + id));
        key.setActive(false);
        repository.save(key);
        log.info("API key revoked: id={} name={}", id, key.getName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String sha256hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Result of key issuance — carries the plaintext key shown once only. */
    public record IssueResult(ApiKey apiKey, String rawKey) {}
}
