package io.github.toansh.mcp.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Helper bean for tests that need a real, committed API key in the database. The
 * insert/delete must run in their own transactions so the JAX-RS request thread can
 * see the row — {@code @TestTransaction} on the test method would roll back too late.
 */
@ApplicationScoped
public class TestKeyFixture {

    public static final String TOKEN = "mcp_test_fixture_token";
    public static final String PRINCIPAL = "test-fixture";

    @Transactional
    public Long createKey() {
        ApiKey key = new ApiKey();
        key.keyHash = ApiKeys.sha256Hex(TOKEN);
        key.principal = PRINCIPAL;
        key.label = "test-fixture";
        key.createdAt = Instant.now();
        key.revoked = false;
        key.persist();
        return key.id;
    }

    @Transactional
    public void deleteAll() {
        ApiKey.deleteAll();
    }
}
