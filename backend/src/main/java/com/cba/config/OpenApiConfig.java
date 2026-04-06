package com.cba.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "CBA Core Banking API",
        version = "1.0.0",
        description = "Production-grade Core Banking Application REST API. " +
            "All endpoints require authentication via Keycloak OIDC. " +
            "Monetary values are in the currency specified (default: USD).",
        contact = @Contact(name = "CBA Engineering", email = "engineering@cba.com"),
        license = @License(name = "Private", url = "https://cba.com")
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local development"),
        @Server(url = "https://api-staging.cba.com", description = "Staging"),
        @Server(url = "https://api.cba.com", description = "Production")
    }
)
@SecurityScheme(
    name = "oauth2",
    type = SecuritySchemeType.OAUTH2,
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "${springdoc.swagger-ui.oauth.auth-url:" +
                "http://localhost:8180/realms/cba/protocol/openid-connect/auth}",
            tokenUrl = "${springdoc.swagger-ui.oauth.token-url:" +
                "http://localhost:8180/realms/cba/protocol/openid-connect/token}",
            scopes = {
                @OAuthScope(name = "openid", description = "OpenID Connect"),
                @OAuthScope(name = "profile", description = "User profile"),
                @OAuthScope(name = "accounts", description = "Account access (Open Banking)"),
                @OAuthScope(name = "payments", description = "Payment initiation (Open Banking)")
            }
        )
    )
)
public class OpenApiConfig {
}
