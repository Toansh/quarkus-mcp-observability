package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class GetFiringAlertsTool implements Tool {

    private static final Logger LOG = Logger.getLogger(GetFiringAlertsTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    @RestClient
    PrometheusClient client;

    @Override
    public String name() {
        return "get_firing_alerts";
    }

    @Override
    public String description() {
        return "Retrieve currently active (firing or pending) alerts from Prometheus. "
                + "Returns alert names, severity, labels, annotations, and active duration. Bounded read-only access for incident triage.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        props.putObject("state")
                .put("type", "string")
                .put("description", "Filter alerts by state: 'firing', 'pending', or leave empty for all active alerts.");
        props.putObject("severity")
                .put("type", "string")
                .put("description", "Filter alerts by severity label (e.g. 'critical', 'warning').");

        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        String stateFilter = arguments.path("state").asText("").trim();
        String sevFilter = arguments.path("severity").asText("").trim();

        try {
            AlertsResponse response = client.alerts();
            if (response == null || !"success".equalsIgnoreCase(response.status())) {
                String err = response != null ? response.error() : "null response";
                return ToolResult.error("Prometheus API error: " + err);
            }

            AlertsResponse.AlertsData data = response.data();
            if (data == null || data.alerts() == null || data.alerts().isEmpty()) {
                return ToolResult.ofText("(no active alerts found in Prometheus)");
            }

            List<AlertsResponse.Alert> alerts = data.alerts();
            StringBuilder sb = new StringBuilder();
            int count = 0;

            for (AlertsResponse.Alert alert : alerts) {
                String state = alert.state() != null ? alert.state() : "unknown";
                if (!stateFilter.isEmpty() && !stateFilter.equalsIgnoreCase(state)) {
                    continue;
                }

                String sev = alert.labels() != null ? alert.labels().getOrDefault("severity", "unknown") : "unknown";
                if (!sevFilter.isEmpty() && !sevFilter.equalsIgnoreCase(sev)) {
                    continue;
                }

                count++;
                String name = alert.labels() != null ? alert.labels().getOrDefault("alertname", "UnnamedAlert") : "UnnamedAlert";
                String summary = alert.annotations() != null ? alert.annotations().getOrDefault("summary",
                        alert.annotations().getOrDefault("description", "")) : "";

                sb.append(String.format("[%s] %s (Severity: %s)\n", state.toUpperCase(), name, sev));
                if (!summary.isEmpty()) {
                    sb.append("  Summary: ").append(summary).append("\n");
                }
                sb.append("  Active since: ").append(alert.activeAt() != null ? alert.activeAt() : "unknown").append("\n");
                sb.append("  Labels: ").append(alert.labels() != null ? alert.labels() : "{}").append("\n\n");
            }

            if (count == 0) {
                return ToolResult.ofText(String.format("(no active alerts matching filters: state='%s', severity='%s')",
                        stateFilter, sevFilter));
            }

            return ToolResult.ofText(sb.toString().trim());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to query Prometheus /api/v1/alerts");
            return ToolResult.error("Failed to query Prometheus alerts: " + e.getMessage());
        }
    }
}
