package io.github.toansh.mcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toansh.mcp.audit.AuditLog;
import io.github.toansh.mcp.tool.Tool;
import io.github.toansh.mcp.tool.ToolRegistry;
import io.github.toansh.mcp.tool.ToolResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class McpDispatcher {

    static final String PROTOCOL_VERSION = "2024-11-05";
    static final String SERVER_NAME = "quarkus-mcp-observability";
    static final String SERVER_VERSION = "1.0.0-SNAPSHOT";

    @Inject
    ToolRegistry tools;

    @Inject
    ObjectMapper mapper;

    public JsonRpcResponse dispatch(JsonRpcRequest req, String caller) {
        if (req == null || req.method() == null) {
            return JsonRpcResponse.error(req == null ? null : req.id(), -32600, "Invalid request: method is required");
        }
        return switch (req.method()) {
            case "initialize" -> JsonRpcResponse.success(req.id(), initializeResult());
            case "tools/list" -> JsonRpcResponse.success(req.id(), toolsListResult());
            case "tools/call" -> callTool(req, caller);
            default -> JsonRpcResponse.error(req.id(), -32601, "Method not found: " + req.method());
        };
    }

    private Map<String, Object> initializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION));
        return result;
    }

    private ObjectNode toolsListResult() {
        ArrayNode arr = mapper.createArrayNode();
        for (Tool t : tools.all()) {
            ObjectNode item = mapper.createObjectNode();
            item.put("name", t.name());
            item.put("description", t.description());
            item.set("inputSchema", t.inputSchema());
            arr.add(item);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", arr);
        return result;
    }

    private JsonRpcResponse callTool(JsonRpcRequest req, String caller) {
        JsonNode params = req.params();
        if (params == null || !params.hasNonNull("name")) {
            return JsonRpcResponse.error(req.id(), -32602, "Invalid params: `name` is required");
        }
        String toolName = params.get("name").asText();
        JsonNode arguments = params.hasNonNull("arguments") ? params.get("arguments") : mapper.createObjectNode();

        Instant start = Instant.now();
        Tool tool = tools.get(toolName).orElse(null);
        if (tool == null) {
            writeAudit(start, caller, toolName, arguments, null, "UNKNOWN_TOOL");
            return JsonRpcResponse.error(req.id(), -32601, "Unknown tool: " + toolName);
        }

        ToolResult result;
        try {
            result = tool.call(arguments);
        } catch (RuntimeException e) {
            writeAudit(start, caller, toolName, arguments, null, "EXCEPTION");
            return JsonRpcResponse.error(req.id(), -32603, "Internal error: " + e.getMessage());
        }

        String status = result.isError() ? "ERROR" : "OK";
        writeAudit(start, caller, toolName, arguments, estimateSize(result), status);
        return JsonRpcResponse.success(req.id(), result);
    }

    private Long estimateSize(ToolResult result) {
        try {
            return (long) mapper.writeValueAsBytes(result).length;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeAudit(Instant start, String caller, String tool, JsonNode args, Long resultSize, String status) {
        AuditLog row = new AuditLog();
        row.createdAt = start;
        row.caller = caller;
        row.tool = tool;
        row.args = jsonNodeToMap(args);
        row.resultSize = resultSize;
        row.latencyMs = Duration.between(start, Instant.now()).toMillis();
        row.status = status;
        row.persist();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return mapper.convertValue(node, Map.class);
    }
}
