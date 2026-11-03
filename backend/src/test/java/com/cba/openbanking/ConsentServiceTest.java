package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.config.CustomerAccess;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.openbanking.card.CardServiceClient;
import com.cba.openbanking.dto.*;
import com.cba.partner.PartnerPrincipal;
import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.Payment;
import com.cba.payment.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    @Mock CustomerAccess customerAccess;
    @Mock PaymentRepository paymentRepository;

    @InjectMocks ConsentService consentService;

    private UUID customerId;
    private String consentId;
    private Customer customer;
    private OpenBankingConsent consent;

    static final UUID ORG = UUID.fromString("50000000-0000-0000-0000-0000000000b1");
    static final UUID OTHER_ORG = UUID.fromString("50000000-0000-0000-0000-0000000000b2");

    /** A TPP as {@code PartnerApiKeyAuthFilter} authenticates it: principal = orgId, no JWT. */
    static void asTpp(UUID org) {
        var auth = new UsernamePasswordAuthenticationToken(org.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        auth.setDetails(new PartnerPrincipal(org.toString(), "DEVELOPER", "SANDBOX", "BASIC", List.of()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Account accountOf(UUID owner, String balance) {
        Customer c = new Customer();
        c.setId(owner);
        Account a = new Account();
        a.setCustomer(c);
        a.setBalance(new BigDecimal(balance));
        return a;
    }

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        consentId = "ob-test12345678901234";

        customer = new Customer();
        customer.setId(customerId);

        consent = new OpenBankingConsent();
        consent.setConsentId(consentId);
        consent.setCustomer(customer);
        consent.setTppClientId(ORG.toString());
        consent.setScopes(List.of("accounts_read", "transactions_read"));
        consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
    }

    @Nested
    @DisplayName("Consent Lifecycle")
    class ConsentLifecycle {

        @Test
        @DisplayName("createConsent saves a consent owned by the calling org (tppClientId omitted)")
        void createConsent_success() {
            asTpp(ORG);
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
            when(consentRepository.save(any())).thenAnswer(inv -> {
                OpenBankingConsent c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            ConsentRequest req = new ConsentRequest(null, customerId,
                List.of("accounts_read", "accounts_read", "balances_read"), Instant.now().plusSeconds(3600));
            ConsentResponse result = consentService.createConsent(req);
            assertThat(result.status()).isEqualTo(ConsentStatus.AWAITING_AUTHORISATION);
            assertThat(result.tppClientId()).isEqualTo(ORG.toString());
            assertThat(result.scopes()).containsExactly("accounts_read", "balances_read");
        }

        @Test
        @DisplayName("createConsent in another org's name: 400 TPP_CLIENT_MISMATCH, nothing saved")
        void createConsent_otherOrg_rejected() {
            asTpp(ORG);
            ConsentRequest req = new ConsentRequest(OTHER_ORG.toString(), customerId, List.of("accounts_read"), null);
            assertThatThrownBy(() -> consentService.createConsent(req))
                .hasFieldOrPropertyWithValue("errorCode", "TPP_CLIENT_MISMATCH");
            verify(consentRepository, never()).save(any());
        }

        @Test
        @DisplayName("createConsent with a scope outside the catalogue: 400 INVALID_SCOPE")
        void createConsent_unknownScope_rejected() {
            asTpp(ORG);
            ConsentRequest req = new ConsentRequest(null, customerId, List.of("accounts"), null);
            assertThatThrownBy(() -> consentService.createConsent(req))
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SCOPE")
                .hasMessageContaining("accounts_read");
            verify(consentRepository, never()).save(any());
        }

        @Test
        @DisplayName("createConsent without a partner org: 403 TPP_UNIDENTIFIED")
        void createConsent_noOrg_forbidden() {
            ConsentRequest req = new ConsentRequest(null, customerId, List.of("accounts_read"), null);
            assertThatThrownBy(() -> consentService.createConsent(req))
                .hasFieldOrPropertyWithValue("errorCode", "TPP_UNIDENTIFIED");
        }

        @Test
        @DisplayName("createConsent throws when customer not found")
        void createConsent_customerNotFound_throws() {
            asTpp(ORG);
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            ConsentRequest req = new ConsentRequest(ORG.toString(), customerId, List.of("accounts_read"), null);
            assertThatThrownBy(() -> consentService.createConsent(req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("getConsent returns the consent to its TPP")
        void getConsent_found() {
            asTpp(ORG);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            ConsentResponse result = consentService.getConsent(consentId);
            assertThat(result.consentId()).isEqualTo(consentId);
        }

        @Test
        @DisplayName("getConsent returns the consent to its customer")
        void getConsent_byCustomer() {
            when(customerAccess.callerCustomerId()).thenReturn(Optional.of(customerId));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            assertThat(consentService.getConsent(consentId).consentId()).isEqualTo(consentId);
        }

        @Test
        @DisplayName("getConsent by another org: 404")
        void getConsent_otherOrg_notFound() {
            asTpp(OTHER_ORG);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            assertThatThrownBy(() -> consentService.getConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
        }

        @Test
        @DisplayName("getConsent throws when not found")
        void getConsent_notFound_throws() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> consentService.getConsent(consentId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("authoriseConsent by the consent's customer: AWAITING → AUTHORISED")
        void authoriseConsent_success() {
            when(customerAccess.callerCustomerId()).thenReturn(Optional.of(customerId));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsentResponse result = consentService.authoriseConsent(consentId);
            assertThat(result.status()).isEqualTo(ConsentStatus.AUTHORISED);
        }

        @Test
        @DisplayName("authoriseConsent by another customer: 404, status unchanged")
        void authoriseConsent_otherCustomer_notFound() {
            when(customerAccess.callerCustomerId()).thenReturn(Optional.of(UUID.randomUUID()));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.authoriseConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
            assertThat(consent.getStatus()).isEqualTo(ConsentStatus.AWAITING_AUTHORISATION);
            verify(consentRepository, never()).save(any());
        }

        @Test
        @DisplayName("authoriseConsent throws when not in AWAITING_AUTHORISATION state")
        void authoriseConsent_wrongState_throws() {
            when(customerAccess.callerCustomerId()).thenReturn(Optional.of(customerId));
            consent.setStatus(ConsentStatus.AUTHORISED);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.authoriseConsent(consentId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("cannot be authorised");
        }

        @Test
        @DisplayName("revokeConsent by its TPP sets REVOKED status")
        void revokeConsent_success() {
            asTpp(ORG);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(consentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsentResponse result = consentService.revokeConsent(consentId);
            assertThat(result.status()).isEqualTo(ConsentStatus.REVOKED);
        }

        @Test
        @DisplayName("revokeConsent by another org: 404, not revoked")
        void revokeConsent_otherOrg_notFound() {
            asTpp(OTHER_ORG);
            consent.setStatus(ConsentStatus.AUTHORISED);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.revokeConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
            assertThat(consent.getStatus()).isEqualTo(ConsentStatus.AUTHORISED);
        }

        @Test
        @DisplayName("revokeConsent throws when already revoked")
        void revokeConsent_alreadyRevoked_throws() {
            asTpp(ORG);
            consent.setStatus(ConsentStatus.REVOKED);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.revokeConsent(consentId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already revoked");
        }
    }

    @Nested
    @DisplayName("requireUsableConsent / PISP")
    class UsableConsentChecks {

        @BeforeEach
        void authorised() {
            asTpp(ORG);
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("accounts_read", "payments"));
        }

        @Test
        @DisplayName("own AUTHORISED consent with the scope: returns its customer and scopes")
        void usable() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            UsableConsent u = consentService.requireUsableConsent(consentId, ConsentScope.PAYMENTS);
            assertThat(u.customerId()).isEqualTo(customerId);
            assertThat(u.has(ConsentScope.ACCOUNTS_READ)).isTrue();
            assertThat(u.has(ConsentScope.BALANCES_READ)).isFalse();
        }

        @Test
        @DisplayName("another org's consent: 404 CONSENT_NOT_FOUND")
        void otherOrg() {
            asTpp(OTHER_ORG);
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
        }

        @Test
        @DisplayName("not AUTHORISED / revoked / expired status / past expiry: 403 with its own code")
        void unusableStatuses() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_AUTHORISED");
            consent.setStatus(ConsentStatus.REVOKED);
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_REVOKED");
            consent.setStatus(ConsentStatus.EXPIRED);
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_EXPIRED");
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setExpiryDate(Instant.now().minusSeconds(1));
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_EXPIRED");
        }

        @Test
        @DisplayName("none of the asked scopes: 403 CONSENT_SCOPE_MISSING")
        void missingScope() {
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId,
                    ConsentScope.BALANCES_READ, ConsentScope.CARD_BALANCES_READ))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_SCOPE_MISSING");
        }

        @Test
        @DisplayName("a caller without a partner org: 403 TPP_UNIDENTIFIED")
        void noOrg() {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> consentService.requireUsableConsent(consentId))
                .hasFieldOrPropertyWithValue("errorCode", "TPP_UNIDENTIFIED");
            verifyNoInteractions(consentRepository);
        }

        @Test
        @DisplayName("payment from the consent customer's account: allowed")
        void paymentFromOwnAccount() {
            UUID source = UUID.randomUUID();
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(source)).thenReturn(Optional.of(accountOf(customerId, "10")));

            assertThat(consentService.authorisePaymentInitiation(consentId, source).consentId()).isEqualTo(consentId);
        }

        @Test
        @DisplayName("payment from someone else's account: 404 ACCOUNT_NOT_FOUND")
        void paymentFromForeignAccount() {
            UUID source = UUID.randomUUID();
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(source)).thenReturn(Optional.of(accountOf(UUID.randomUUID(), "10")));

            assertThatThrownBy(() -> consentService.authorisePaymentInitiation(consentId, source))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
        }

        @Test
        @DisplayName("payment under a consent without 'payments': 403 CONSENT_SCOPE_MISSING")
        void paymentWithoutScope() {
            consent.setScopes(List.of("accounts_read"));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThatThrownBy(() -> consentService.authorisePaymentInitiation(consentId, UUID.randomUUID()))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_SCOPE_MISSING");
            verifyNoInteractions(accountRepository);
        }

        @Test
        @DisplayName("requireOwnPayment: a PISP payment under the caller's consent → its consent id")
        void ownPayment() {
            UUID paymentId = UUID.randomUUID();
            Payment p = new Payment();
            p.setCreatedBy("open-banking:" + consentId);
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(p));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));

            assertThat(consentService.requireOwnPayment(paymentId)).isEqualTo(consentId);
        }

        @Test
        @DisplayName("requireOwnPayment: another org's PISP payment or a teller payment → 404")
        void foreignPayment() {
            UUID theirs = UUID.randomUUID();
            Payment p = new Payment();
            p.setCreatedBy("open-banking:" + consentId);
            when(paymentRepository.findById(theirs)).thenReturn(Optional.of(p));
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            asTpp(OTHER_ORG);
            assertThatThrownBy(() -> consentService.requireOwnPayment(theirs))
                .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_NOT_FOUND");

            UUID teller = UUID.randomUUID();
            Payment t = new Payment();
            t.setCreatedBy("teller@cba.com");
            when(paymentRepository.findById(teller)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> consentService.requireOwnPayment(teller))
                .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Funds Confirmation (CBPII)")
    class FundsConfirmation {

        @Test
        @DisplayName("confirmFunds returns true when account balance sufficient")
        void confirmFunds_accountSufficientBalance() {
            asTpp(ORG);
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("fundsconfirmation"));

            UUID accountId = UUID.randomUUID();
            Account account = accountOf(customerId, "10000.00");

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
            asTpp(ORG);
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("fundsconfirmation"));

            UUID accountId = UUID.randomUUID();
            Account account = accountOf(customerId, "100.00");

            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, accountId, new BigDecimal("500.00"), "USD");
            FundsConfirmationResponse result = consentService.confirmFunds(req);
            assertThat(result.fundsAvailable()).isFalse();
        }

        @Test
        @DisplayName("confirmFunds on an account of another customer: 404, no balance revealed")
        void confirmFunds_foreignAccount_notFound() {
            asTpp(ORG);
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("fundsconfirmation"));
            UUID accountId = UUID.randomUUID();
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(accountOf(UUID.randomUUID(), "1")));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, accountId, new BigDecimal("500.00"), "USD");
            assertThatThrownBy(() -> consentService.confirmFunds(req))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
            verifyNoInteractions(webhookDelivery);
        }

        @Test
        @DisplayName("confirmFunds on another customer's card: 404, card balance never fetched")
        void confirmFunds_foreignCard_notFound() {
            asTpp(ORG);
            consent.setStatus(ConsentStatus.AUTHORISED);
            consent.setScopes(List.of("fundsconfirmation", "card_balances_read"));
            UUID cardId = UUID.randomUUID();
            when(consentRepository.findByConsentId(consentId)).thenReturn(Optional.of(consent));
            when(accountRepository.findById(cardId)).thenReturn(Optional.empty());
            when(cardServiceClient.getCard(cardId)).thenReturn(Optional.of(new CardServiceClient.CardDto(
                cardId, "DEBIT", "ACTIVE", "411111", "1111", "2812", UUID.randomUUID(), null, "Debit")));

            FundsConfirmationRequest req = new FundsConfirmationRequest(
                consentId, cardId, new BigDecimal("5.00"), "USD");
            assertThatThrownBy(() -> consentService.confirmFunds(req))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
            verify(cardServiceClient, never()).getCardBalance(any());
        }

        @Test
        @DisplayName("confirmFunds throws when consent not AUTHORISED")
        void confirmFunds_notAuthorised_throws() {
            asTpp(ORG);
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
            asTpp(ORG);
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
