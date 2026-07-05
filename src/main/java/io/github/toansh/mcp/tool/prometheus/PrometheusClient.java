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
 *
 * <p>The full path lives on each method (no class-level {@code @Path}): the Quarkus REST server
 * also scans annotated interfaces, and a shared class path with two {@code @GET} methods collides
 * in its duplicate-endpoint check. Per-method paths keep them distinct and the client unaffected.
 */
@RegisterRestClient(configKey = "prometheus-api")
@Produces(MediaType.APPLICATION_JSON)
public interface PrometheusClient {

    @GET
    @Path("/api/v1/query")
    PromResponse instantQuery(@QueryParam("query") String promql);

    @GET
    @Path("/api/v1/query_range")
    PromResponse rangeQuery(@QueryParam("query") String promql,
                            @QueryParam("start") String start,
                            @QueryParam("end") String end,
                            @QueryParam("step") String step);

    @GET
    @Path("/api/v1/alerts")
    AlertsResponse alerts();
}
