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
 * Unit tests for {@link PrometheusRangeTool}. Pure JUnit — no @QuarkusTest — so input validation,
 * the total-samples cap, and error mapping run without Prometheus, a database, or Docker.
 */
class PrometheusRangeToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StubClient client;
    private PrometheusRangeTool tool;

    @BeforeEach
    void setUp() {
        client = new StubClient();
        tool = new PrometheusRangeTool();
        tool.mapper = MAPPER;
        tool.client = client;
        tool.maxSamples = 11000;
    }

    @Test
    void rejectsMissingPromql() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("start", "0").put("end", "60").put("step", "15s");
        ToolResult result = tool.call(args);
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("promql"));
    }

    @Test
    void rejectsMissingTimeParams() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("promql", "up").put("start", "0").put("step", "15s"); // no end
        ToolResult result = tool.call(args);
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("end"));
    }

    @Test
    void acceptsNumericTimeParams() {
        client.next = successMatrix(1, 3);
        ObjectNode args = MAPPER.createObjectNode();
        args.put("promql", "up");
        args.put("start", 0);          // numbers, not strings
        args.put("end", 60);
        args.put("step", 15);
        ToolResult result = tool.call(args);
        assertFalse(result.isError());
    }

    @Test
    void passesThroughSmallMatrix() {
        client.next = successMatrix(2, 4);
        ToolResult result = tool.call(args("up", "0", "60", "15s"));
        assertFalse(result.isError());
        assertTrue(result.content().get(0).text().contains("\"resultType\""));
    }

    @Test
    void rejectsResultExceedingMaxSamples() {
        tool.maxSamples = 10;
        client.next = successMatrix(2, 6); // 12 samples
        ToolResult result = tool.call(args("up", "0", "60", "15s"));
        assertTrue(result.isError());
        String msg = result.content().get(0).text();
        assertTrue(msg.contains("12 samples"));
        assertTrue(msg.contains("10-sample limit"));
        assertTrue(msg.toLowerCase().contains("step"));
    }

    @Test
    void acceptsResultAtExactlyMaxSamples() {
        tool.maxSamples = 10;
        client.next = successMatrix(2, 5); // exactly 10 samples
        ToolResult result = tool.call(args("up", "0", "60", "15s"));
        assertFalse(result.isError());
    }

    @Test
    void surfacesPrometheusErrorEnvelope() {
        client.next = new PromResponse("error", null, "bad_data", "invalid step");
        ToolResult result = tool.call(args("up", "0", "60", "0"));
        assertTrue(result.isError());
        String msg = result.content().get(0).text();
        assertTrue(msg.contains("bad_data"));
        assertTrue(msg.contains("invalid step"));
    }

    @Test
    void mapsHttpErrorToToolError() {
        client.toThrow = new WebApplicationException(Response.status(503).build());
        ToolResult result = tool.call(args("up", "0", "60", "15s"));
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("HTTP 503"));
    }

    @Test
    void mapsProcessingExceptionToTimeoutHint() {
        client.toThrow = new ProcessingException(new SocketTimeoutException("Read timed out"));
        ToolResult result = tool.call(args("up", "0", "60", "15s"));
        assertTrue(result.isError());
        assertTrue(result.content().get(0).text().contains("5s timeout"));
    }

    @Test
    void inputSchemaRequiresAllFourFields() {
        ObjectNode schema = tool.inputSchema();
        assertEquals("object", schema.get("type").asText());
        ArrayNode required = (ArrayNode) schema.get("required");
        assertEquals(4, required.size());
        assertTrue(required.toString().contains("promql"));
        assertTrue(required.toString().contains("start"));
        assertTrue(required.toString().contains("end"));
        assertTrue(required.toString().contains("step"));
    }

    @Test
    void nameAndDescriptionAreStable() {
        assertEquals("query_prometheus_range", tool.name());
        assertTrue(tool.description().toLowerCase().contains("range"));
    }

    // --- helpers ---

    private static ObjectNode args(String promql, String start, String end, String step) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("promql", promql).put("start", start).put("end", end).put("step", step);
        return n;
    }

    private static PromResponse successMatrix(int series, int pointsPerSeries) {
        return new PromResponse("success", new PromResponse.PromData("matrix", buildMatrix(series, pointsPerSeries)), null, null);
    }

    private static ArrayNode buildMatrix(int series, int pointsPerSeries) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (int s = 0; s < series; s++) {
            ObjectNode entry = arr.addObject();
            entry.putObject("metric").put("__name__", "up").put("instance", "host-" + s);
            ArrayNode values = entry.putArray("values");
            for (int p = 0; p < pointsPerSeries; p++) {
                ArrayNode point = values.addArray();
                point.add(1700000000.0 + p * 15);
                point.add("1");
            }
        }
        return arr;
    }

    /** Hand-rolled stub for {@link PrometheusClient}; only the range method is exercised here. */
    private static final class StubClient implements PrometheusClient {
        PromResponse next;
        RuntimeException toThrow;

        @Override
        public PromResponse instantQuery(String promql) {
            throw new UnsupportedOperationException("range-only stub");
        }

        @Override
        public PromResponse rangeQuery(String promql, String start, String end, String step) {
            if (toThrow != null) {
                throw toThrow;
            }
            return next;
        }
    }
}
