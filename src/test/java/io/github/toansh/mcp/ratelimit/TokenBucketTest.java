package io.github.toansh.mcp.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the bucket math — no Quarkus, no Docker. Time is fed in explicitly so
 * refill behaviour is deterministic. Shares the package to construct the package-private bucket.
 */
class TokenBucketTest {

    private static final long SEC = 1_000_000_000L;

    @Test
    void allowsUpToCapacityThenDenies() {
        TokenBucket b = new TokenBucket(3, 1.0, 0L);
        assertTrue(b.tryConsume(0L));
        assertTrue(b.tryConsume(0L));
        assertTrue(b.tryConsume(0L));
        assertFalse(b.tryConsume(0L), "4th request in the same instant must be denied");
    }

    @Test
    void refillsOverTime() {
        TokenBucket b = new TokenBucket(2, 1.0, 0L); // 1 token/sec
        assertTrue(b.tryConsume(0L));
        assertTrue(b.tryConsume(0L));
        assertFalse(b.tryConsume(0L));
        assertTrue(b.tryConsume(SEC), "one token refills after a second");
        assertFalse(b.tryConsume(SEC));
    }

    @Test
    void refillIsCappedAtCapacity() {
        TokenBucket b = new TokenBucket(2, 1.0, 0L);
        assertTrue(b.tryConsume(0L));
        assertTrue(b.tryConsume(0L));
        // A long idle period refills, but never above capacity (2).
        assertTrue(b.tryConsume(100 * SEC));
        assertTrue(b.tryConsume(100 * SEC));
        assertFalse(b.tryConsume(100 * SEC));
    }

    @Test
    void retryAfterReportsWholeSecondsToNextToken() {
        TokenBucket b = new TokenBucket(1, 0.5, 0L); // 0.5 tokens/sec -> 2s per token
        assertTrue(b.tryConsume(0L));
        assertFalse(b.tryConsume(0L));
        assertEquals(2L, b.retryAfterSeconds(0L));
    }
}
