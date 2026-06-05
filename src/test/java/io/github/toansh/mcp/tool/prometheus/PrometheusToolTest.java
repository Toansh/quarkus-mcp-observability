package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.ToolResult;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PrometheusTool}. Deliberately pure JUnit — no @QuarkusTest boot — so the
 * cap-enforcement, error-mapping, and validation logic can be exercised without a Prometheus
 * instance, a database, or Docker.
 */
class PrometheusToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StubClient client;
    private PrometheusTool tool;

    @BeforeEach
    void setUp() {
        client = new StubClient();
        tool = new PrometheusTool();
        tool.mapper = MAPPER;
        tool.client = client;
        tool.maxSeries = 1000;
    }

    @Test
    void rejectsMissingPromql() {
        ToolResult result = tool.call(MAPPER.createObjectNode());
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("required"));
    }

    @Test
    void rejectsBlankPromql() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("promql", "   ");
        ToolResult result = tool.call(args);
        assertTrue(result.isError());
    }

    @Test
    void rejectsNonStringPromql() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("promql", 42);
        ToolResult result = tool.call(args);
        assertTrue(result.isError());
    }

    @Test
    void passesThroughSmallResult() {
        client.next = successResponse(buildVector(3));
        ToolResult result = tool.call(args("up"));
        assertFalse(result.isError());
        assertTrue(result.content().get(0).text().contains("\"resultType\""));
    }

    @Test
    void rejectsResultExceedingMaxSeries() {
        client.next = successResponse(buildVector(1001));
        ToolResult result = tool.call(args("up"));
        assertTrue(result.isError());
        String msg = result.content().get(0).text();
        assertTrue(msg.contains("1001 series"));
        assertTrue(msg.contains("1000-series limit"));
        assertTrue(msg.toLowerCase().contains("narrow"));
    }

    @Test
    void acceptsResultAtExactlyMaxSeries() {
        client.next = successResponse(buildVector(1000));
        ToolResult result = tool.call(args("up"));
        assertFalse(result.isError());
    }

    @Test
    void surfacesPrometheusErrorEnvelope() {
        client.next = new PromResponse("error", null, "bad_data", "parse error at char 5");
        ToolResult result = tool.call(args("up{"));
        assertTrue(result.isError());
        String msg = result.content().get(0).text();
        assertTrue(msg.contains("bad_data"));
        assertTrue(msg.contains("parse error"));
    }

    @Test
    void mapsHttpErrorToToolError() {
        client.toThrow = new WebApplicationException(Response.status(503).build());
        ToolResult result = tool.call(args("up"));
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("HTTP 503"));
    }

    @Test
    void mapsProcessingExceptionToTimeoutHint() {
        client.toThrow = new ProcessingException(new SocketTimeoutException("Read timed out"));
        ToolResult result = tool.call(args("up"));
        assertTrue(result.isError());
        String msg = result.content().get(0).text();
        assertTrue(msg.contains("5s timeout"));
        assertTrue(msg.contains("cheaper query"));
    }

    @Test
    void respectsConfiguredMaxSeries() {
        tool.maxSeries = 5;
        client.next = successResponse(buildVector(6));
        ToolResult result = tool.call(args("up"));
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("5-series limit"));
    }

    @Test
    void inputSchemaRequiresPromql() {
        ObjectNode schema = tool.inputSchema();
        assertEquals("object", schema.get("type").asText());
        assertEquals("promql", schema.get("required").get(0).asText());
        assertEquals("string", schema.get("properties").get("promql").get("type").asText());
    }

    @Test
    void nameAndDescriptionAreStable() {
        assertEquals("query_prometheus", tool.name());
        assertTrue(tool.description().toLowerCase().contains("promql"));
    }

    // --- helpers ---

    private static ObjectNode args(String promql) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("promql", promql);
        return n;
    }

    private static PromResponse successResponse(JsonNode result) {
        return new PromResponse("success", new PromResponse.PromData("vector", result), null, null);
    }

    private static ArrayNode buildVector(int n) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (int i = 0; i < n; i++) {
            ObjectNode series = arr.addObject();
            series.putObject("metric").put("__name__", "up").put("instance", "host-" + i);
            ArrayNode value = series.putArray("value");
            value.add(1700000000.0);
            value.add("1");
        }
        return arr;
    }

    /** Hand-rolled stub for {@link PrometheusClient}. Avoids pulling Mockito for this small surface. */
    private static final class StubClient implements PrometheusClient {
        PromResponse next;
        RuntimeException toThrow;

        @Override
        public PromResponse instantQuery(String promql) {
            if (toThrow != null) {
                throw toThrow;
            }
            return next;
        }

        @Override
        public PromResponse rangeQuery(String promql, String start, String end, String step) {
            throw new UnsupportedOperationException("instant-only stub");
        }
    }
}
