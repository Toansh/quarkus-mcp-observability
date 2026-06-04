package io.github.toansh.mcp.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-principal token-bucket rate limiter. One bucket per API-key principal, held in memory.
 *
 * <p>Single-instance by design: the buckets live in this JVM. Scaling horizontally would move
 * the counters to a shared store (e.g. Redis) — see the README rate-limiting note. For a
 * single self-hosted server this keeps the hot path lock-light and dependency-free.
 */
@ApplicationScoped
public class RateLimiter {

    @ConfigProperty(name = "mcp.rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "mcp.rate-limit.requests-per-minute", defaultValue = "60")
    int requestsPerMinute;

    @ConfigProperty(name = "mcp.rate-limit.burst", defaultValue = "20")
    int burst;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /** Charge one request to {@code principal} and decide whether it is allowed. */
    public Decision check(String principal) {
        if (!enabled) {
            return Decision.ALLOWED;
        }
        double refillPerSecond = requestsPerMinute / 60.0;
        TokenBucket bucket = buckets.computeIfAbsent(principal,
                p -> new TokenBucket(burst, refillPerSecond, System.nanoTime()));
        long now = System.nanoTime();
        if (bucket.tryConsume(now)) {
            return Decision.ALLOWED;
        }
        return Decision.denied(bucket.retryAfterSeconds(now));
    }

    /** Outcome of a rate-limit check. {@code retryAfterSeconds} is meaningful only when denied. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0L);

        static Decision denied(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
