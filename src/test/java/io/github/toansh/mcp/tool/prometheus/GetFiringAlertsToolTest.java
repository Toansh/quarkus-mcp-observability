package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetFiringAlertsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PrometheusClient client;
    private GetFiringAlertsTool tool;

    @BeforeEach
    void setUp() {
        client = mock(PrometheusClient.class);
        tool = new GetFiringAlertsTool();
        tool.mapper = MAPPER;
        tool.client = client;
    }

    @Test
    void getAlertsSuccess() {
        AlertsResponse.Alert alert = new AlertsResponse.Alert(
                Map.of("alertname", "HighCpuAlert", "severity", "critical"),
                Map.of("summary", "CPU usage > 90%"),
                "firing",
                "2026-07-05T10:00:00Z",
                "95.0"
        );
        AlertsResponse res = new AlertsResponse("success", new AlertsResponse.AlertsData(List.of(alert)), null, null);
        when(client.alerts()).thenReturn(res);

        ObjectNode args = MAPPER.createObjectNode();
        ToolResult result = tool.call(args);

        assertFalse(result.isError());
        assertTrue(result.content().get(0).text().contains("FIRING"));
        assertTrue(result.content().get(0).text().contains("HighCpuAlert"));
        assertTrue(result.content().get(0).text().contains("CPU usage > 90%"));
    }

    @Test
    void getAlertsFilterByState() {
        AlertsResponse.Alert firing = new AlertsResponse.Alert(
                Map.of("alertname", "HighCpuAlert", "severity", "critical"),
                Map.of("summary", "CPU usage > 90%"),
                "firing",
                "2026-07-05T10:00:00Z",
                "95.0"
        );
        AlertsResponse.Alert pending = new AlertsResponse.Alert(
                Map.of("alertname", "LowMemAlert", "severity", "warning"),
                Map.of("summary", "Memory < 10%"),
                "pending",
                "2026-07-05T10:05:00Z",
                "8.0"
        );
        AlertsResponse res = new AlertsResponse("success", new AlertsResponse.AlertsData(List.of(firing, pending)), null, null);
        when(client.alerts()).thenReturn(res);

        ObjectNode args = MAPPER.createObjectNode();
        args.put("state", "firing");
        ToolResult result = tool.call(args);

        assertFalse(result.isError());
        assertTrue(result.content().get(0).text().contains("HighCpuAlert"));
        assertFalse(result.content().get(0).text().contains("LowMemAlert"));
    }
}
