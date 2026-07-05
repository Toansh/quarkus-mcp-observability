package io.github.toansh.mcp.tool.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolResult;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DescribeDeploymentTool implements Tool {

    private static final Logger LOG = Logger.getLogger(DescribeDeploymentTool.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    KubernetesClient client;

    @Override
    public String name() {
        return "describe_deployment";
    }

    @Override
    public String description() {
        return "Get detailed status and configuration for a Kubernetes deployment. "
                + "Returns the deployment spec and status as JSON.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        
        props.putObject("name").put("type", "string").put("description", "Name of the deployment.");
        props.putObject("namespace").put("type", "string").put("description", "Kubernetes namespace (defaults to client's default).");
        
        schema.putArray("required").add("name");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        String name = arguments.path("name").asText();
        if (name.isBlank()) {
            return ToolResult.error("`name` is required.");
        }
        
        String namespace = arguments.path("namespace").asText(client.getNamespace());

        try {
            Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(name).get();
            if (deployment == null) {
                return ToolResult.error("Deployment '" + name + "' not found in namespace '" + namespace + "'.");
            }
            return ToolResult.ofText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deployment));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to describe deployment %s in namespace %s", name, namespace);
            return ToolResult.error("Failed to retrieve deployment details: " + e.getMessage());
        }
    }
}
