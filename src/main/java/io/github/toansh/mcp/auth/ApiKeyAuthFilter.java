package io.github.toansh.mcp.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.Optional;

/**
 * Enforces hybrid bearer-token auth on /mcp (supporting both OAuth2/OIDC JWT tokens and static API keys).
 * /q/* (health, metrics, openapi, swagger-ui) is left open.
 *
 * <p>When OIDC is enabled in production, valid JWT access tokens are verified via JWKS and their subject/principal
 * is used as the audit caller. If OIDC is disabled or the token is not a JWT, falls back to checking SHA-256
 * hashed static API keys (mcp_*) in PostgreSQL.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    ApiKeys apiKeys;

    @Inject
    Caller caller;

    @Inject
    SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext ctx) {
        // UriInfo.getPath() includes a leading slash under Quarkus REST but not under every
        // JAX-RS runtime — normalize it before matching so the guard can't silently skip auth.
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.startsWith("mcp")) {
            return;
        }

        // If Quarkus OIDC is enabled and has already successfully authenticated the bearer token
        // (e.g. valid OAuth2 / OIDC Access Token / JWT), use the OIDC principal as caller identity.
        if (identity != null && !identity.isAnonymous()) {
            String oidcPrincipal = identity.getPrincipal().getName();
            if (oidcPrincipal != null && !oidcPrincipal.isBlank()) {
                caller.setPrincipal(oidcPrincipal);
                return;
            }
        }

        String header = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            abort(ctx, "missing_bearer", "Authorization: Bearer <key> required");
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();

        Optional<ApiKey> key = apiKeys.authenticate(token);
        if (key.isEmpty()) {
            abort(ctx, "invalid_key", "API key not recognized or revoked");
            return;
        }

        caller.setPrincipal(key.get().principal);
    }

    private void abort(ContainerRequestContext ctx, String code, String message) {
        Map<String, Object> body = Map.of("error", code, "message", message);
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"mcp\"")
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build());
    }
}
