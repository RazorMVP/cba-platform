package com.cba.partner;

import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerService — unit tests")
class PartnerServiceTest {

    @Mock PartnerOrganizationRepository orgRepo;
    @Mock PartnerUserRepository userRepo;
    @Mock PartnerApiKeyRepository apiKeyRepo;
    @Mock PartnerApplicationRepository applicationRepo;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock PartnerJwtService jwtService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;

    @InjectMocks PartnerService service;

    private UUID orgId;
    private PartnerOrganization org;
    private PartnerUser partnerUser;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();

        org = new PartnerOrganization();
        org.setId(orgId);
        org.setName("Acme Fintech");
        org.setStatus(PartnerStatus.SANDBOX);
        org.setTier("BASIC");
        org.setEnvironment(PartnerEnvironment.SANDBOX);

        partnerUser = new PartnerUser();
        partnerUser.setId(UUID.randomUUID());
        partnerUser.setEmail("dev@acme.io");
        partnerUser.setPasswordHash("$2a$10$hashedpassword");
        partnerUser.setRole("DEVELOPER");
        partnerUser.setActive(true);
        partnerUser.setOrganization(org);
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("register creates org and user when email is new")
        void register_success() {
            when(userRepo.existsByEmail("dev@acme.io")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(orgRepo.save(any())).thenReturn(org);

            assertThatCode(() -> service.register("Acme Fintech", "dev@acme.io", "Secret1!"))
                .doesNotThrowAnyException();
            verify(orgRepo).save(any(PartnerOrganization.class));
            verify(userRepo).save(any(PartnerUser.class));
        }

        @Test
        @DisplayName("register throws when email already registered")
        void register_duplicateEmail_throws() {
            when(userRepo.existsByEmail("dev@acme.io")).thenReturn(true);

            assertThatThrownBy(() -> service.register("Acme Fintech", "dev@acme.io", "Secret1!"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Email already registered");
        }
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("login returns token on valid credentials")
        void login_success() {
            when(userRepo.findByEmailAndActiveTrue("dev@acme.io")).thenReturn(Optional.of(partnerUser));
            when(passwordEncoder.matches("Secret1!", partnerUser.getPasswordHash())).thenReturn(true);
            when(jwtService.generateToken(partnerUser)).thenReturn("jwt-token-here");

            PartnerService.LoginResult result = service.login("dev@acme.io", "Secret1!");
            assertThat(result.token()).isEqualTo("jwt-token-here");
            assertThat(result.user().email()).isEqualTo("dev@acme.io");
        }

        @Test
        @DisplayName("login throws when user not found")
        void login_userNotFound_throws() {
            when(userRepo.findByEmailAndActiveTrue("nobody@acme.io")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login("nobody@acme.io", "Secret1!"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Invalid credentials");
        }

        @Test
        @DisplayName("login throws when password does not match")
        void login_wrongPassword_throws() {
            when(userRepo.findByEmailAndActiveTrue("dev@acme.io")).thenReturn(Optional.of(partnerUser));
            when(passwordEncoder.matches("WrongPass", partnerUser.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> service.login("dev@acme.io", "WrongPass"))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Invalid credentials");
        }
    }

    @Nested
    @DisplayName("API Keys")
    class ApiKeys {

        @Test
        @DisplayName("issueApiKey returns raw key starting with cba_")
        void issueApiKey_success() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
            // API keys are SHA-256 hashed (not via passwordEncoder) since Gap 5

            String rawKey = service.issueApiKey(orgId, "Test Key", List.of("accounts:read"));
            assertThat(rawKey).startsWith("cba_");
            verify(apiKeyRepo).save(any(PartnerApiKey.class));
        }

        @Test
        @DisplayName("issueApiKey throws when org not found")
        void issueApiKey_orgNotFound_throws() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.issueApiKey(orgId, "Key", List.of()))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("listApiKeys returns active keys for org")
        void listApiKeys_success() {
            PartnerApiKey key = new PartnerApiKey();
            key.setId(UUID.randomUUID());
            when(apiKeyRepo.findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(key));

            assertThat(service.listApiKeys(orgId)).hasSize(1);
        }

        @Test
        @DisplayName("revokeApiKey sets active=false")
        void revokeApiKey_success() {
            UUID keyId = UUID.randomUUID();
            PartnerApiKey key = new PartnerApiKey();
            key.setId(keyId);
            key.setOrganization(org);
            key.setActive(true);

            when(apiKeyRepo.findById(keyId)).thenReturn(Optional.of(key));

            service.revokeApiKey(orgId, keyId);
            verify(apiKeyRepo).save(argThat(k -> !k.isActive()));
        }

        @Test
        @DisplayName("revokeApiKey throws when key belongs to different org")
        void revokeApiKey_wrongOrg_throws() {
            UUID keyId = UUID.randomUUID();
            PartnerApiKey key = new PartnerApiKey();
            key.setId(keyId);
            PartnerOrganization otherOrg = new PartnerOrganization();
            otherOrg.setId(UUID.randomUUID());
            key.setOrganization(otherOrg);

            when(apiKeyRepo.findById(keyId)).thenReturn(Optional.of(key));

            assertThatThrownBy(() -> service.revokeApiKey(orgId, keyId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Admin Operations")
    class AdminOperations {

        @Test
        @DisplayName("listAll returns all organisations")
        void listAll_success() {
            when(orgRepo.findAll()).thenReturn(List.of(org));
            assertThat(service.listAll()).hasSize(1);
        }

        @Test
        @DisplayName("getOrg returns org when found")
        void getOrg_found() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
            assertThat(service.getOrg(orgId).getName()).isEqualTo("Acme Fintech");
        }

        @Test
        @DisplayName("approveProduction sets PRODUCTION status")
        void approveProduction_success() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));

            service.approveProduction(orgId, "admin@cba.com");
            verify(orgRepo).save(argThat(o ->
                o.getStatus() == PartnerStatus.PRODUCTION &&
                o.getEnvironment() == PartnerEnvironment.PRODUCTION
            ));
        }

        @Test
        @DisplayName("rejectApplication sets REJECTED application status")
        void rejectApplication_success() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));

            service.rejectApplication(orgId);
            verify(orgRepo).save(argThat(o -> "REJECTED".equals(o.getApplicationStatus())));
        }

        @Test
        @DisplayName("submitApplication creates application and sets org to PENDING_REVIEW")
        void submitApplication_success() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));

            PartnerApplicationRequest req = new PartnerApplicationRequest(
                "PAYMENTS", "Accept payments online", "50000",
                "https://acme.io", "tech@acme.io", "GDPR compliant"
            );
            service.submitApplication(orgId, req);
            verify(applicationRepo).save(any(PartnerApplication.class));
            verify(orgRepo).save(argThat(o -> o.getStatus() == PartnerStatus.PENDING_REVIEW));
        }
    }
}
