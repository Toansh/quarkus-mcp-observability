package io.github.toansh.mcp.mcp;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Native mode integration test.
 * When executed via './mvnw verify -Dnative', Quarkus launches the compiled native binary (or container)
 * and executes these tests against the live endpoints over HTTP.
 */
@QuarkusIntegrationTest
class McpEndpointsIT {

    @Test
    void healthEndpointIsOpen() {
        given().when().get("/q/health").then().statusCode(200);
    }

    @Test
    void metricsEndpointIsOpen() {
        given().when().get("/q/metrics").then().statusCode(200);
    }

    @Test
    void openapiEndpointIsOpen() {
        given().when().get("/q/openapi").then()
                .statusCode(200)
                .body(containsString("bearer-key"));
    }

    @Test
    void missingAuthorizationHeaderReturns401() {
        given()
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize"}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(401)
                .body("error", equalTo("missing_bearer"));
    }

    @Test
    void invalidBearerTokenReturns401() {
        given()
                .header("Authorization", "Bearer mcp_definitely_not_a_real_key")
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize"}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(401)
                .body("error", equalTo("invalid_key"));
    }
}
