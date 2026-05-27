CREATE TABLE audit_log (
    id            BIGSERIAL    PRIMARY KEY,
    created_at    TIMESTAMPTZ  NOT NULL,
    caller        VARCHAR(128) NOT NULL,
    tool          VARCHAR(64)  NOT NULL,
    args          JSONB        NULL,
    result_size   BIGINT       NULL,
    latency_ms    BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL
);

CREATE INDEX idx_audit_caller_created_at ON audit_log (caller, created_at DESC);
CREATE INDEX idx_audit_created_at        ON audit_log (created_at DESC);

COMMENT ON TABLE audit_log IS
    'Every MCP tool invocation is recorded here. Audit is not optional — see README design rules.';
