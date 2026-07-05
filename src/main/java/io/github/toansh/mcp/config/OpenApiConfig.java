package io.github.toansh.mcp.config;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

import jakarta.ws.rs.core.Application;

/**
 * OpenAPI document metadata + the {@code bearer-key} security scheme that gates {@code /mcp}.
 * Defined at the {@code Application} level so Swagger-UI's "Try it out" prompts for a token.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "quarkus-mcp-observability",
                version = "1.0.0-SNAPSHOT",
                description = "Production-grade MCP server in Quarkus — read-only, bounded, audited "
                        + "access to Prometheus and Kubernetes for AI assistants.",
                contact = @Contact(name = "Ansh Taneja", url = "https://github.com/Toansh"),
                license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
        )
)
@SecurityScheme(
        securitySchemeName = "bearer-key",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "mcp / JWT",
        in = SecuritySchemeIn.HEADER,
        description = "API key issued by the operator (`mcp_<random>`) OR OAuth2/OIDC Access Token (JWT). "
                + "Static keys are stored as SHA-256(token); JWTs are verified via JWKS."
)
public class OpenApiConfig extends Application {
}
