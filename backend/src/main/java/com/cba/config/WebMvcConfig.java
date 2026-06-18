package com.cba.config;

import com.cba.partner.PartnerUsageInterceptor;
import com.cba.tenant.TenantInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final PartnerUsageInterceptor partnerUsageInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor())
            .addPathPatterns("/api/**", "/open-banking/**")
            .excludePathPatterns("/actuator/**", "/api-docs/**", "/swagger-ui/**");

        // Partner usage metering — records per-request counts for partner (JWT/API-key) traffic only
        registry.addInterceptor(partnerUsageInterceptor)
            .addPathPatterns("/api/**", "/open-banking/**")
            .excludePathPatterns("/actuator/**", "/api-docs/**", "/swagger-ui/**",
                "/api/v1/partners/register", "/api/v1/partners/auth/login");
    }
}
