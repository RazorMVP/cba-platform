package com.cba.card.openbanking.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security {@link org.springframework.security.core.Authentication} token
 * set in the SecurityContext when a request presents a valid API key.
 *
 * <p>Carries {@code ROLE_API_KEY} plus one role per scope granted to the key
 * (e.g. {@code SCOPE_CARD_READ}). Controllers use these for {@code @PreAuthorize}.
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final UUID apiKeyId;
    private final String name;

    public ApiKeyAuthentication(ApiKey key) {
        super(buildAuthorities(key));
        this.apiKeyId = key.getId();
        this.name     = key.getName();
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal()   { return apiKeyId; }

    public String getKeyName() { return name; }

    private static List<SimpleGrantedAuthority> buildAuthorities(ApiKey key) {
        var auths = new java.util.ArrayList<SimpleGrantedAuthority>();
        auths.add(new SimpleGrantedAuthority("ROLE_API_KEY"));
        for (String scope : key.getScopes()) {
            auths.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        return auths;
    }
}
