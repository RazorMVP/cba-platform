package com.cba.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerAccess — the caller owns the resource")
class CustomerAccessTest {

    @Mock JdbcTemplate jdbc;
    @InjectMocks CustomerAccess access;

    final UUID me = UUID.randomUUID();
    final UUID someoneElse = UUID.randomUUID();
    final UUID resource = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("isCustomer: only the customer linked to the caller's JWT subject")
    void isCustomer() {
        signedInAs("kc-me");

        assertThat(access.isCustomer(me)).isTrue();
        assertThat(access.isCustomer(someoneElse)).isFalse();
        assertThat(access.isCustomer(null)).isFalse();
    }

    @Test
    @DisplayName("isAccountOwner: true for my account, false for another's or a missing one")
    void isAccountOwner() {
        signedInAs("kc-me");
        owner("accounts", List.of(me));
        assertThat(access.isAccountOwner(resource)).isTrue();

        owner("accounts", List.of(someoneElse));
        assertThat(access.isAccountOwner(resource)).isFalse();

        owner("accounts", List.of());
        assertThat(access.isAccountOwner(resource)).isFalse();
    }

    @Test
    @DisplayName("isPaymentParty: either side of the payment")
    void isPaymentParty() {
        signedInAs("kc-me");
        when(jdbc.queryForList(contains("FROM payments"), eq(UUID.class), eq(resource))).thenReturn(List.of(someoneElse, me));
        assertThat(access.isPaymentParty(resource)).isTrue();

        when(jdbc.queryForList(contains("FROM payments"), eq(UUID.class), eq(resource))).thenReturn(List.of(someoneElse));
        assertThat(access.isPaymentParty(resource)).isFalse();
    }

    @Test
    @DisplayName("a caller with no linked customer, or no JWT at all, owns nothing")
    void noCustomer() {
        when(jdbc.queryForList(contains("FROM customers"), eq(UUID.class), eq("kc-stranger"))).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(jwtAuth("kc-stranger"));
        assertThat(access.isCustomer(me)).isFalse();
        assertThat(access.isShareAccountOwner(resource)).isFalse();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "dev", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        assertThat(access.isCustomer(me)).isFalse();
        assertThat(access.isFixedDepositOwner(resource)).isFalse();
        verify(jdbc, never()).queryForList(contains("fixed_deposit_accounts"), eq(UUID.class), any());
    }

    private void signedInAs(String subject) {
        lenient().when(jdbc.queryForList(contains("FROM customers"), eq(UUID.class), eq(subject))).thenReturn(List.of(me));
        SecurityContextHolder.getContext().setAuthentication(jwtAuth(subject));
    }

    private void owner(String table, List<UUID> owners) {
        when(jdbc.queryForList(contains("FROM " + table), eq(UUID.class), eq(resource))).thenReturn(owners);
    }

    private static JwtAuthenticationToken jwtAuth(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
