package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GetPodLogsTool implements Tool {

    private static final Logger LOG = Logger.getLogger(GetPodLogsTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    KubernetesClient client;

    @Override
    public String name() {
        return "get_pod_logs";
    }

    @Override
    public String description() {
        return "Retrieve logs for a specific pod in a Kubernetes cluster. "
                + "Read-only. Returns at most the last 1000 lines.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        
        props.putObject("podName").put("type", "string").put("description", "Name of the pod.");
        props.putObject("namespace").put("type", "string").put("description", "Kubernetes namespace (defaults to client's default).");
        props.putObject("containerName").put("type", "string").put("description", "Optional container name if pod has multiple containers.");
        props.putObject("tailLines").put("type", "integer").put("description", "Number of lines to retrieve from the end (max 1000, default 100).");
        
        schema.putArray("required").add("podName");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        String podName = arguments.path("podName").asText();
        if (podName.isBlank()) {
            return ToolResult.error("`podName` is required.");
        }
        
        String namespace = arguments.path("namespace").asText(client.getNamespace());
        String containerName = arguments.path("containerName").asText("");
        int tailLines = Math.min(arguments.path("tailLines").asInt(100), 1000);

        try {
            PodResource pod = client.pods().inNamespace(namespace).withName(podName);
            String logs;
            if (!containerName.isBlank()) {
                logs = pod.inContainer(containerName).tailingLines(tailLines).getLog();
            } else {
                logs = pod.tailingLines(tailLines).getLog();
            }
            return ToolResult.ofText(logs != null ? logs : "(no logs found)");
        } catch (Exception e) {
            LOG.warnf(e, "Failed to get logs for pod %s in namespace %s", podName, namespace);
            return ToolResult.error("Failed to retrieve logs: " + e.getMessage());
        }
    }
}
