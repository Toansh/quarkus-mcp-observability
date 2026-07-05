package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GetNodeStatusTool implements Tool {

    private static final Logger LOG = Logger.getLogger(GetNodeStatusTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    KubernetesClient client;

    @Override
    public String name() {
        return "get_node_status";
    }

    @Override
    public String description() {
        return "Retrieve health status, readiness, capacity (CPU, memory, pods), and pressure conditions "
                + "(DiskPressure, MemoryPressure, PIDPressure) for all Kubernetes nodes or a specific node.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        props.putObject("nodeName")
                .put("type", "string")
                .put("description", "Name of a specific node. Leave empty to retrieve status for all nodes.");

        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        String nodeName = arguments.path("nodeName").asText("").trim();

        try {
            List<Node> nodes;
            if (!nodeName.isEmpty()) {
                Node node = client.nodes().withName(nodeName).get();
                if (node == null) {
                    return ToolResult.error("Node not found: " + nodeName);
                }
                nodes = List.of(node);
            } else {
                nodes = client.nodes().list().getItems();
            }

            if (nodes == null || nodes.isEmpty()) {
                return ToolResult.ofText("(no Kubernetes nodes found)");
            }

            StringBuilder sb = new StringBuilder();
            for (Node node : nodes) {
                String name = node.getMetadata() != null ? node.getMetadata().getName() : "unknown";
                NodeStatus status = node.getStatus();
                if (status == null) {
                    sb.append(String.format("Node: %s - Status: Unknown\n\n", name));
                    continue;
                }

                // Find Ready condition and Pressure conditions
                String ready = "Unknown";
                String diskPressure = "Unknown";
                String memPressure = "Unknown";
                String pidPressure = "Unknown";

                if (status.getConditions() != null) {
                    for (NodeCondition cond : status.getConditions()) {
                        if ("Ready".equalsIgnoreCase(cond.getType())) ready = cond.getStatus();
                        if ("DiskPressure".equalsIgnoreCase(cond.getType())) diskPressure = cond.getStatus();
                        if ("MemoryPressure".equalsIgnoreCase(cond.getType())) memPressure = cond.getStatus();
                        if ("PIDPressure".equalsIgnoreCase(cond.getType())) pidPressure = cond.getStatus();
                    }
                }

                Map<String, io.fabric8.kubernetes.api.model.Quantity> allocatable =
                        status.getAllocatable() != null ? status.getAllocatable() : Collections.emptyMap();
                String cpu = allocatable.containsKey("cpu") ? allocatable.get("cpu").getAmount() : "unknown";
                String memory = allocatable.containsKey("memory") ? allocatable.get("memory").getAmount() : "unknown";
                String pods = allocatable.containsKey("pods") ? allocatable.get("pods").getAmount() : "unknown";

                String kubelet = status.getNodeInfo() != null ? status.getNodeInfo().getKubeletVersion() : "unknown";
                String os = status.getNodeInfo() != null ? status.getNodeInfo().getOsImage() : "unknown";

                sb.append(String.format("Node: %s | Ready: %s\n", name, ready));
                sb.append(String.format("  Allocatable -> CPU: %s | Memory: %s | Max Pods: %s\n", cpu, memory, pods));
                sb.append(String.format("  Conditions  -> DiskPressure: %s | MemoryPressure: %s | PIDPressure: %s\n",
                        diskPressure, memPressure, pidPressure));
                sb.append(String.format("  Info        -> Kubelet: %s | OS: %s\n\n", kubelet, os));
            }

            return ToolResult.ofText(sb.toString().trim());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to get Kubernetes node status for %s", nodeName);
            return ToolResult.error("Failed to retrieve node status: " + e.getMessage());
        }
    }
}
