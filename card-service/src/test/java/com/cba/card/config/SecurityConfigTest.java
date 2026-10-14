package com.cba.card.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfig — Keycloak realm roles")
class SecurityConfigTest {

    @Test
    @DisplayName("realm_access.roles (nested, as Keycloak issues it) become ROLE_ authorities")
    void realmRoles_nestedClaim() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("TELLER", "offline_access"))));

        assertThat(SecurityConfig.realmRoles(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TELLER", "ROLE_offline_access");
    }

    @Test
    @DisplayName("no realm_access claim, or no roles in it: no authorities")
    void realmRoles_missing() {
        assertThat(SecurityConfig.realmRoles(jwt(Map.of("scope", "openid")))).isEmpty();
        assertThat(SecurityConfig.realmRoles(jwt(Map.of("realm_access", Map.of())))).isEmpty();
    }

    @Test
    @DisplayName("the converter bean maps realm roles and keeps sub as the principal name")
    void converterBean() {
        var converter = new SecurityConfig().jwtAuthenticationConverter();
        var auth = converter.convert(jwt(Map.of("realm_access", Map.of("roles", List.of("ADMIN")))));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(auth.getName()).isEqualTo("kc-user");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token").header("alg", "none").subject("kc-user");
        claims.forEach(b::claim);
        return b.build();
    }
}
