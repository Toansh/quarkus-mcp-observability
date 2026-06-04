package io.github.toansh.mcp.ratelimit;

/**
 * A classic token bucket. Tokens refill continuously at {@code refillPerSecond} up to
 * {@code capacity} (the burst ceiling); each accepted request consumes one token.
 *
 * <p>The current time (monotonic nanos) is passed into every method rather than read
 * internally, so behaviour is deterministic under test. Mutating methods are synchronized —
 * one bucket is shared by all concurrent requests from a single principal.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillNanos;

    TokenBucket(double capacity, double refillPerSecond, long nowNanos) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /** Consume one token if available. Returns true when the request is allowed. */
    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Whole seconds until at least one token is available (0 if one is available now). */
    synchronized long retryAfterSeconds(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            return 0L;
        }
        return (long) Math.ceil((1.0 - tokens) / refillPerSecond);
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double refilled = (elapsed / 1_000_000_000.0) * refillPerSecond;
        tokens = Math.min(capacity, tokens + refilled);
        lastRefillNanos = nowNanos;
    }
}
