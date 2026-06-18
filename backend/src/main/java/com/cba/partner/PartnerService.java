package com.cba.partner;

import com.cba.common.exception.CbaException;
import com.cba.openbanking.ConsentRepository;
import com.cba.openbanking.ConsentStatus;
import com.cba.openbanking.OpenBankingConsent;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerOrganizationRepository orgRepo;
    private final PartnerUserRepository userRepo;
    private final PartnerApiKeyRepository apiKeyRepo;
    private final PartnerApplicationRepository applicationRepo;
    private final PartnerWebhookRepository webhookRepo;
    private final PartnerUsageSnapshotRepository usageRepo;
    private final PartnerWebhookDeliveryRepository deliveryRepo;
    private final ConsentRepository consentRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PartnerJwtService jwtService;
    private final PartnerWebhookDeliveryService webhookDelivery;

    // ── Registration ─────────────────────────────────────────────────────────

    @Transactional
    public void register(String organizationName, String email, String password) {
        if (userRepo.existsByEmail(email)) {
            throw CbaException.conflict("PARTNER_EMAIL_EXISTS", "Email already registered");
        }
        PartnerOrganization org = orgRepo.save(PartnerOrganization.builder()
                .name(organizationName)
                .status(PartnerStatus.SANDBOX)
                .tier("BASIC")
                .environment(PartnerEnvironment.SANDBOX)
                .build());

        userRepo.save(PartnerUser.builder()
                .organization(org)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role("DEVELOPER")
                .active(true)
                .build());
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        PartnerUser user = userRepo.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> CbaException.badRequest("INVALID_CREDENTIALS", "Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw CbaException.badRequest("INVALID_CREDENTIALS", "Invalid credentials");
        }
        String token = jwtService.generateToken(user);
        return new LoginResult(token, toUserDto(user));
    }

    // ── API Keys ──────────────────────────────────────────────────────────────

    @Transactional
    public String issueApiKey(UUID orgId, String name, List<String> scopes) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));

        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        String rawKey = "cba_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = PartnerApiKeys.hash(rawKey);  // SHA-256: deterministic, lookup-able
        String prefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        apiKeyRepo.save(PartnerApiKey.builder()
                .organization(org)
                .name(name)
                .keyHash(hash)
                .keyPrefix(prefix)
                .scopes(scopes)
                .tier("BASIC")
                .active(true)
                .build());
        webhookDelivery.publishEvent(orgId, "API_KEY.CREATED",
                Map.of("name", name == null ? "" : name, "keyPrefix", prefix));
        return rawKey;
    }

    @Transactional(readOnly = true)
    public List<PartnerApiKey> listApiKeys(UUID orgId) {
        return apiKeyRepo.findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(orgId);
    }

    @Transactional
    public void revokeApiKey(UUID orgId, UUID keyId) {
        PartnerApiKey key = apiKeyRepo.findById(keyId)
                .orElseThrow(() -> CbaException.notFound("KEY_NOT_FOUND", "API key not found"));
        if (!key.getOrganization().getId().equals(orgId)) {
            throw CbaException.notFound("KEY_NOT_FOUND", "API key not found");
        }
        key.setActive(false);
        apiKeyRepo.save(key);
        webhookDelivery.publishEvent(orgId, "API_KEY.REVOKED", Map.of("keyId", keyId.toString()));
    }

    // ── Webhooks ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PartnerWebhook> listWebhooks(UUID orgId) {
        return webhookRepo.findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(orgId);
    }

    @Transactional
    public PartnerWebhook createWebhook(UUID orgId, String name, String callbackUrl, String secret, List<String> events) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        return webhookRepo.save(PartnerWebhook.builder()
                .organization(org)
                .name(name)
                .callbackUrl(callbackUrl)
                .secret(secret)
                .events(events)
                .active(true)
                .build());
    }

    @Transactional
    public void deleteWebhook(UUID orgId, UUID webhookId) {
        PartnerWebhook webhook = webhookRepo.findById(webhookId)
                .orElseThrow(() -> CbaException.notFound("WEBHOOK_NOT_FOUND", "Webhook not found"));
        if (!webhook.getOrganization().getId().equals(orgId)) {
            throw CbaException.notFound("WEBHOOK_NOT_FOUND", "Webhook not found");
        }
        webhook.setActive(false);
        webhookRepo.save(webhook);
    }

    // ── Organizations (admin) ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PartnerOrganization> listAll() {
        return orgRepo.findAll();
    }

    @Transactional(readOnly = true)
    public PartnerOrganization getOrg(UUID id) {
        return orgRepo.findById(id).orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
    }

    @Transactional
    public void updateOrg(UUID orgId, String name, String website) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        if (name != null && !name.isBlank()) org.setName(name);
        if (website != null) org.setWebsite(website);
        orgRepo.save(org);
    }

    @Transactional
    public void approveProduction(UUID orgId, String approvedBy) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        org.setStatus(PartnerStatus.PRODUCTION);
        org.setEnvironment(PartnerEnvironment.PRODUCTION);
        org.setApplicationStatus("APPROVED");
        org.setApprovedBy(approvedBy);
        org.setApprovedAt(Instant.now());
        orgRepo.save(org);
        webhookDelivery.publishEvent(orgId, "APPLICATION.APPROVED",
                Map.of("approvedBy", approvedBy == null ? "" : approvedBy));
    }

    @Transactional
    public void rejectApplication(UUID orgId) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        org.setApplicationStatus("REJECTED");
        orgRepo.save(org);
        webhookDelivery.publishEvent(orgId, "APPLICATION.REJECTED", Map.of("orgId", orgId.toString()));
    }

    // ── Production Application ────────────────────────────────────────────────

    @Transactional
    public void submitApplication(UUID orgId, PartnerApplicationRequest req) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        applicationRepo.save(PartnerApplication.builder()
                .organization(org)
                .businessType(req.businessType())
                .useCase(req.useCase())
                .estimatedMonthlyCalls(req.estimatedMonthlyCalls())
                .website(req.website())
                .technicalContact(req.technicalContact())
                .complianceNotes(req.complianceNotes())
                .status("PENDING_REVIEW")
                .build());
        org.setStatus(PartnerStatus.PENDING_REVIEW);
        org.setApplicationStatus("PENDING_REVIEW");
        orgRepo.save(org);
    }

    // ── Partner User settings ─────────────────────────────────────────────────

    @Transactional
    public void updateUserEmail(UUID userId, String email) {
        PartnerUser user = userRepo.findById(userId)
                .orElseThrow(() -> CbaException.notFound("USER_NOT_FOUND", "User not found"));
        if (!user.getEmail().equals(email) && userRepo.existsByEmail(email)) {
            throw CbaException.conflict("EMAIL_EXISTS", "Email already in use");
        }
        user.setEmail(email);
        userRepo.save(user);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        PartnerUser user = userRepo.findById(userId)
                .orElseThrow(() -> CbaException.notFound("USER_NOT_FOUND", "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw CbaException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    // ── Consents ─────────────────────────────────────────────────────────────

    /**
     * Lists all OB consents where the tppClientId matches the partner org's ID.
     * Partners set their orgId as tppClientId when initiating consent via the OB API.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConsentsForOrg(UUID orgId) {
        // Verify org exists
        orgRepo.findById(orgId).orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        return consentRepo.findByTppClientIdOrderByCreatedAtDesc(orgId.toString()).stream()
                .map(this::toConsentMap)
                .toList();
    }

    @Transactional
    public void revokeConsentForOrg(UUID orgId, UUID consentId) {
        orgRepo.findById(orgId).orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        OpenBankingConsent consent = consentRepo.findById(consentId)
                .orElseThrow(() -> CbaException.notFound("CONSENT_NOT_FOUND", "Consent not found"));
        if (!orgId.toString().equals(consent.getTppClientId())) {
            throw CbaException.notFound("CONSENT_NOT_FOUND", "Consent not found");
        }
        consent.setStatus(ConsentStatus.REVOKED);
        consentRepo.save(consent);
    }

    private Map<String, Object> toConsentMap(OpenBankingConsent c) {
        return Map.of(
                "id", c.getId().toString(),
                "consentId", c.getConsentId(),
                "status", c.getStatus().name(),
                "scopes", c.getScopes() != null ? c.getScopes() : List.of(),
                "expiryDate", c.getExpiryDate() != null ? c.getExpiryDate().toString() : "",
                "createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : ""
        );
    }

    // ── Usage metering ─────────────────────────────────────────────────────────

    /** Trailing-30-day usage for one organization, with daily series and top endpoints. */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsage(UUID orgId) {
        LocalDate from = LocalDate.now().minusDays(29);
        List<PartnerUsageSnapshot> snaps =
                usageRepo.findByOrganizationIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(orgId, from);

        long total = snaps.stream().mapToLong(PartnerUsageSnapshot::getTotalCalls).sum();
        long success = snaps.stream().mapToLong(PartnerUsageSnapshot::getSuccessCalls).sum();
        long error = snaps.stream().mapToLong(PartnerUsageSnapshot::getErrorCalls).sum();

        List<Map<String, Object>> dailyCalls = snaps.stream()
                .map(s -> Map.<String, Object>of(
                        "date", s.getSnapshotDate().toString(),
                        "count", s.getTotalCalls()))
                .toList();

        Map<String, Integer> merged = new HashMap<>();
        for (PartnerUsageSnapshot s : snaps) {
            if (s.getTopEndpoints() != null) {
                s.getTopEndpoints().forEach((k, v) -> merged.merge(k, v, Integer::sum));
            }
        }
        List<Map<String, Object>> topEndpoints = merged.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.<String, Object>of("endpoint", e.getKey(), "count", e.getValue()))
                .toList();

        long deliveriesTotal = deliveryRepo.countByOrg(orgId);
        long deliveriesOk = deliveryRepo.countDeliveredByOrg(orgId);
        double webhookDeliveryRate = deliveriesTotal == 0
                ? 0 : Math.round(deliveriesOk * 1000.0 / deliveriesTotal) / 10.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRequests", total);
        out.put("successRequests", success);
        out.put("failedRequests", error);
        out.put("webhookDeliveryRate", webhookDeliveryRate);
        out.put("dailyCalls", dailyCalls);
        out.put("topEndpoints", topEndpoints);
        return out;
    }

    /** Per-organization usage totals over the last {@code days} days (admin view). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllUsage(int days) {
        LocalDate from = LocalDate.now().minusDays(Math.max(0, days - 1));
        List<PartnerUsageSnapshot> snaps = usageRepo.findBySnapshotDateGreaterThanEqual(from);

        Map<UUID, long[]> agg = new HashMap<>(); // [total, success, error]
        for (PartnerUsageSnapshot s : snaps) {
            long[] a = agg.computeIfAbsent(s.getOrganizationId(), k -> new long[3]);
            a[0] += s.getTotalCalls();
            a[1] += s.getSuccessCalls();
            a[2] += s.getErrorCalls();
        }
        if (agg.isEmpty()) return List.of();

        Map<UUID, String> names = orgRepo.findAllById(agg.keySet()).stream()
                .collect(Collectors.toMap(PartnerOrganization::getId, PartnerOrganization::getName));

        return agg.entrySet().stream()
                .map(e -> {
                    long t = e.getValue()[0], s = e.getValue()[1], er = e.getValue()[2];
                    double rate = t == 0 ? 0 : Math.round(s * 1000.0 / t) / 10.0;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("organizationId", e.getKey().toString());
                    m.put("organizationName", names.getOrDefault(e.getKey(), "—"));
                    m.put("totalCalls", t);
                    m.put("successCalls", s);
                    m.put("errorCalls", er);
                    m.put("successRate", rate);
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("totalCalls"), (Long) a.get("totalCalls")))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PartnerUserDto toUserDto(PartnerUser u) {
        return new PartnerUserDto(
                u.getId().toString(),
                u.getEmail(),
                u.getRole(),
                u.getOrganization().getId().toString(),
                u.getOrganization().getName(),
                u.getOrganization().getStatus().name(),
                u.getOrganization().getTier(),
                u.getOrganization().getEnvironment().name()
        );
    }

    // ── Records / DTOs ────────────────────────────────────────────────────────

    public record LoginResult(String token, PartnerUserDto user) {}

    public record PartnerUserDto(
            String id, String email, String role,
            String organizationId, String organizationName,
            String status, String tier, String environment) {}
}
