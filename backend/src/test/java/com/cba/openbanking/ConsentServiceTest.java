package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.openbanking.card.CardServiceClient;
import com.cba.openbanking.dto.*;
import com.cba.partner.PartnerWebhookDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsentService — unit tests")
class ConsentServiceTest {

    @Mock ConsentRepository consentRepository;
    @Mock CustomerRepository customerRepository;
    @Mock AccountRepository accountRepository;
    @Mock AuditLogService auditLogService;
    @Mock CardServiceClient cardServiceClient;
    @Mock PartnerWebhookDeliveryService webhookDelivery;

    @InjectMocks ConsentService consentService;

    private UUID customerId;
    private String consentId;
    private Customer customer;
    private OpenBankingConsent consent;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        consentId = "ob-test12345678901234";

        customer = new Customer();
        customer.setId(customerId);

        consent = new OpenBankingConsent();
        consent.setConsentId(consentId);
        consent.setCustomer(customer);
        consent.setTppClientId("tpp-client-001");
        consent.setScopes(List.of("accounts", "transactions"));
        consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
    }

    @Nested
    @DisplayName("Consent Lifecycle")
    class ConsentLifecycle {

        @Test
        @DisplayName("createConsent saves new consent")
        void createConsent_success() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(consentRepository.save(any())).thenAnswer(inv -> {
                OpenBankingConsent c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            ConsentRequest req = new ConsentRequest("tpp-client-001", customerId,
                List.of("accounts"), Instant.now().plusSeconds(3600));
            ConsentResponse result = consentService.createConsent(req);
            assertThat(result.status()).isEqualTo(ConsentStatus.AWAITING_AUTHORISATION);
        }

        @Test
        @DisplayName("createConsent throws when customer not found")
        void createConsent_customerNotFound_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            ConsentRequest req = new ConsentRequest("tpp-001", customerId, List.of("accounts"), null);
            assertThatThrownBy(() -> consentService.createConsent(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("getConsent returns consent response")
        void getConsent_found() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            ConsentResponse result = consentService.getConsent(consentId);
            assertThat(result.consentId()).isEqualTo(consentId);
        }

        @Test
        @DisplayName("getConsent throws when not found")
        void getConsent_notFound_throws() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> consentService.getConsent(consentId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("authoriseConsent transitions AWAITING → AUTHORISED")
        void authoriseConsent_success() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsentResponse result = consentService.authoriseConsent(consentId);
            assertThat(result.status()).isEqualTo(ConsentStatus.AUTHORISED);
        }

        @Test
        @DisplayName("authoriseConsent throws when not in AWAITING_AUTHORISATION state")
        void authoriseConsent_wrongState_throws() {
            consent.setStatus(ConsentStatus.AUTHORISED);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.authoriseConsent(consentId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("cannot be authorised");
        }

        @Test
        @DisplayName("revokeConsent sets REVOKED status")
        void revokeConsent_success() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsentResponse result = consentService.revokeConsent(consentId);
            assertThat(result.status()).isEqualTo(ConsentStatus.REVOKED);
        }

        @Test
        @DisplayName("revokeConsent throws when already revoked")
        void revokeConsent_alreadyRevoked_throws() {
            consent.setStatus(ConsentStatus.REVOKED);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.revokeConsent(consentId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already revoked");
        }
    }

    @Nested
    @DisplayName("PISP Validation")
    class PispValidation {

        @Test
        @DisplayName("validatePispConsent passes when AUTHORISED with payments scope and not expired")
        void validatePispConsent_success() {
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("accounts", "payments"));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatCode(() -> consentService.validatePispConsent(consentId))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validatePispConsent throws when consent not AUTHORISED")
        void validatePispConsent_notAuthorised_throws() {
            consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.validatePispConsent(consentId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("validatePispConsent throws when payments scope missing")
        void validatePispConsent_missingScope_throws() {
            consent.setStatus(ConsentStatus.AUTHORISED);
            // scopes = ["accounts", "transactions"] — no "payments"
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.validatePispConsent(consentId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("payments");
        }
    }

    @Nested
    @DisplayName("Funds Confirmation (CBPII)")
    class FundsConfirmation {

        @Test
        @DisplayName("confirmFunds returns true when account balance sufficient")
        void confirmFunds_accountSufficientBalance() {
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("fundsconfirmation"));

            UUID accountId = UUID.randomUUID();
            Account account = new Account();
            account.setBalance(new BigDecimal("10000.00"));

            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, accountId, new BigDecimal("500.00"), "USD");
            FundsConfirmationResponse result = consentService.confirmFunds(req);
            assertThat(result.fundsAvailable()).isTrue();
        }

        @Test
        @DisplayName("confirmFunds returns false when account balance insufficient")
        void confirmFunds_accountInsufficientBalance() {
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("fundsconfirmation"));

            UUID accountId = UUID.randomUUID();
            Account account = new Account();
            account.setBalance(new BigDecimal("100.00"));

            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, accountId, new BigDecimal("500.00"), "USD");
            FundsConfirmationResponse result = consentService.confirmFunds(req);
            assertThat(result.fundsAvailable()).isFalse();
        }

        @Test
        @DisplayName("confirmFunds throws when consent not AUTHORISED")
        void confirmFunds_notAuthorised_throws() {
            consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, UUID.randomUUID(), new BigDecimal("100.00"), "USD");
            assertThatThrownBy(() -> consentService.confirmFunds(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("confirmFunds throws when fundsconfirmation scope missing")
        void confirmFunds_missingScope_throws() {
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("accounts")); // no fundsconfirmation scope
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, UUID.randomUUID(), new BigDecimal("100.00"), "USD");
            assertThatThrownBy(() -> consentService.confirmFunds(req))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("expireDueConsents")
    class ExpireDueConsents {

        private OpenBankingConsent dueConsent(UUID partnerOrg, String cid) {
            OpenBankingConsent c = new OpenBankingConsent();
            c.setConsentId(cid);
            c.setTppClientId(partnerOrg.toString());   // UUID → resolves to a partner org
            c.setStatus(ConsentStatus.AUTHORISED);
            c.setExpiryDate(Instant.now().minusSeconds(3600));
            return c;
        }

        @Test
        @DisplayName("transitions due consents to EXPIRED and fires CONSENT.EXPIRED per consent")
        void expiresAndPublishes() {
            UUID org = UUID.randomUUID();
            OpenBankingConsent c1 = dueConsent(org, "ob-exp-000000000001");
            OpenBankingConsent c2 = dueConsent(org, "ob-exp-000000000002");
            Instant now = Instant.now();
            when(consentRepository.findByStatusInAndExpiryDateBefore(anyCollection(), eq(now)))
                .thenReturn(List.of(c1, c2));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int expired = consentService.expireDueConsents(now);

            assertThat(expired).isEqualTo(2);
            assertThat(c1.getStatus()).isEqualTo(ConsentStatus.EXPIRED);
            assertThat(c2.getStatus()).isEqualTo(ConsentStatus.EXPIRED);
            verify(webhookDelivery, times(2)).publishEvent(eq(org), eq("CONSENT.EXPIRED"), any());
            verify(auditLogService, times(2)).log(eq("OpenBankingConsent"), any(), eq("EXPIRE"), any(), any());
        }

        @Test
        @DisplayName("no due consents → returns 0 and publishes nothing")
        void noneDue_returnsZero() {
            Instant now = Instant.now();
            when(consentRepository.findByStatusInAndExpiryDateBefore(anyCollection(), eq(now)))
                .thenReturn(List.of());

            assertThat(consentService.expireDueConsents(now)).isZero();
            verifyNoInteractions(webhookDelivery);
        }
    }
}
