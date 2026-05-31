# quarkus-mcp-observability

> Production-grade MCP server in Quarkus — safely expose your observability stack to AI assistants.

**Status:** Active development (May 2026). MCP dispatcher, bearer-token auth, Postgres audit log, and the first bounded tool (`query_prometheus`) are in `main`, covered by an integration test suite that runs against a real Postgres (Testcontainers). CI lands next.

---

## What this is

A [Model Context Protocol](https://modelcontextprotocol.io) server, written in Java/Quarkus, that lets AI assistants (Claude, Cursor, custom agents) safely query a production observability stack — Prometheus metrics, Kubernetes logs, deployment status — without giving them write access or unbounded query power.

## Why

By mid-2026, MCP is becoming the standard way for AI assistants to reach into engineering tooling. Most existing implementations are in Python or TypeScript. Java/Quarkus shops — of which there are many — need a production-grade reference.

When an on-call engineer asks Claude *"why is order-service slow right now?"*, the AI should be able to **look at the actual environment** rather than guess from training data. This server makes that safe.

## The agentic-safe API design problem

Exposing tools to an AI is not the same as exposing them to a logged-in human. The AI may act on prompt-injected instructions, may run tools in unexpected sequences, and is not accountable for impact. This server is built around three rules:

1. **Read-only by default.** All v1 tools are queries. No state-mutation surface.
2. **Bounded by construction.** Every tool enforces query timeout, result size limit, and per-client rate limit.
3. **Fully audited.** Every tool call writes a Postgres audit row (caller, tool, arguments, result size, latency, status). Audit is not optional.

## Architecture

```
  AI client (Claude / Cursor / IDE)
           │  MCP protocol (JSON-RPC over Streamable HTTP)
           ▼
  ┌───────────────────────────────────┐
  │   Quarkus MCP Server              │
  │   ──────────────────────────────  │
  │   API key auth                    │
  │   Per-client rate limiting        │
  │   Audit log → Postgres            │
  │   Tool registry (SPI)             │
  │      ├── PrometheusTool           │
  │      ├── K8sLogsTool              │
  │      └── K8sStatusTool            │
  │   Micrometer / Prometheus metrics │
  └─────────┬─────────────────────────┘
            │
            ▼
  Prometheus   │   Kubernetes API
```

## Tools (v1)

| Tool | Inputs | Output | Safety bound | Status |
|------|--------|--------|--------------|--------|
| `query_prometheus` | `promql` (string) | Prometheus instant-query result (`vector` / `scalar` / `string` / `matrix`) | 5s read timeout, ≤1000 result series | shipped |
| `query_prometheus_range` | `promql`, `start`, `end`, `step` | range result (`matrix`) | 5s read timeout, points-per-series cap | follow-up |
| `get_pod_logs` | `namespace`, `pod`, `lines` (≤500) | log lines | hard cap 500 lines, ≤30s window | planned |
| `describe_deployment` | `namespace`, `name` | replicas, status, last rollout | metadata only, no spec dump | planned |

**On the cap:** `query_prometheus` rejects a result with more than the configured `prometheus.tool.max-series` (default 1000) rather than truncating. Silent truncation lets an AI confidently act on partial data; an explicit error tells it to narrow the query with stricter label matchers or an aggregation. Configure via `prometheus.tool.max-series`.

## Authentication

Every call to `/mcp` requires a bearer token:

```
Authorization: Bearer mcp_<random>
```

Tokens are random 32-byte secrets prefixed with `mcp_` (GitHub-style, so secret scanners can spot them in leaked diffs). The server only stores the **SHA-256 hash** of the token — the raw value never lives on disk. A token's `principal` becomes the `caller` recorded in every audit row, so a leaked or misused key is traceable to a single identity.

`/q/health`, `/q/metrics`, `/q/openapi`, and `/q/swagger-ui` are left open for ops tooling.

**Dev mode** (`mvn quarkus:dev`) auto-seeds a known key on first startup and prints the token to the log so the demo path works out-of-the-box. **Prod operators** insert keys via SQL — no auto-seed, no admin endpoint:

```sql
INSERT INTO api_keys (key_hash, principal, label, created_at, revoked)
VALUES (encode(sha256('mcp_…'::bytea), 'hex'), 'ci-runner', 'github-actions', now(), false);
```

## Live demo

The compose stack ships a Prometheus that scrapes **both itself and this server** (`monitoring/prometheus.yml`), so `query_prometheus` has real data to hit out of the box. End to end:

```bash
docker compose up -d          # Postgres + Prometheus
mvn quarkus:dev               # seeds a known dev API key and prints it
#   ... Seeded dev API key. Authenticate with: Authorization: Bearer mcp_dev_local_do_not_use_in_prod
```

**Auth is enforced** — no token, no entry:

```console
$ curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/mcp \
       -H 'Content-Type: application/json' \
       -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
401
```

**Call the tool over MCP** (`tools/call`). Prometheus is scraping itself *and* this server, so `up` comes back with both targets healthy:

```console
$ curl -s -X POST localhost:8080/mcp \
       -H 'Authorization: Bearer mcp_dev_local_do_not_use_in_prod' \
       -H 'Content-Type: application/json' \
       -d '{"jsonrpc":"2.0","id":2,"method":"tools/call",
            "params":{"name":"query_prometheus","arguments":{"promql":"up"}}}'
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [{ "type": "text", "text": "{
      \"resultType\": \"vector\",
      \"result\": [
        { \"metric\": {\"__name__\":\"up\",\"job\":\"quarkus-mcp\",\"instance\":\"host.docker.internal:8080\"}, \"value\": [t, \"1\"] },
        { \"metric\": {\"__name__\":\"up\",\"job\":\"prometheus\",\"instance\":\"localhost:9090\"},      \"value\": [t, \"1\"] }
      ]
    }" }],
    "isError": false
  }
}
```

**Every call is audited.** That one invocation wrote a row attributed to the token's principal:

```console
$ docker exec mcp-postgres psql -U mcp -d mcp \
       -c "SELECT caller, tool, status, latency_ms, result_size, args
           FROM audit_log ORDER BY id DESC LIMIT 1;"
  caller   |       tool       | status | latency_ms | result_size |       args
-----------+------------------+--------+------------+-------------+------------------
 dev-local | query_prometheus | OK     |        248 |         526 | {"promql": "up"}
```

`caller` is the `principal` bound to the bearer key — not a guess — so a misused key traces to one identity. `latency_ms` and `result_size` ride along on every audit row, giving per-call cost/latency visibility for free.

## Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Framework | Quarkus 3.x | Fast startup, fits the MCP server profile, daily stack |
| Language | Java 21 | LTS, virtual threads for blocking calls to K8s / Prometheus |
| Transport | JSON-RPC over Streamable HTTP | MCP spec; works with Claude Desktop, Cursor, IDE clients |
| Audit store | PostgreSQL via Quarkus Panache | Durable, queryable; logs-only audit fails compliance reviews |
| Tests | JUnit + Quarkus Test + Testcontainers | Integration tests hit real Postgres, not mocks |
| Observability | Micrometer → Prometheus | Eat the dogfood: this server is itself observable via the same stack it queries |
| CI | GitHub Actions | Build, test, container image push |
| Deploy | Docker + minimal K8s manifests | Cattle, not pets |

## Roadmap

**Shipped**
- [x] Quarkus 3.35 / Java 21 scaffold with REST, Panache, Flyway, Micrometer, OpenAPI
- [x] Postgres audit log (Flyway-managed schema, JSONB args, indexed by caller and time)
- [x] JSON-RPC 2.0 dispatcher + Tool SPI (`initialize`, `tools/list`, `tools/call`)
- [x] Bearer-token auth on `/mcp` (SHA-256 hashed, `principal`-attributed audit rows)
- [x] First bounded tool: `query_prometheus` (instant)
- [x] JUnit + Quarkus Test + Testcontainers — auth 401 paths, dispatcher round-trip, tool cap enforcement
- [x] OpenAPI polish: `bearer-key` security scheme so Swagger-UI "Try it out" works with a bearer token

**Next**
- [ ] `query_prometheus_range`, then K8s tools (`get_pod_logs`, `describe_deployment`)
- [ ] Per-client rate limiting
- [ ] Docker image + GitHub Actions CI

**Stretch**
- [ ] Quarkus native image build
- [ ] Helm chart
- [ ] OAuth2 / OIDC instead of static API keys

## Non-goals (v1)

- Write-mutating tools (kubectl apply, etc.) — explicitly out of scope for safety
- Multi-tenant SaaS deployment — single-org, self-hosted only
- Cross-cluster federation
- A web UI — this is a server, not a console

---

Designed and built by [Ansh Taneja](https://github.com/Toansh).