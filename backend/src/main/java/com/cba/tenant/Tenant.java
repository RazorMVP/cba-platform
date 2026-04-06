package com.cba.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** ISO 4217 currency code — the home/base currency for this deployment */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** ISO 3166-1 alpha-2 country code */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** BCP-47 locale (e.g. en-US, sw-KE, en-GH) for number/date formatting */
    @Column(name = "locale_code", length = 10)
    private String localeCode;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
