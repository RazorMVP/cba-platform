package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.account.TransactionRepository;
import com.cba.common.exception.CbaException;
import com.cba.config.CustomerAccess;
import com.cba.customer.Customer;
import com.cba.openbanking.card.CardServiceClient;
import com.cba.partner.PartnerPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountInfoController — AISP reads the customer's own data, or a TPP's consent")
class AccountInfoControllerTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock CardServiceClient cardServiceClient;
    @Mock CustomerAccess customerAccess;
    @Mock ConsentService consentService;
    @InjectMocks AccountInfoController controller;

    static final String KEYCLOAK_SUB = "8f6c1a52-0000-0000-0000-00000000000a";
    static final UUID CUSTOMER_ID = UUID.fromString("30000000-0000-0000-0000-0000000000a1");
    static final UUID ORG = UUID.fromString("50000000-0000-0000-0000-0000000000b1");
    static final String CONSENT = "ob-consent0000000001";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginCustomer() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(KEYCLOAK_SUB).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    /** What {@code PartnerApiKeyAuthFilter} sets: principal = orgId string, no JWT. */
    private void loginTpp() {
        var auth = new UsernamePasswordAuthenticationToken(ORG.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_PARTNER_DEVELOPER"),
                        new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        auth.setDetails(new PartnerPrincipal(ORG.toString(), "DEVELOPER", "SANDBOX", "BASIC", List.of()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void consentWith(String... scopes) {
        when(consentService.requireUsableConsent(eq(CONSENT), any(ConsentScope[].class)))
                .thenReturn(new UsableConsent(CONSENT, ORG.toString(), CUSTOMER_ID, Set.of(scopes)));
    }

    private Account account(UUID owner) {
        Customer c = new Customer();
        c.setId(owner);
        Account a = new Account();
        a.setCustomer(c);
        a.setBalance(new BigDecimal("12.00"));
        a.setCurrencyCode("USD");
        return a;
    }

    @Test
    @DisplayName("CUSTOMER: sub → customers.keycloak_id → that customer's accounts and cards, no consent")
    void customer_resolvedByKeycloakId() {
        loginCustomer();
        when(customerAccess.callerCustomerId()).thenReturn(Optional.of(CUSTOMER_ID));
        when(accountRepository.findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class))).thenReturn(Page.empty());

        controller.getAccounts(null, null);

        verify(accountRepository).findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class));
        verify(cardServiceClient).getCardsForCustomer(CUSTOMER_ID);
        verifyNoInteractions(consentService);
    }

    @Test
    @DisplayName("CUSTOMER with no customer record: 404, nothing looked up")
    void customer_unknown_notFound() {
        loginCustomer();
        when(customerAccess.callerCustomerId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getAccounts(null, null))
                .isInstanceOf(CbaException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CUSTOMER_NOT_FOUND");
        verifyNoInteractions(accountRepository, cardServiceClient);
    }

    @Test
    @DisplayName("TPP with an API key (no JWT): the consent's customer — this was a NullPointerException (500)")
    void tpp_readsConsentCustomer() {
        loginTpp();
        consentWith("accounts_read", "card_read");
        when(accountRepository.findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class))).thenReturn(Page.empty());

        controller.getAccounts(null, CONSENT);

        verify(consentService).requireUsableConsent(CONSENT, ConsentScope.ACCOUNTS_READ, ConsentScope.CARD_READ);
        verify(accountRepository).findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class));
        verify(cardServiceClient).getCardsForCustomer(CUSTOMER_ID);
        verifyNoInteractions(customerAccess);
    }

    @Test
    @DisplayName("TPP without x-consent-id: 400 CONSENT_ID_REQUIRED, nothing read")
    void tpp_noConsentHeader_badRequest() {
        loginTpp();

        assertThatThrownBy(() -> controller.getAccounts(null, " "))
                .isInstanceOf(CbaException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_ID_REQUIRED");
        verifyNoInteractions(consentService, accountRepository, cardServiceClient);
    }

    @Test
    @DisplayName("TPP whose consent check fails: the refusal stands, nothing read")
    void tpp_consentRefused_nothingRead() {
        loginTpp();
        when(consentService.requireUsableConsent(eq(CONSENT), any(ConsentScope[].class)))
                .thenThrow(CbaException.notFound("Consent", CONSENT));

        assertThatThrownBy(() -> controller.getAccounts(null, CONSENT))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
        verifyNoInteractions(accountRepository, cardServiceClient);
    }

    @Test
    @DisplayName("TPP with accounts_read only: bank accounts, card-service not asked")
    void tpp_bankScopeOnly_noCards() {
        loginTpp();
        consentWith("accounts_read");
        when(accountRepository.findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class))).thenReturn(Page.empty());

        controller.getAccounts(null, CONSENT);

        verify(accountRepository).findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class));
        verifyNoInteractions(cardServiceClient);
    }

    @Test
    @DisplayName("TPP with card_read only: cards, bank accounts not read")
    void tpp_cardScopeOnly_noBankAccounts() {
        loginTpp();
        consentWith("card_read");

        controller.getAccounts(null, CONSENT);

        verify(cardServiceClient).getCardsForCustomer(CUSTOMER_ID);
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("TPP balance of an account that isn't the consent customer's: 404")
    void tpp_foreignAccountBalance_notFound() {
        loginTpp();
        consentWith("balances_read");
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(UUID.randomUUID())));

        assertThatThrownBy(() -> controller.getBalances(accountId, null, CONSENT))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("TPP balance of the customer's account under a card-only consent: 403 CONSENT_SCOPE_MISSING")
    void tpp_bankBalanceWithCardScope_forbidden() {
        loginTpp();
        consentWith("card_balances_read");
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(CUSTOMER_ID)));

        assertThatThrownBy(() -> controller.getBalances(accountId, null, CONSENT))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_SCOPE_MISSING");
    }

    @Test
    @DisplayName("TPP balance of the customer's account with balances_read: returned")
    void tpp_ownBalance_ok() {
        loginTpp();
        consentWith("balances_read");
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account(CUSTOMER_ID)));

        assertThat(controller.getBalances(accountId, null, CONSENT).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("TPP transactions of another customer's card: 404, authorizations not fetched")
    void tpp_foreignCardTransactions_notFound() {
        loginTpp();
        consentWith("card_transactions_read");
        UUID cardId = UUID.randomUUID();
        when(accountRepository.findById(cardId)).thenReturn(Optional.empty());
        when(cardServiceClient.getCard(cardId)).thenReturn(Optional.of(new CardServiceClient.CardDto(
                cardId, "DEBIT", "ACTIVE", "411111", "1111", "2812", UUID.randomUUID(), null, "Debit")));

        assertThatThrownBy(() -> controller.getTransactions(cardId, null, null, null, CONSENT))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
        verify(cardServiceClient, never()).getCardAuthorizations(any());
    }
}
