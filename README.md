# quarkus-mcp-observability

> Production-grade MCP server in Quarkus — safely expose your observability stack to AI assistants.

**Status:** Design phase (May 2026). Implementation begins week of Jun 2.

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

| Tool | Inputs | Output | Safety bound |
|------|--------|--------|--------------|
| `query_prometheus` | `promql` (string) | metric series | 5s timeout, ≤1000 points |
| `get_pod_logs` | `namespace`, `pod`, `lines` (≤500) | log lines | hard cap 500 lines, ≤30s window |
| `describe_deployment` | `namespace`, `name` | replicas, status, last rollout | metadata only, no spec dump |

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

- [ ] **Week of Jun 2** — Scaffold, MCP protocol handler, `query_prometheus`, API-key auth, first tests
- [ ] **Week of Jun 9** — K8s tools, Postgres audit log, Docker, GitHub Actions CI, Micrometer metrics, README polish
- [ ] **Stretch** — Quarkus native image build, Helm chart, OAuth2 instead of API key

## Non-goals (v1)

- Write-mutating tools (kubectl apply, etc.) — explicitly out of scope for safety
- Multi-tenant SaaS deployment — single-org, self-hosted only
- Cross-cluster federation
- A web UI — this is a server, not a console

---

Designed and built by [Ansh Taneja](https://github.com/Toansh).