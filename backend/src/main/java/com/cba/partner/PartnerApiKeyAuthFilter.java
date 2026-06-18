package com.cba.partner;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates partner machine-to-machine requests presented as
 * {@code Authorization: ApiKey cba_xxx}. The raw key is SHA-256 hashed and looked up
 * against {@code partner_api_keys}; a match installs a {@link SecurityContextHolder}
 * authentication scoped to the key's organization.
 *
 * <p>Authorities granted:
 * <ul>
 *   <li>{@code ROLE_PARTNER_DEVELOPER} — reach the partner self-service API ({@code /api/v1/partners/**})</li>
 *   <li>{@code ROLE_API_CLIENT} — reach the Open Banking data plane ({@code /open-banking/**})</li>
 *   <li>{@code SCOPE_<scope>} — one per granted scope, for fine-grained checks</li>
 * </ul>
 * The owning {@code orgId} is placed on the authentication details via {@link PartnerPrincipal}
 * so {@link PartnerSecurity} enforces org ownership identically to JWT callers.
 */
@Component
@RequiredArgsConstructor
public class PartnerApiKeyAuthFilter extends OncePerRequestFilter {

    private final PartnerApiKeyRepository apiKeyRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("ApiKey ")) {
            chain.doFilter(request, response);
            return;
        }

        String rawKey = header.substring("ApiKey ".length()).trim();
        if (!rawKey.isEmpty()) {
            try {
                Optional<PartnerApiKey> match = apiKeyRepo.findByKeyHashAndActiveTrue(PartnerApiKeys.hash(rawKey));
                if (match.isPresent()) {
                    authenticate(match.get());
                }
            } catch (Exception ignored) {
                // malformed key — leave unauthenticated; endpoint authorization will reject
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticate(PartnerApiKey key) {
        PartnerOrganization org = key.getOrganization();
        String orgId = org.getId().toString();

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_PARTNER_DEVELOPER"));
        authorities.add(new SimpleGrantedAuthority("ROLE_API_CLIENT"));
        if (key.getScopes() != null) {
            key.getScopes().forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
        }

        var auth = new UsernamePasswordAuthenticationToken(orgId, null, authorities);
        auth.setDetails(new PartnerPrincipal(
                orgId, "DEVELOPER", org.getEnvironment().name(), key.getTier(), key.getScopes()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        key.setLastUsedAt(Instant.now());
        apiKeyRepo.save(key);
    }
}
