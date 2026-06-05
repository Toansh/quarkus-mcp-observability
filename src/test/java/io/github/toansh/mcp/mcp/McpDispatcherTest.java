package io.github.toansh.mcp.mcp;

import io.github.toansh.mcp.audit.AuditLog;
import io.github.toansh.mcp.auth.TestKeyFixture;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * End-to-end tests of the JSON-RPC dispatcher behind the auth filter. Verifies the audit row is
 * actually persisted (the core invariant of the "fully audited" rule) and that error paths still
 * land an audit row with the right status code.
 *
 * <p>Same Devservices-Postgres caveat as {@link McpResourceAuthTest}: requires Docker.
 */
@QuarkusTest
class McpDispatcherTest {

    private static final String BEARER = "Bearer " + TestKeyFixture.TOKEN;

    @Inject
    TestKeyFixture fixture;

    @BeforeEach
    void seedKey() {
        wipeAudit();
        fixture.deleteAll();
        fixture.createKey();
    }

    @AfterEach
    void cleanup() {
        wipeAudit();
        fixture.deleteAll();
    }

    @Test
    void toolsListIncludesEchoAndQueryPrometheus() {
        given()
                .header("Authorization", BEARER)
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("result.tools.name", hasItem("echo"))
                .body("result.tools.name", hasItem("query_prometheus"))
                .body("result.tools.name", hasItem("query_prometheus_range"));
    }

    @Test
    void toolsCallEchoRoundTrips() {
        given()
                .header("Authorization", BEARER)
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":3,"method":"tools/call",
                         "params":{"name":"echo","arguments":{"message":"hello world"}}}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("result.isError", equalTo(false))
                .body("result.content[0].text", equalTo("hello world"));

        List<AuditLog> rows = recentAudit();
        assertEquals(1, rows.size(), "exactly one audit row expected");
        AuditLog row = rows.get(0);
        assertEquals(TestKeyFixture.PRINCIPAL, row.caller, "audit caller must equal authenticated principal");
        assertEquals("echo", row.tool);
        assertEquals("OK", row.status);
        assertFalse(row.latencyMs < 0, "latency must be non-negative");
    }

    @Test
    void unknownToolWritesUnknownToolAudit() {
        given()
                .header("Authorization", BEARER)
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":4,"method":"tools/call",
                         "params":{"name":"definitely_not_a_tool","arguments":{}}}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32601))
                .body("error.message", notNullValue());

        List<AuditLog> rows = recentAudit();
        assertEquals(1, rows.size());
        assertEquals("UNKNOWN_TOOL", rows.get(0).status);
        assertEquals("definitely_not_a_tool", rows.get(0).tool);
        assertEquals(TestKeyFixture.PRINCIPAL, rows.get(0).caller);
    }

    @Test
    void unknownMethodReturnsMethodNotFoundButDoesNotAudit() {
        given()
                .header("Authorization", BEARER)
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":5,"method":"resources/list"}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32601));

        assertEquals(0, recentAudit().size(),
                "non-tool methods (e.g. resources/list) should not write to the tool audit log");
    }

    // --- helpers ---

    @Transactional
    void wipeAudit() {
        AuditLog.deleteAll();
    }

    @Transactional
    List<AuditLog> recentAudit() {
        return AuditLog.listAll();
    }
}
