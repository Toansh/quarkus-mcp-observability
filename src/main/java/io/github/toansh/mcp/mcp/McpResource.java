package io.github.toansh.mcp.mcp;

import io.github.toansh.mcp.auth.Caller;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/mcp")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "MCP", description = "Model Context Protocol endpoint. Single POST handler dispatching "
        + "initialize / tools/list / tools/call by JSON-RPC method.")
@SecurityRequirement(name = "bearer-key")
public class McpResource {

    @Inject
    McpDispatcher dispatcher;

    @Inject
    Caller caller;

    @POST
    @Transactional
    @Operation(
            summary = "Dispatch a JSON-RPC 2.0 MCP request",
            description = "Accepts initialize, tools/list, and tools/call. Tool invocations are "
                    + "audited synchronously in the same transaction as the response."
    )
    public JsonRpcResponse handle(JsonRpcRequest request) {
        return dispatcher.dispatch(request, caller.getPrincipal());
    }
}
