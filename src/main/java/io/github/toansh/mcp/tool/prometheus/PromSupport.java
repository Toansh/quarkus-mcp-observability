package io.github.toansh.mcp.tool.prometheus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.toansh.mcp.tool.ToolResult;
import jakarta.ws.rs.ProcessingException;

/**
 * Shared response/error handling for the Prometheus tools (instant + range). Keeps the two tools'
 * error vocabulary identical and the result formatting in one place, so a fix to either applies
 * to both.
 */
final class PromSupport {

    private PromSupport() {
    }

    static ToolResult httpError(int status) {
        return ToolResult.error("Prometheus returned HTTP " + status + ".");
    }

    static ToolResult timeoutError(ProcessingException e) {
        return ToolResult.error("Prometheus call failed: " + rootMessage(e)
                + ". The 5s timeout may have been exceeded — try a cheaper query.");
    }

    static ToolResult envelopeError(PromResponse response) {
        return ToolResult.error("Prometheus rejected the query (" + response.errorType()
                + "): " + response.error());
    }

    static String formatResult(PromResponse response, ObjectMapper mapper) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.data());
        } catch (JsonProcessingException e) {
            return "{\"resultType\":\"" + safe(response.data().resultType())
                    + "\",\"result\":\"<serialization-failed>\"}";
        }
    }

    static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
