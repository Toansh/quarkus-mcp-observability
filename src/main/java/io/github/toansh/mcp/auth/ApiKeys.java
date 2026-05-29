package io.github.toansh.mcp.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@ApplicationScoped
public class ApiKeys {

    /**
     * Look up an active key by its raw bearer token. On hit, touches last_used_at.
     * Returns empty on miss — callers must translate that into 401.
     */
    @Transactional
    public Optional<ApiKey> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256Hex(rawToken);
        Optional<ApiKey> found = ApiKey.findActiveByHash(hash);
        found.ifPresent(k -> k.lastUsedAt = Instant.now());
        return found;
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
