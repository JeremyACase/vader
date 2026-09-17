# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.16.0]
### Added
- **Every inference strategy now offers the harness the full MCP tool inventory, on every task
  execution turn -- not just during initial problem decomposition.** Previously a dispatched task
  never had any tools available to it at all, so an agent could not retrieve an uploaded file or
  query the database while actually doing its work, only while the prompt was first being planned.
- **A harness can now actually invoke a tool the model asks for.** A new `/agent/tool-calls`
  endpoint executes one named tool on behalf of a running assignment and returns its result, going
  through the same tool registry (and the same per-call logging) an MCP client's call would. The
  `/agent/inference` endpoint now carries a full running conversation (including prior tool calls
  and their results) instead of a single one-shot prompt string, so a multi-turn agent loop has
  somewhere to fold tool results back in.
- **Every tool call a harness executes is now durably logged the moment it happens.** Previously a
  tool call's result only became visible in the database once folded into a later inference turn's
  transcript -- so a harness that crashed or was reaped right after invoking a tool left no trace
  of it at all. The new `TaskAttemptToolCall` record (queryable the same way as every other entity)
  is written before the result is returned to the caller, so the exchange survives regardless of
  what the harness does next.
- **A dispatched task now gets the background its own short description never carried.** Fetching
  a work order now also returns the original client-submitted request, any files attached to it,
  and the results of any prerequisite tasks it depends on. Previously a task like "ensure the
  report is well-structured" or "identify patterns in the data" had no way to discover what report
  or what data was meant, since the planner never restates that in every subtask -- an agent would
  ask a clarifying question instead of doing the work, and that question was then reported and
  accepted as though it were the finished result.
### Changed
- The local decomposition and inference strategies now share one registry of every registered MCP
  tool, instead of the decomposition step keeping its own private copy.
- The static (no-LLM) inference gateway now scripts its very first turn as a request to call
  `list_queryable_entities` instead of answering immediately, so `helm test` -- with no live LLM
  in the cluster -- deterministically exercises a real harness Job's full action loop, including
  a genuine `/agent/tool-calls` round trip, rather than only ever taking a single turn.

