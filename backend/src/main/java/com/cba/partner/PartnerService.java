package com.cba.partner;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerOrganizationRepository orgRepo;
    private final PartnerUserRepository userRepo;
    private final PartnerApiKeyRepository apiKeyRepo;
    private final PartnerApplicationRepository applicationRepo;
    private final PartnerWebhookRepository webhookRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PartnerJwtService jwtService;

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
        String hash = passwordEncoder.encode(rawKey);
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
    }

    @Transactional
    public void rejectApplication(UUID orgId) {
        PartnerOrganization org = orgRepo.findById(orgId)
                .orElseThrow(() -> CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found"));
        org.setApplicationStatus("REJECTED");
        orgRepo.save(org);
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
