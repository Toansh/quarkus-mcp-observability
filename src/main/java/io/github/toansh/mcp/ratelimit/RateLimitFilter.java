package io.github.toansh.mcp.ratelimit;

import io.github.toansh.mcp.auth.Caller;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * Per-client rate limiting on /mcp. Runs at AUTHORIZATION priority — after
 * {@code ApiKeyAuthFilter} (AUTHENTICATION) has populated the {@link Caller} principal — so the
 * limit is keyed by the real authenticated identity. Over-limit requests are rejected with
 * 429 + Retry-After before any tool runs, and counted in {@code mcp.ratelimit.rejected} so the
 * server's own Prometheus scrape shows when clients are being throttled.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    RateLimiter rateLimiter;

    @Inject
    Caller caller;

    @Inject
    MeterRegistry registry;

    @Override
    public void filter(ContainerRequestContext ctx) {
        // Same leading-slash normalization as the auth filter (UriInfo.getPath() includes it
        // under Quarkus REST), so the guard can't silently skip enforcement.
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (!path.startsWith("mcp")) {
            return;
        }

        String principal = caller.getPrincipal();
        if (principal == null) {
            // Unauthenticated requests were already rejected by the auth filter — nothing to limit.
            return;
        }

        RateLimiter.Decision decision = rateLimiter.check(principal);
        if (!decision.allowed()) {
            registry.counter("mcp.ratelimit.rejected").increment();
            Map<String, Object> body = Map.of(
                    "error", "rate_limited",
                    "message", "Per-client rate limit exceeded. Retry after "
                            + decision.retryAfterSeconds() + "s.",
                    "retry_after_seconds", decision.retryAfterSeconds());
            ctx.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, decision.retryAfterSeconds())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build());
        }
    }
}
