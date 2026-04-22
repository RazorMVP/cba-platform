package com.cba.partner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PartnerConfig {

    @Bean
    public BCryptPasswordEncoder partnerPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
