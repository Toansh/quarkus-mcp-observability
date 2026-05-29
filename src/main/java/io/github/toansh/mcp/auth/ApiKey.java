package io.github.toansh.mcp.auth;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "api_keys")
public class ApiKey extends PanacheEntity {

    @Column(name = "key_hash", nullable = false, length = 64, unique = true)
    public String keyHash;

    @Column(nullable = false, length = 128)
    public String principal;

    @Column(length = 128)
    public String label;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    @Column(nullable = false)
    public boolean revoked;

    public static Optional<ApiKey> findActiveByHash(String hash) {
        return find("keyHash = ?1 and revoked = false", hash).firstResultOptional();
    }
}
