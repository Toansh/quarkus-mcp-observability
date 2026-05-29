package io.github.toansh.mcp.mcp;

import io.github.toansh.mcp.auth.Caller;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/mcp")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class McpResource {

    @Inject
    McpDispatcher dispatcher;

    @Inject
    Caller caller;

    @POST
    @Transactional
    public JsonRpcResponse handle(JsonRpcRequest request) {
        return dispatcher.dispatch(request, caller.getPrincipal());
    }
}
