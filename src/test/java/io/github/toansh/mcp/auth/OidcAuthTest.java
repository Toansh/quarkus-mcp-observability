package io.github.toansh.mcp.auth;

import io.github.toansh.mcp.audit.AuditLog;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies OAuth2 / OIDC authentication path. When a request is authenticated by Quarkus OIDC
 * (simulated here via @TestSecurity), our hybrid auth filter adopts the OIDC principal as the caller
 * and allows access to /mcp without requiring a static API key in PostgreSQL.
 */
@QuarkusTest
class OidcAuthTest {

    private static final String OIDC_PRINCIPAL = "oidc-admin@example.com";

    @BeforeEach
    void setup() {
        wipeAudit();
    }

    @AfterEach
    void cleanup() {
        wipeAudit();
    }

    @Test
    @TestSecurity(user = OIDC_PRINCIPAL, roles = "user")
    void validOidcTokenAllowsAccessAndRecordsOidcPrincipalInAudit() {
        // Verify tools/list succeeds with OIDC identity
        given()
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list"}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("jsonrpc", equalTo("2.0"))
                .body("id", equalTo(1));

        // Verify tools/call succeeds and records OIDC principal in Postgres audit log
        given()
                .contentType("application/json")
                .body("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/call",
                         "params":{"name":"echo","arguments":{"message":"testing oidc auth"}}}
                        """)
                .when().post("/mcp")
                .then()
                .statusCode(200)
                .body("result.isError", equalTo(false))
                .body("result.content[0].text", equalTo("testing oidc auth"));

        List<AuditLog> rows = recentAudit();
        assertEquals(1, rows.size(), "exactly one audit row expected for tools/call");
        AuditLog row = rows.get(0);
        assertEquals(OIDC_PRINCIPAL, row.caller, "audit caller must equal OIDC authenticated principal");
        assertEquals("echo", row.tool);
        assertEquals("OK", row.status);
    }

    @Transactional
    void wipeAudit() {
        AuditLog.deleteAll();
    }

    @Transactional
    List<AuditLog> recentAudit() {
        return AuditLog.listAll();
    }
}
