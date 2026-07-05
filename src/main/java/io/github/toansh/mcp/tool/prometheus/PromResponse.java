package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Top-level envelope returned by Prometheus's HTTP API.
 * <p>On success: {@code status="success"}, {@code data} populated.
 * On error: {@code status="error"}, {@code errorType} + {@code error} populated.
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromResponse(
        String status,
        PromData data,
        String errorType,
        String error
) {
    public boolean isSuccess() {
        return "success".equals(status);
    }

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PromData(String resultType, JsonNode result) {}
}
