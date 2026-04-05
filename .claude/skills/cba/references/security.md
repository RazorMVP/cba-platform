# CBA Security Reference

## Security Architecture

```
Client (Web/Mobile)
    │  HTTPS + mTLS (optional for FAPI 2.0)
    ▼
Keycloak (OIDC/OAuth 2.0 Authorization Server)
    │  FAPI 2.0 Security Profile
    │  - PAR (Pushed Authorization Requests)
    │  - DPoP (Demonstrating Proof of Possession)
    │  - PKCE enforced
    ▼
Spring Boot Backend (Resource Server)
    │  JWT validation + RBAC
    │  Field-level encryption
    │  Audit logging
    ▼
PostgreSQL (encrypted at rest)
```

## Keycloak Configuration

### Realm: `cba`

```json
{
  "realm": "cba",
  "enabled": true,
  "accessTokenLifespan": 300,
  "ssoSessionMaxLifespan": 36000,
  "bruteForceProtected": true,
  "permanentLockout": false,
  "maxFailureWaitSeconds": 900,
  "minimumQuickLoginWaitSeconds": 60,
  "waitIncrementSeconds": 60,
  "quickLoginCheckMilliSeconds": 1000,
  "maxDeltaTimeSeconds": 43200,
  "failureFactor": 5,
  "clients": [
    {
      "clientId": "cba-backend",
      "protocol": "openid-connect",
      "bearerOnly": true
    },
    {
      "clientId": "cba-web",
      "protocol": "openid-connect",
      "publicClient": false,
      "standardFlowEnabled": true,
      "pkceCodeChallengeMethod": "S256",
      "redirectUris": ["http://localhost:4200/*", "https://banking.cba.com/*"]
    },
    {
      "clientId": "cba-mobile",
      "protocol": "openid-connect",
      "publicClient": true,
      "standardFlowEnabled": true,
      "pkceCodeChallengeMethod": "S256",
      "redirectUris": ["com.cba.mobile://callback"]
    }
  ],
  "roles": {
    "realm": [
      { "name": "ADMIN" },
      { "name": "TELLER" },
      { "name": "CUSTOMER" },
      { "name": "API_CLIENT" }
    ]
  }
}
```

## Spring Boot Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/open-banking/**").hasAnyRole("CUSTOMER", "API_CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("ADMIN", "TELLER", "CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAnyRole("ADMIN", "TELLER")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .headers(headers -> headers
                .frameOptions(FrameOptionsConfig::deny)
                .xssProtection(XssConfig::disable)  // Handled by CSP
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none'")
                )
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("realm_access.roles");
        converter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }
}
```

## Field-Level Encryption (PII)

```java
// common/crypto/FieldEncryptor.java
@Component
public class FieldEncryptor {
    private final StringEncryptor encryptor;

    public FieldEncryptor(@Value("${cba.encryption.secret-key}") String secretKey) {
        PooledPBEStringEncryptor enc = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(secretKey);
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        enc.setConfig(config);
        this.encryptor = enc;
    }

    public String encrypt(String value) { return value == null ? null : encryptor.encrypt(value); }
    public String decrypt(String value) { return value == null ? null : encryptor.decrypt(value); }
}

// Apply via JPA AttributeConverter
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    @Autowired private FieldEncryptor encryptor;
    @Override public String convertToDatabaseColumn(String s) { return encryptor.encrypt(s); }
    @Override public String convertToEntityAttribute(String s) { return encryptor.decrypt(s); }
}

// Usage in entity:
@Convert(converter = EncryptedStringConverter.class)
@Column(name = "first_name_encrypted")
private String firstName;
```

## Audit Logging

```java
// common/audit/AuditableEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}

// common/audit/AuditLogService.java — write to audit_log table
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW) // Audit persists even if main TX rolls back
public class AuditLogService {
    public void log(String entityType, String entityId, String action,
                    Object oldValues, Object newValues) {
        // Write to audit_log — NEVER update or delete audit records
    }
}
```

## FAPI 2.0 Open Banking Endpoints

```java
// openbanking/AccountInfoController.java
@RestController
@RequestMapping("/open-banking/v3.1/aisp")
@SecurityRequirement(name = "oauth2", scopes = {"accounts"})
public class AccountInfoController {

    // GET /open-banking/v3.1/aisp/accounts
    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('SCOPE_accounts')")
    public ResponseEntity<OBReadAccount6> getAccounts(
            @RequestHeader("x-fapi-interaction-id") String interactionId,
            @RequestHeader("x-fapi-auth-date") String authDate,
            JwtAuthenticationToken token) {
        // Return accounts for authenticated customer
    }

    // GET /open-banking/v3.1/aisp/accounts/{accountId}/transactions
    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAuthority('SCOPE_accounts')")
    public ResponseEntity<OBReadTransaction6> getTransactions(
            @PathVariable String accountId,
            @RequestParam @DateTimeFormat(iso = DATE_TIME) OffsetDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DATE_TIME) OffsetDateTime toDate) {
        // Return paginated transactions
    }
}
```

## Rate Limiting

```java
// Apply via filter or use Resilience4j
@Bean
public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
    // 100 requests/minute per authenticated user
    // 10 requests/minute for unauthenticated
}
```

## PCI-DSS Checklist (implemented by this skill)

- [ ] No PAN (Primary Account Numbers) stored unencrypted
- [ ] All PII encrypted at field level (AES-256)
- [ ] Audit trail for every data access and modification
- [ ] Session tokens never logged
- [ ] TLS 1.2+ enforced (configured in infrastructure)
- [ ] Failed login attempts tracked and locked (Keycloak brute force)
- [ ] Sensitive data masked in API responses (e.g., `****1234` for account numbers)
- [ ] Database credentials in environment variables, never in code
