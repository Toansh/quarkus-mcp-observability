package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Bounded-by-construction PromQL tool. Demonstrates the safety rules from the README:
 * read-only, hard timeout (set on the REST client), hard result-size cap (here),
 * and audited (by the dispatcher).
 *
 * <p>v1 supports instant queries only. Range queries land in a follow-up D.
 */
@ApplicationScoped
public class PrometheusTool implements Tool {

    private static final Logger LOG = Logger.getLogger(PrometheusTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    @RestClient
    PrometheusClient client;

    @ConfigProperty(name = "prometheus.tool.max-series", defaultValue = "1000")
    int maxSeries;

    @Override
    public String name() {
        return "query_prometheus";
    }

    @Override
    public String description() {
        return "Run an instant PromQL query against the configured Prometheus instance. "
                + "Read-only. 5s server-side timeout. Returns at most "
                + "the configured max-series result series — if your query exceeds that, "
                + "narrow it with stricter label matchers.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode promql = props.putObject("promql");
        promql.put("type", "string");
        promql.put("description", "PromQL expression, e.g. `up{job=\"prometheus\"}` or `sum(rate(http_requests_total[5m]))`.");
        schema.putArray("required").add("promql");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        JsonNode promqlNode = arguments == null ? null : arguments.get("promql");
        if (promqlNode == null || !promqlNode.isTextual() || promqlNode.asText().isBlank()) {
            return ToolResult.error("`promql` argument is required and must be a non-empty string.");
        }
        String promql = promqlNode.asText();

        PromResponse response;
        try {
            response = client.instantQuery(promql);
        } catch (WebApplicationException e) {
            int status = e.getResponse() == null ? -1 : e.getResponse().getStatus();
            LOG.warnf("Prometheus returned HTTP %d for query %s", status, promql);
            return ToolResult.error("Prometheus returned HTTP " + status + ".");
        } catch (ProcessingException e) {
            LOG.warnf(e, "Prometheus call failed (network/timeout) for query %s", promql);
            return ToolResult.error("Prometheus call failed: " + rootMessage(e)
                    + ". The 5s timeout may have been exceeded — try a cheaper query.");
        }

        if (!response.isSuccess()) {
            return ToolResult.error("Prometheus rejected the query (" + response.errorType() + "): " + response.error());
        }

        JsonNode result = response.data() == null ? null : response.data().result();
        int seriesCount = result != null && result.isArray() ? result.size() : 0;
        if (seriesCount > maxSeries) {
            return ToolResult.error("Query returned " + seriesCount + " series, exceeding the "
                    + maxSeries + "-series limit. Narrow your query with stricter label matchers "
                    + "or an aggregation (e.g. wrap in `sum by(...)`).");
        }

        return ToolResult.ofText(formatResult(response));
    }

    private String formatResult(PromResponse response) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.data());
        } catch (JsonProcessingException e) {
            return "{\"resultType\":\"" + safe(response.data().resultType()) + "\",\"result\":\"<serialization-failed>\"}";
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
