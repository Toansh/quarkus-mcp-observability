package io.github.toansh.mcp.ratelimit;

import io.github.toansh.mcp.auth.TestKeyFixture;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end check that per-client rate limiting returns 429 once the burst is spent. Uses a
 * dedicated profile with a tiny burst so it can trip the limit in a couple of requests without
 * affecting the other @QuarkusTest classes, which share a generous test-wide limit.
 */
@QuarkusTest
@TestProfile(RateLimitTest.TinyLimitProfile.class)
class RateLimitTest {

    public static class TinyLimitProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "mcp.rate-limit.enabled", "true",
                    "mcp.rate-limit.burst", "2",
                    "mcp.rate-limit.requests-per-minute", "1"); // refill negligible within the test
        }
    }

    private static final String INITIALIZE_BODY = """
            {"jsonrpc":"2.0","id":1,"method":"initialize"}
            """;

    @Inject
    TestKeyFixture fixture;

    @BeforeEach
    void seedKey() {
        fixture.deleteAll();
        fixture.createKey();
    }

    @AfterEach
    void cleanup() {
        fixture.deleteAll();
    }

    @Test
    void rejectsWith429AfterBurstExhausted() {
        // burst = 2: the first two authenticated requests pass.
        for (int i = 0; i < 2; i++) {
            given().header("Authorization", "Bearer " + TestKeyFixture.TOKEN)
                    .contentType("application/json").body(INITIALIZE_BODY)
                    .when().post("/mcp")
                    .then().statusCode(200);
        }
        // The third is throttled, with a Retry-After hint and a structured error body.
        given().header("Authorization", "Bearer " + TestKeyFixture.TOKEN)
                .contentType("application/json").body(INITIALIZE_BODY)
                .when().post("/mcp")
                .then()
                .statusCode(429)
                .header("Retry-After", notNullValue())
                .body("error", equalTo("rate_limited"));
    }
}
