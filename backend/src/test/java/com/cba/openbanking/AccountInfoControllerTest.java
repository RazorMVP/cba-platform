package com.cba.openbanking;

import com.cba.account.AccountRepository;
import com.cba.account.TransactionRepository;
import com.cba.common.exception.CbaException;
import com.cba.config.CustomerAccess;
import com.cba.openbanking.card.CardServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountInfoController — AISP resolves the customer from the token")
class AccountInfoControllerTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock CardServiceClient cardServiceClient;
    @Mock CustomerAccess customerAccess;
    @InjectMocks AccountInfoController controller;

    static final String KEYCLOAK_SUB = "8f6c1a52-0000-0000-0000-00000000000a";
    static final UUID CUSTOMER_ID = UUID.fromString("30000000-0000-0000-0000-0000000000a1");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Jwt login(String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(KEYCLOAK_SUB).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return jwt;
    }

    @Test
    @DisplayName("CUSTOMER: sub → customers.keycloak_id → that customer's accounts and cards")
    void customer_resolvedByKeycloakId() {
        Jwt jwt = login("CUSTOMER");
        when(customerAccess.callerCustomerId()).thenReturn(Optional.of(CUSTOMER_ID));
        when(accountRepository.findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class))).thenReturn(Page.empty());

        controller.getAccounts(null, jwt);

        verify(accountRepository).findByCustomerId(eq(CUSTOMER_ID), any(Pageable.class));
        verify(cardServiceClient).getCardsForCustomer(CUSTOMER_ID);
    }

    @Test
    @DisplayName("CUSTOMER with no customer record: 404, nothing looked up")
    void customer_unknown_notFound() {
        Jwt jwt = login("CUSTOMER");
        when(customerAccess.callerCustomerId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getAccounts(null, jwt))
                .isInstanceOf(CbaException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CUSTOMER_NOT_FOUND");
        verifyNoInteractions(accountRepository, cardServiceClient);
    }

    @Test
    @DisplayName("API_CLIENT (TPP): unchanged — sub taken as the customer id until consent resolution exists")
    void apiClient_unchanged() {
        Jwt jwt = login("API_CLIENT");
        UUID sub = UUID.fromString(KEYCLOAK_SUB);
        when(accountRepository.findByCustomerId(eq(sub), any(Pageable.class))).thenReturn(Page.empty());

        controller.getAccounts(null, jwt);

        verify(cardServiceClient).getCardsForCustomer(sub);
        verifyNoInteractions(customerAccess);
    }
}
