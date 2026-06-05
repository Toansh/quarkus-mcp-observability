package io.github.toansh.mcp.tool.prometheus;

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
 * Bounded-by-construction range PromQL tool: {@code /api/v1/query_range} over [start, end] at a
 * fixed step. Same safety rules as {@link PrometheusTool} (read-only, hard timeout, audited by the
 * dispatcher), but the size bound is on <em>total samples</em> (series × points) rather than series
 * count — a range query's payload grows with both the number of series and the number of steps.
 */
@ApplicationScoped
public class PrometheusRangeTool implements Tool {

    private static final Logger LOG = Logger.getLogger(PrometheusRangeTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    @RestClient
    PrometheusClient client;

    @ConfigProperty(name = "prometheus.tool.range.max-samples", defaultValue = "11000")
    int maxSamples;

    @Override
    public String name() {
        return "query_prometheus_range";
    }

    @Override
    public String description() {
        return "Run a PromQL range query against the configured Prometheus instance over a "
                + "[start, end] window at a fixed step. Read-only. 5s server-side timeout. Returns "
                + "at most the configured max-samples data points across all series — if your query "
                + "exceeds that, widen the step, shorten the window, or narrow the series with "
                + "label matchers.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("promql").put("type", "string")
                .put("description", "PromQL expression, e.g. `rate(http_requests_total[5m])`.");
        props.putObject("start").put("type", "string")
                .put("description", "Range start: RFC3339 timestamp or Unix seconds, e.g. `2026-06-04T10:00:00Z`.");
        props.putObject("end").put("type", "string")
                .put("description", "Range end: RFC3339 timestamp or Unix seconds.");
        props.putObject("step").put("type", "string")
                .put("description", "Resolution step: a duration such as `30s` / `5m`, or float seconds.");
        schema.putArray("required").add("promql").add("start").add("end").add("step");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        JsonNode promqlNode = arguments == null ? null : arguments.get("promql");
        if (promqlNode == null || !promqlNode.isTextual() || promqlNode.asText().isBlank()) {
            return ToolResult.error("`promql` argument is required and must be a non-empty string.");
        }
        String promql = promqlNode.asText();
        String start = scalarText(arguments, "start");
        String end = scalarText(arguments, "end");
        String step = scalarText(arguments, "step");
        if (start == null || end == null || step == null) {
            return ToolResult.error("`start`, `end`, and `step` are all required: start/end as "
                    + "RFC3339 or Unix seconds, step as a duration (e.g. `30s`) or float seconds.");
        }

        PromResponse response;
        try {
            response = client.rangeQuery(promql, start, end, step);
        } catch (WebApplicationException e) {
            int status = e.getResponse() == null ? -1 : e.getResponse().getStatus();
            LOG.warnf("Prometheus returned HTTP %d for range query %s", status, promql);
            return PromSupport.httpError(status);
        } catch (ProcessingException e) {
            LOG.warnf(e, "Prometheus range call failed (network/timeout) for query %s", promql);
            return PromSupport.timeoutError(e);
        }

        if (!response.isSuccess()) {
            return PromSupport.envelopeError(response);
        }

        JsonNode result = response.data() == null ? null : response.data().result();
        int samples = countSamples(result);
        if (samples > maxSamples) {
            return ToolResult.error("Range query returned " + samples + " samples, exceeding the "
                    + maxSamples + "-sample limit. Widen the step, shorten the [start, end] window, "
                    + "or narrow the series with stricter label matchers.");
        }

        return ToolResult.ofText(PromSupport.formatResult(response, mapper));
    }

    /** Total data points in a matrix result: the sum of each series' {@code values} count. */
    private static int countSamples(JsonNode matrix) {
        if (matrix == null || !matrix.isArray()) {
            return 0;
        }
        int total = 0;
        for (JsonNode series : matrix) {
            JsonNode values = series.get("values");
            if (values != null && values.isArray()) {
                total += values.size();
            }
        }
        return total;
    }

    /** Read a scalar argument (string or number) as text; null if absent, null, or blank. */
    private static String scalarText(JsonNode args, String field) {
        JsonNode node = args == null ? null : args.get(field);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }
}
