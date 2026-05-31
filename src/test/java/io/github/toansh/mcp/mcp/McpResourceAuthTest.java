package io.github.toansh.mcp.mcp;

import io.github.toansh.mcp.auth.TestKeyFixture;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Auth-path tests for {@code POST /mcp}. Runs against a real Postgres started by
 * Quarkus Devservices — requires Docker on the host. The unit tests in
 * {@code PrometheusToolTest} cover what doesn't need a database.
 */
@QuarkusTest
class McpResourceAuthTest {

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
    void missingAuthorizationHeaderReturns401() {
        given()
                .contentType("application/json")
                .body(INITIALIZE_BODY)
                .when().post("/mcp")
                .then()
                .statusCode(401)
                .body("error", equalTo("missing_bearer"));
    }

    @Test
    void malformedAuthorizationHeaderReturns401() {
        given()
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .contentType("application/json")
                .body(INITIALIZE_BODY)
                .when().post("/mcp")
                .then()
                .statusCode(401)
                .body("error", equalTo("missing_bearer"));
    }

    @Test
    void unknownBearerTokenReturns401() {
        given()
                .header("Authorization", "Bearer mcp_definitely_not_a_real_key")
                .contentType("application/json")
                .body(INITIALIZE_BODY)
                .when().post("/mcp")
                .then()
                .statusCode(401)
                .body("error", equalTo("invalid_key"));
    }

    @Test
    void validBearerReturnsInitializeResponse() {
        given()
                .header("Authorization", "Bearer " + TestKeyFixture.TOKEN)
                .contentType("application/json")
                .body(INITIALIZE_BODY)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("jsonrpc", equalTo("2.0"))
                .body("id", equalTo(1))
                .body("result.protocolVersion", equalTo("2024-11-05"))
                .body("result.serverInfo.name", equalTo("quarkus-mcp-observability"));
    }

    @Test
    void healthEndpointIsOpen() {
        given().when().get("/q/health").then().statusCode(200);
    }

    @Test
    void metricsEndpointIsOpen() {
        given().when().get("/q/metrics").then()
                .statusCode(200)
                .body(containsString("jvm_"));
    }

    @Test
    void openapiEndpointIsOpen() {
        given().when().get("/q/openapi").then()
                .statusCode(200)
                .body(containsString("bearer-key"));
    }
}
