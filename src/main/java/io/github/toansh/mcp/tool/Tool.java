package io.github.toansh.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface Tool {

    String name();

    String description();

    ObjectNode inputSchema();

    ToolResult call(JsonNode arguments);
}
