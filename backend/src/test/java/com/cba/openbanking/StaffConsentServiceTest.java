package com.cba.openbanking;

import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.openbanking.dto.StaffConsentResponse;
import com.cba.partner.PartnerOrganization;
import com.cba.partner.PartnerOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffConsentService — staff list, view and revoke consents (backlog 23)")
class StaffConsentServiceTest {

    @Mock ConsentRepository consentRepository;
    @Mock ConsentService consentService;
    @Mock CustomerRepository customerRepository;
    @Mock PartnerOrganizationRepository partnerOrganizationRepository;
    @InjectMocks StaffConsentService service;

    static final UUID ORG = ConsentServiceTest.ORG;
    UUID customerId = UUID.randomUUID();
    OpenBankingConsent consent;

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setId(customerId);
        consent = new OpenBankingConsent();
        consent.setConsentId("ob-staff000000000001");
        consent.setCustomer(c);
        consent.setTppClientId(ORG.toString());
        consent.setScopes(List.of("accounts_read"));
        consent.setStatus(ConsentStatus.AUTHORISED);
    }

    private void names() {
        Customer named = new Customer();
        named.setId(customerId);
        named.setFirstName("Ada");
        named.setLastName("Obi");
        when(customerRepository.findAllById(List.of(customerId))).thenReturn(List.of(named));
        PartnerOrganization org = new PartnerOrganization();
        org.setId(ORG);
        org.setName("Acme Fintech");
        when(partnerOrganizationRepository.findAllById(List.of(ORG))).thenReturn(List.of(org));
    }

    @Test
    @DisplayName("consent type from scopes: payments → PISP, fundsconfirmation → CBPII, else AISP")
    void types() {
        assertThat(StaffConsentService.typeOf(List.of("payments"))).isEqualTo("PISP");
        assertThat(StaffConsentService.typeOf(List.of("fundsconfirmation", "card_read"))).isEqualTo("CBPII");
        assertThat(StaffConsentService.typeOf(List.of("accounts_read", "balances_read"))).isEqualTo("AISP");
        assertThat(StaffConsentService.typeOf(null)).isEqualTo("AISP");
    }

    @Test
    @DisplayName("get: the customer's and the TPP's names are filled in")
    void get_withNames() {
        when(consentRepository.findByConsentId(consent.getConsentId())).thenReturn(Optional.of(consent));
        names();

        StaffConsentResponse r = service.get(consent.getConsentId());

        assertThat(r.customerName()).isEqualTo("Ada Obi");
        assertThat(r.tppName()).isEqualTo("Acme Fintech");
        assertThat(r.consentType()).isEqualTo("AISP");
        assertThat(r.status()).isEqualTo(ConsentStatus.AUTHORISED);
    }

    @Test
    @DisplayName("get of an unknown consent: 404 CONSENT_NOT_FOUND")
    void get_unknown() {
        when(consentRepository.findByConsentId("ob-nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("ob-nope"))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
    }

    @Test
    @DisplayName("revoke: through ConsentService.revoke with the trimmed reason")
    void revoke() {
        when(consentRepository.findByConsentId(consent.getConsentId())).thenReturn(Optional.of(consent));
        when(consentService.revoke(consent, "customer asked")).thenAnswer(inv -> {
            consent.setStatus(ConsentStatus.REVOKED);
            return consent;
        });
        names();

        StaffConsentResponse r = service.revoke(consent.getConsentId(), "  customer asked ");

        assertThat(r.status()).isEqualTo(ConsentStatus.REVOKED);
        verify(consentService).revoke(consent, "customer asked");
    }

    @Test
    @DisplayName("revoke without a reason: 400 REASON_REQUIRED, nothing revoked")
    void revoke_noReason() {
        assertThatThrownBy(() -> service.revoke(consent.getConsentId(), " "))
                .hasFieldOrPropertyWithValue("errorCode", "REASON_REQUIRED");
        verify(consentService, never()).revoke(any(), any());
    }
}
