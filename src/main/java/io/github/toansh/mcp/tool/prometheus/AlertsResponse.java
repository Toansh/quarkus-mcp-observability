package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

/**
 * Data transfer object representing the response from Prometheus's {@code /api/v1/alerts} endpoint.
 * Annotated with {@link RegisterForReflection} to ensure GraalVM Mandrel native image compatibility.
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertsResponse(
        String status,
        AlertsData data,
        String errorType,
        String error
) {
    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertsData(List<Alert> alerts) {}

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(
            Map<String, String> labels,
            Map<String, String> annotations,
            String state,
            String activeAt,
            String value
    ) {}
}
