package com.cba.fep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * FEP Spring configuration.
 *
 * <p>Provides shared infrastructure beans used across the FEP:
 * <ul>
 *   <li>{@link RestTemplate} — synchronous HTTP client for card-service calls</li>
 * </ul>
 *
 * <p>{@link EnableScheduling} activates the BIN cache refresh scheduler
 * defined in {@link com.cba.fep.scheme.SchemeAdapterFactory}.
 * The refresh runs every 5 minutes to pick up new BIN registrations
 * without requiring a restart.
 */
@Configuration
@EnableScheduling
public class FepConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
