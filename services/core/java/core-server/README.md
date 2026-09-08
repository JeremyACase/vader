# core-server

Minimal Spring Boot service for Vader's `core` layer. Establishes the base pattern that other
core services will follow: a `Config` bean that pins the JVM's default timezone to UTC on
startup.

## Running locally

```
../../../../gradlew :services:core:java:core-server:bootRun
```

## Endpoints

- `GET /actuator/health` — Spring Boot Actuator health check.
- `POST /vader/core-server/client-prompt` — accepts a `ClientPrompt` (multipart form: `text`
  plus optional `files`), asks the orchestrator LLM to decompose the problem, validates and
  persists the resulting task plan under a new workflow, and returns that `Workflow` as JSON.
  Returns `502` if the LLM's response fails the task-plan schema, `503` if the LLM is
  unreachable. Attached file contents are not yet persisted.

## Orchestration

`vader.orchestrator.type` picks the strategy (`@ConditionalOnProperty`):

- `static` — returns a fixed `StaticTaskPlan`, no LLM. Used by the Helm/CI tests.
- `local` — Spring AI `ChatClient` against an in-cluster Ollama. The client prompt is sent
  **with every registered tool** (each `ToolCallbackProvider` bean — currently the MCP operator
  tools) and the model may call them while decomposing; the final message is
  structured-output-converted to a `TaskPlan`. Needs a tool-capable model
  (`vader.orchestrator.local.model`, e.g. `qwen2.5:3b`) for tool calls to actually happen.
  - `vader.orchestrator.local.fallback-to-static` (default `true`): if the LLM is unreachable,
    return the `StaticTaskPlan` (logged `warn`) rather than a 503, so tests pass with no Ollama.

## Kubernetes operators

`tools/operators` holds a small operator framework (`InterfaceOperator` + `AbstractOperator`)
ported from ubiquia. Each operator manages a set of Kubernetes resources and is also exposed to
LLMs as MCP tools.

`vader.operators.enabled=true` is the master switch — it builds the shared Kubernetes client and
the RBAC Role/RoleBinding. Each operator then has its own flag; the first is the **Python sandbox
operator** (`vader.operators.python-sandbox.enabled=true`), which manages the lifecycle of
isolated `python:3.12-slim` sandbox pods. Both are off by default.

- MCP: `create_sandbox`, `list_sandboxes`, `delete_sandbox`, served over the Spring AI MCP SSE
  endpoint (`GET /sse`, `POST /mcp/message`) on this service's port.
- REST (used by the Helm smoke test and for debugging):
  - `POST /vader/core-server/python-sandbox/sandboxes` — body `{"name": "<optional>"}`.
  - `GET /vader/core-server/python-sandbox/sandboxes`.
  - `DELETE /vader/core-server/python-sandbox/sandboxes/{name}`.
  - `502` when the Kubernetes API call fails.

## Database query

The `common:java:library:dao` engine is wired over the six entities that have a DTO mapper —
`Workflow`, `ClientPrompt`, `TaskPlan`, `TaskGraph`, `Task`, `ObjectMetadata`
(`FileContentEntity` is never registered, so file bytes are unreachable).

- REST: `GET|POST /vader/core-server/data/{workflow|client-prompt|task-plan|task-graph|task|object-metadata}/query...`
  — `POST /query` takes a `QueryFilter` body, `GET /query/params` takes URL params
  (`?title*=%cake%`), `POST /query/count` returns `{"count": n}`, `GET /query/{id}` fetches one.
- MCP (`vader.mcp.database-query.enabled`, default true):
  - `list_queryable_entities` — the entities and their filterable fields (associations shown as
    `-> X`; reach them with dotted keychains like `taskPlan.objective`).
  - `query_database(entity, filter)` — a page of DTOs, or `{"error": ...}` for a bad field.
  - `count_matching(entity, filter)` — `{"count": n}`.
- `vader.dao.max-page-size` (default 100) caps every query's page size.
