package com.cba.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.of("system");
            }
            if (auth.getPrincipal() instanceof Jwt jwt) {
                // Prefer sub claim; fall back to preferred_username
                String sub = jwt.getClaimAsString("preferred_username");
                return Optional.ofNullable(sub != null ? sub : jwt.getSubject());
            }
            return Optional.of(auth.getName());
        };
    }
}
