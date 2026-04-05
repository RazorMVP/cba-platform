# CBA Tech Stack Reference

## Backend — Java 21 + Spring Boot 3

### pom.xml Dependencies

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.2.x</spring-boot.version>
    <keycloak.version>23.x</keycloak.version>
</properties>

<dependencies>
    <!-- Core Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>

    <!-- Security + OIDC -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- Field Encryption -->
    <dependency>
        <groupId>com.github.ulisesbocchio</groupId>
        <artifactId>jasypt-spring-boot-starter</artifactId>
        <version>3.0.5</version>
    </dependency>

    <!-- OpenAPI / Swagger -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.3.x</version>
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.x</version>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### application.yml Structure

```yaml
spring:
  profiles:
    active: dev

---
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/cba_db
    username: cba_user
    password: ${DB_PASSWORD:cba_pass}
  jpa:
    hibernate:
      ddl-auto: validate        # Flyway owns schema, never auto
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/cba

server:
  port: 8080

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    oauth:
      client-id: cba-web
      realm: cba

cba:
  encryption:
    secret-key: ${ENCRYPTION_KEY:changeme-32-char-secret-key!!!!}
  pagination:
    default-page-size: 20
    max-page-size: 100
```

---

## Web Frontend — Angular 17+

### Package Setup

```bash
ng new cba-web --routing --style=scss --standalone
cd cba-web

# UI Components
npm install @angular/material @angular/cdk
npm install primeng primeicons primeflex

# Auth
npm install keycloak-angular keycloak-js

# HTTP + State
npm install @ngrx/store @ngrx/effects @ngrx/entity
npm install axios   # or use Angular HttpClient directly

# Charts (for dashboards)
npm install chart.js ng2-charts

# Forms
npm install @angular/forms   # built-in, ensure imported

# Currency / Number formatting
npm install numeral
```

### Folder Structure

```
cba-web/src/app/
├── core/
│   ├── auth/                # Keycloak service, guards, interceptors
│   ├── http/                # Base HTTP service, error interceptor
│   └── models/              # Shared TypeScript interfaces
├── shared/
│   ├── components/          # Reusable: data-table, confirm-dialog, breadcrumb
│   ├── pipes/               # currency-format, mask-account, date-local
│   └── directives/
├── features/
│   ├── dashboard/
│   ├── customers/
│   ├── accounts/
│   ├── loans/
│   ├── payments/
│   └── reports/
├── layout/
│   ├── sidebar/
│   ├── header/
│   └── main-layout/
└── app.routes.ts            # Lazy-loaded feature routes
```

### Auth Guard (Keycloak)

```typescript
// core/auth/auth.guard.ts
import { KeycloakAuthGuard, KeycloakService } from 'keycloak-angular';

@Injectable({ providedIn: 'root' })
export class AuthGuard extends KeycloakAuthGuard {
  constructor(protected readonly router: Router, protected readonly keycloak: KeycloakService) {
    super(router, keycloak);
  }
  async isAccessAllowed(route: ActivatedRouteSnapshot): Promise<boolean> {
    if (!this.authenticated) {
      await this.keycloak.login({ redirectUri: window.location.href });
    }
    const requiredRoles = route.data['roles'] as string[];
    return requiredRoles?.every(role => this.roles.includes(role)) ?? true;
  }
}
```

---

## Mobile Frontend — Flutter 3+

### pubspec.yaml Dependencies

```yaml
dependencies:
  flutter:
    sdk: flutter

  # Auth
  flutter_appauth: ^6.0.0
  flutter_secure_storage: ^9.0.0

  # HTTP
  dio: ^5.4.0
  retrofit: ^4.1.0

  # State Management
  flutter_riverpod: ^2.5.0
  riverpod_annotation: ^2.3.0

  # Navigation
  go_router: ^13.0.0

  # Biometrics
  local_auth: ^2.1.8

  # UI
  google_fonts: ^6.1.0
  fl_chart: ^0.67.0           # Charts for dashboards
  shimmer: ^3.0.0              # Loading skeletons
  cached_network_image: ^3.3.1

  # Utils
  intl: ^0.19.0                # Currency/date formatting
  uuid: ^4.3.3

dev_dependencies:
  flutter_test:
    sdk: flutter
  build_runner: ^2.4.8
  retrofit_generator: ^8.1.0
  riverpod_generator: ^2.3.0
  mockito: ^5.4.4
```

### Feature-First Folder Structure

```
cba_mobile/lib/
├── main.dart
├── core/
│   ├── auth/                  # AppAuth + token storage
│   ├── network/               # Dio client + interceptors
│   ├── router/                # GoRouter configuration
│   └── theme/                 # App theme, colors, typography
├── shared/
│   ├── widgets/               # AccountCard, TransactionTile, LoadingOverlay
│   └── models/                # Shared DTOs
└── features/
    ├── auth/                  # Login, biometric, PIN
    ├── dashboard/             # Home screen, balance summary
    ├── accounts/              # Account list, detail, statement
    ├── loans/                 # Loan list, detail, repayment
    ├── payments/              # Transfer, pay bill, history
    └── profile/               # Settings, security, logout
```

### Keycloak Auth Flow (Flutter)

```dart
// core/auth/auth_service.dart
class AuthService {
  final FlutterAppAuth _appAuth = const FlutterAppAuth();
  static const String _clientId = 'cba-mobile';
  static const String _issuer = 'https://auth.cba.com/realms/cba';
  static const String _redirectUrl = 'com.cba.mobile://callback';

  Future<AuthorizationTokenResponse?> login() async {
    return await _appAuth.authorizeAndExchangeCode(
      AuthorizationTokenRequest(
        _clientId,
        _redirectUrl,
        issuer: _issuer,
        scopes: ['openid', 'profile', 'email', 'accounts', 'payments'],
        additionalParameters: {'acr_values': 'urn:mace:incommon:iap:silver'}, // MFA
      ),
    );
  }
}
```
