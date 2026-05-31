package io.github.toansh.mcp.auth;

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
 * Enforces bearer-token auth on /mcp. /q/* (health, metrics, openapi, swagger-ui) is left open.
 *
 * <p>Rationale: a single hand-rolled filter is more transparent in a portfolio repo than wiring
 * quarkus-security's HttpAuthenticationMechanism + IdentityProvider stack for a one-scheme API.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    ApiKeys apiKeys;

    @Inject
    Caller caller;

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