## [0.15.0]
### Added
- **Every MCP tool now logs when an agent actuates it.** Tool invocations were previously silent,
  making it impossible to tell whether an agent ever actually called a given tool (e.g. fetching
  an uploaded file's content before analyzing it) versus skipping it and hallucinating a result.
  Every tool exposed over MCP — the Python sandbox, database query, object storage, back
  pressure, and per-entity DAO tools alike — now logs its name and input on invocation and its
  output or failure on completion, applied uniformly in one place so future tools get the same
  logging automatically without any per-tool changes.

## [0.14.0]
### Added
- **Object storage retrieval.** A previously-uploaded file's content is now reachable regardless
  of whether it lives in MinIO or the database. A REST endpoint streams it back honoring HTTP
  Range requests — including genuine multi-range `multipart/byteranges` responses — via the same
  resource-serving engine Spring uses for static assets, so a large file can be fetched in parts
  across several requests instead of one large response. An MCP tool (`get_object_content`) lets
  agents fetch an object's content directly as base64, transparently to whichever storage
  strategy is active; objects above a configurable size are rejected with a pointer to the REST
  endpoint instead, so one tool result can't blow the calling model's context budget.
### Fixed
- **The local LLM inference gateway used by dispatched agent-harness tasks was completely
  broken.** Every task attempt failed immediately with "No CallAdvisors available to execute" —
  reading both the response text and its token usage off one chat call re-ran an
  already-consumed internal state in the AI client library — and was silently retried until it
  exhausted its attempts, so no task ever actually completed against a local Ollama instance.
  Task attempts now succeed normally.

## [0.13.0]
### Changed
- The Kubernetes operator subsystem, and the agent-harness operator specifically, are now
  enabled by default rather than opt-in — a normal deployment expects a reachable cluster, so
  running without one (test suites, CI) is now the case that opts out instead. A missing cluster
  no longer silently leaves every dispatched task failing with no obvious cause; it now shows up
  immediately as a logged operator-initialization error.

## [0.12.0]
### Added
- **Agent harness operator.** A new operator creates one Kubernetes Job per dispatched task
  attempt, owned by and cascade-deleted with the `core-server` Deployment. A task-graph
  scheduler advances each workflow as dependencies complete, dispatching ready tasks through a
  new task-assignment inbox/outbox pipeline.
- **Agent control plane and inference gateway endpoints.** A harness Job fetches its work order,
  reports heartbeats and its terminal outcome, and completes inference turns exclusively through
  new endpoints scoped to its own single-use assignment id; stale or duplicate reports against an
  already-settled assignment are rejected rather than allowed to overwrite a newer outcome.
- **Stuck-attempt reaping and retries.** A background reaper reclaims attempts that go silent
  past their deadline (crash, OOM, network partition, or a Job that never scheduled), and a
  failed/timed-out/stalled task is retried with a fresh attempt and Job up to a configurable cap
  before being permanently failed.
- Config knobs for harness turn/token/deadline budgets, retry limits, finished-Job TTL, and the
  reaper's poll interval and grace period, plus the task-assignment inbox's poll interval and
  dispatch concurrency.

## [0.10.0]
### Changed
- Client-prompt intake is now asynchronous. `POST /client-prompt` stores the prompt and returns
  a `202` receipt instead of blocking on the LLM and returning the workflow. A downstream inbox
  decomposes queued prompts into workflows — draining on a fixed interval and immediately after
  each new submission — and each message settles independently, so an unusable or unreachable
  LLM records a failed message rather than returning an HTTP error to the caller.
### Added
- A backpressure endpoint (REST and MCP) reporting queue depth, its rate of change, and
  in-flight capacity for any inbox/outbox queue, without exposing the individual messages.
- Config for the inbox poll interval and concurrency, the backpressure sample interval, a master
  switch for the background pollers, and a toggle for the backpressure MCP tools. The Helm notes
  document the backpressure and MCP endpoints on DEV installs.

## [0.9.0]
### Added
- Chain-of-thought decomposition: the planning prompt directs the model to reason about the
  problem before planning, and that reasoning is persisted on the task plan and returned with
  the workflow.
- Dedicated per-entity MCP tools (query, count, get-by-id) for every queryable entity,
  complementing the generic database-query tools.
### Changed
- Orchestrator exception types moved to a dedicated exceptions package shared across the
  service.

## [0.8.0]
### Added
- Database query over REST and MCP, using the `common:java:library:dao` engine. All six mapped
  entities are queryable; file content is excluded.
- OpenAPI / Swagger UI.
- Kubernetes operator framework and the Python Sandbox operator, exposed to LLMs as MCP tools
  and over REST.
### Changed
- Local orchestrator rewritten on Spring AI's ChatClient with native tool-calling, structured
  output, and a configurable fallback to the static plan when the LLM is unreachable.
- Field injection adopted uniformly across the service.

## [0.6.0]
### Added
- Pluggable file storage strategy (database or MinIO), with files stored in the same transaction
  as the prompt. Helm configures multipart limits.
### Changed
- The decomposition flow wires file storage in before persisting the prompt.

## [0.5.0]
### Added
- End-to-end problem decomposition: prompt → orchestrator → validate → persist → return workflow.
  Static orchestrator for deterministic Helm/CI tests. Orchestrator failures map to appropriate
  HTTP error codes.

## [0.4.0]
### Added
- Local LLM orchestration via Ollama.
### Changed
- Interfaces moved to an `interfaces` subpackage and named with the `Interface*` prefix.

## [0.3.0]
### Changed
- Database backend switched from HSQL to H2.

## [0.2.1]
### Changed
- Docker image renamed to `jeremyacase/vader-core-server`.

## [0.2.0]
### Added
- HSQL-backed persistence and a `ClientPromptController`.

## [0.1.0]
### Added
- Initial Spring Boot service with UTC default timezone.
