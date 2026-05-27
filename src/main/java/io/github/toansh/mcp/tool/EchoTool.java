package io.github.toansh.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EchoTool implements Tool {

    @Inject
    ObjectMapper mapper;

    @Override
    public String name() {
        return "echo";
    }

    @Override
    public String description() {
        return "Echoes the `message` argument back. Useful for verifying client connectivity.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode message = props.putObject("message");
        message.put("type", "string");
        message.put("description", "Text to echo back.");
        schema.putArray("required").add("message");
        return schema;
    }

    @Override
    public ToolResult call(JsonNode arguments) {
        JsonNode message = arguments == null ? null : arguments.get("message");
        if (message == null || !message.isTextual()) {
            return ToolResult.error("`message` argument is required and must be a string.");
        }
        return ToolResult.ofText(message.asText());
    }
}
