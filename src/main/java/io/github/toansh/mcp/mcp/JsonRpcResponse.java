package io.github.toansh.mcp.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
        @JsonProperty("jsonrpc") String jsonrpc,
        @JsonProperty("id") JsonNode id,
        @JsonProperty("result") Object result,
        @JsonProperty("error") Error error
) {

    public static JsonRpcResponse success(JsonNode id, Object result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse error(JsonNode id, int code, String message) {
        return new JsonRpcResponse("2.0", id, null, new Error(code, message, null));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int code, String message, Object data) {}
}
