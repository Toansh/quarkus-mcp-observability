package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GetClusterEventsTool implements Tool {

    private static final Logger LOG = Logger.getLogger(GetClusterEventsTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    KubernetesClient client;

    @Override
    public String name() {
        return "get_cluster_events";
    }

    @Override
    public String description() {
        return "Retrieve recent Kubernetes events (warnings, errors, scheduling failures, container restarts) "
                + "in a namespace or across all namespaces. Bounded read-only access for SRE triage.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        props.putObject("namespace")
                .put("type", "string")
                .put("description", "Kubernetes namespace. Leave empty to query across all namespaces.");
        props.putObject("type")
                .put("type", "string")
                .put("description", "Filter events by type (e.g. 'Warning', 'Normal'). Leave empty for all types.");
        props.putObject("limit")
                .put("type", "integer")
                .put("description", "Maximum number of events to return (max 100, default 50).");

        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        String namespace = arguments.path("namespace").asText("").trim();
        String typeFilter = arguments.path("type").asText("").trim();
        int limit = Math.min(arguments.path("limit").asInt(50), 100);
        if (limit <= 0) {
            limit = 50;
        }

        try {
            List<Event> events;
            if (!namespace.isEmpty()) {
                events = client.v1().events().inNamespace(namespace).list().getItems();
            } else {
                events = client.v1().events().inAnyNamespace().list().getItems();
            }

            if (events == null || events.isEmpty()) {
                return ToolResult.ofText("(no Kubernetes events found)");
            }

            // Filter by type if specified
            if (!typeFilter.isEmpty()) {
                events = events.stream()
                        .filter(e -> typeFilter.equalsIgnoreCase(e.getType()))
                        .collect(Collectors.toList());
            }

            if (events.isEmpty()) {
                return ToolResult.ofText(String.format("(no Kubernetes events matching type='%s')", typeFilter));
            }

            // Sort by timestamp (newest first)
            events.sort(Comparator.comparing((Event e) -> {
                if (e.getLastTimestamp() != null) return e.getLastTimestamp();
                if (e.getEventTime() != null && e.getEventTime().getTime() != null) return e.getEventTime().getTime();
                if (e.getMetadata() != null && e.getMetadata().getCreationTimestamp() != null) return e.getMetadata().getCreationTimestamp();
                return "";
            }).reversed());

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Event event : events) {
                if (count >= limit) break;
                count++;

                String time = event.getLastTimestamp() != null ? event.getLastTimestamp() :
                        (event.getEventTime() != null && event.getEventTime().getTime() != null ? event.getEventTime().getTime() : "unknown");
                String obj = event.getInvolvedObject() != null ?
                        (event.getInvolvedObject().getKind() + "/" + event.getInvolvedObject().getName() +
                                (event.getInvolvedObject().getNamespace() != null ? " [" + event.getInvolvedObject().getNamespace() + "]" : "")) : "unknown";
                int evCount = event.getCount() != null ? event.getCount() : 1;
                String evType = event.getType() != null ? event.getType() : "Unknown";
                String reason = event.getReason() != null ? event.getReason() : "None";
                String msg = event.getMessage() != null ? event.getMessage() : "";

                sb.append(String.format("[%s] [%s] %s (count: %d)\n  Reason: %s | Message: %s\n\n",
                        evType.toUpperCase(), time, obj, evCount, reason, msg));
            }

            return ToolResult.ofText(sb.toString().trim());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to get Kubernetes events in namespace %s", namespace);
            return ToolResult.error("Failed to retrieve Kubernetes events: " + e.getMessage());
        }
    }
}
