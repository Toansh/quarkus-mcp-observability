CREATE TABLE api_keys (
    id            BIGSERIAL    PRIMARY KEY,
    key_hash      CHAR(64)     NOT NULL,
    principal     VARCHAR(128) NOT NULL,
    label         VARCHAR(128) NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    last_used_at  TIMESTAMPTZ  NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_api_keys_hash ON api_keys (key_hash);

COMMENT ON TABLE  api_keys           IS 'API keys for /mcp. Raw token never stored — column holds SHA-256(token) hex.';
COMMENT ON COLUMN api_keys.key_hash  IS 'SHA-256 of the bearer token, lowercase hex (64 chars).';
COMMENT ON COLUMN api_keys.principal IS 'Logical caller identity. Recorded into audit_log.caller on every invocation.';
COMMENT ON COLUMN api_keys.label     IS 'Optional human-readable label for ops (e.g. "ci-runner", "demo").';
