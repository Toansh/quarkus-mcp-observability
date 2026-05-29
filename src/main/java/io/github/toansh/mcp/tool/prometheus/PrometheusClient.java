package io.github.toansh.mcp.tool.prometheus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Thin binding for the Prometheus HTTP API. Configured via
 * {@code quarkus.rest-client.prometheus-api.*} properties.
 */
@RegisterRestClient(configKey = "prometheus-api")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface PrometheusClient {

    @GET
    @Path("/query")
    PromResponse instantQuery(@QueryParam("query") String promql);
}
