# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.15.0]
### Added
- **Task agents can analyze attached files with Python.** Each task attempt gets its own managed
  sandbox, created on first use with the prompt's attachments already in it and cleaned up when
  the attempt settles; a bare expression on the last line is echoed back the way a notebook shows
  it.
- **Every finished attempt is independently judged, and failures can be retried.** An evaluator
  decides whether an attempt actually did its task, and a failed one is re-attempted, up to a
  configurable cap, when the orchestrator judges another try worthwhile. Each task keeps a full
  authored history of these verdicts and of agents' own progress notes.
- **Better task plans.** A decomposed plan is checked for structural problems and critiqued, with
  missing dependencies added directly, before any task runs.
- **A reworked UI**: an interactive task-graph view of the selected workflow, a navigation rail,
  per-task update history, and full agent transcripts showing why each turn ended.
### Changed
- **The local LLM defaults to `qwen2.5:7b` with a 16k context window.** The 3b model's tool calls
  were too unreliable for task agents, and Ollama's own default context silently cut longer
  prompts. Output length, context size, and per-call timeouts are all configurable in the chart.
- **An LLM outage pauses a workflow instead of hanging it.** Affected workflows show
  `AWAITING_LLM` and resume on their own once the LLM answers again.
- **Canned (static) results are refused outside `vader.mode=TEST`**, by the chart at render time
  and by core-server at startup.
### Fixed
- **Agent runs that reported success with nothing done, or stalled for no visible reason.** Empty
  replies, replies cut off at the output cap, and tool calls Ollama silently dropped are now all
  handled explicitly, and transcripts show the model's tool calls instead of blank responses. A
  task that failed once is no longer mistaken for a repeating failure and left without a retry.
- **`./gradlew build` now runs the Angular unit tests**, in a container, so a failing spec fails
  the build.

## [0.14.0]
### Added
- **Object storage retrieval.** A file attached to a prompt can now be read back, not just
  written: a REST endpoint downloads it (with HTTP Range support for fetching large files in
  parts) and a new MCP tool (`get_object_content`, `vader.mcp.object-storage.enabled`, default
  on) lets agents fetch its content directly, transparently to whether the deployment is backed
  by MinIO or the database.
### Fixed
- **Real task execution against a local Ollama instance was silently broken.** Every
  agent-harness task dispatched under the `local` orchestrator failed immediately and retried
  until it exhausted its attempts, due to a Spring AI usage bug in the inference gateway. Task
  attempts now complete successfully.

## [0.13.0]
### Added
- **Immediate workflow feedback in the UI.** Submitting a prompt now shows a placeholder in the
  workflows panel the instant it is accepted, instead of leaving the panel empty until
  decomposition finishes and the next poll picks it up. Only one prompt may be in flight from the
  submit form at a time, so the panel always has an unambiguous placeholder to show and swap out
  once the real workflow comes back.
### Fixed
- **The agent-harness operator is enabled by default in the Helm chart again.** A stale chart
  default had it turned off regardless of `core-server`'s own default, so every dispatched task
  attempt silently went nowhere until the reaper eventually timed it out. Deployed workflows now
  actually execute.

## [0.12.0]
### Added
- **Agent harness operator — end-to-end task execution.** `core-server` now dispatches
  task-graph subtasks to real, ephemeral `core-agent-harness` Jobs and runs them to completion.
  A scheduler advances each workflow's task graph as dependencies complete and hands ready tasks
  to a new inbox/outbox pipeline; a Kubernetes operator turns each dispatch into a harness Job
  (owned by, and cascade-deleted with, the `core-server` Deployment); the harness fetches its
  work order, performs one inference turn against `core-server`'s new `/agent/*` control-plane
  and inference endpoints, and reports heartbeats and a terminal outcome (succeeded, failed,
  timed out, or stalled) back over the network — `core-server` remains the harness's only path
  to any LLM. A reaper periodically reclaims attempts that go silent (crash, OOM, network
  partition, or a Job that never scheduled), and failed/timed-out/stalled attempts are retried
  up to a configurable cap before the task is permanently failed. Helm now deploys and RBACs the
  operator, builds/publishes the harness image, and runs a new system test that exercises a real
  dispatch through to completion; CI's system-test timeout was extended to accommodate it.
### Changed
- `Workflow` and its task attempts now carry richer lifecycle state (status, transcript) so the
  scheduler and reaper have enough information to drive and recover a run.

## [0.11.0]
### Added
- **`core-agent-harness` scaffold.** A new Rust subproject (`services/core/rust/core-agent-harness`)
  for the agent harness: an ephemeral k8s Job that will execute exactly one task-graph subtask,
  identified by a `TaskId`/`AssignmentId` pair, with `core-server` as its only dependency —
  both the sole control plane and the sole path to any LLM. `HarnessBudget` (turn/token/deadline
  caps) and `StallDetector` (repeated-action detection) are implemented and unit-tested; the
  `core-server` HTTP calls and the turn loop itself are stubbed pending the corresponding
  `core-server` endpoints (tracked for a follow-up branch). Wired into `./gradlew build` and the
  devops Docker build/push pipeline. Every `cargo` invocation, in both Gradle and the new
  `tools/scripts/devs/agent-core-harness-dockerized-build.sh` helper script, runs inside Docker
  rather than against a host toolchain, so the module builds without a local Rust install.
- **Rust added to `CLAUDE.md`'s language conventions**, alongside Java/TypeScript/Python: the
  same complexity and pattern-naming philosophy, adapted to idiomatic Rust (traits stand in for
  `Interface*` without the prefix, `Result`/`?` is the guard-clause mechanism, `clippy -D
  warnings` is the checkstyle equivalent).

## [0.10.1]
### Fixed
- **`helm test` no longer races the `core-server` rollout.** CI ran `helm install` without
  `--wait`, so `helm test` fired while `vader-core-server` was still starting (its `startupProbe`
  alone delays readiness ~40s+) and the single-shot health-check curl reported `000`. The
  install step now uses `--wait --timeout 5m`, and the `core-server` test hook retries the
  `/actuator/health` check (up to 60 attempts, 5s apart) instead of failing on the first miss.

## [0.10.0]
### Changed
- **Asynchronous prompt intake (inbox/outbox).** Submitting a prompt no longer blocks on the
  LLM. `core-server` persists the prompt, returns a lightweight receipt, and a background inbox
  decomposes it into a workflow — draining on a schedule and reacting immediately to each new
  submission. A decomposition that fails (an unusable or unreachable LLM) is recorded as a
  failed queue message rather than surfacing as an HTTP error. The UI shows a "queued" state
  and polls for the result, indicating how far back in the queue the request sits while it
  waits.
### Added
- **Queue backpressure.** A new endpoint reports how backed up an inbox/outbox queue is — how
  many records are waiting, how fast that backlog is moving, and how much consumer capacity is
  free — over both REST and MCP. Individual queue messages stay private. DEV Helm installs point
  the notes at this endpoint and at the MCP endpoint.

## [0.9.0]
### Added
- **Chain-of-thought planning.** The local orchestrator now prompts the model to reason
  through the problem before committing to a plan — restating the goal, naming constraints and
  unknowns, and deciding whether its tools would help. That reasoning is captured on the task
  plan, persisted with the workflow, and returned to the client for display.
- **Per-entity database query tools over MCP.** Alongside the generic query surface, every
  queryable entity now exposes its own query, count, and get-by-id MCP tools so agents can work
  at the entity level without discovering entity names first.
### Changed
- Orchestrator failure types now live in a shared exceptions package so code outside the
  orchestrator can depend on them without reaching into it.

## [0.8.0]
### Added
- **Kubernetes operator framework + Python Sandbox operator.** A reusable operator base handles
  Deployment ownership, resource labelling, cascade cleanup, and self-deletion watching. The first
  operator manages isolated Python sandbox pods in-cluster and exposes them to LLMs as MCP tools,
  with a matching REST surface for debugging and smoke testing.
- **Database query over REST and MCP.** All registered entities are queryable by LLMs via MCP
  tools and by developers via a REST API, using a dynamic query engine that supports filtering,
  sorting, pagination, and association joins. File content is excluded.
- **OpenAPI / Swagger UI** for `core-server`.
### Changed
- **Local orchestrator rewritten on Spring AI.** The LLM client moved from a hand-rolled REST
  call to Spring AI's ChatClient, enabling native tool-calling during decomposition and structured
  output. An unreachable LLM falls back to the static plan rather than returning a 503.
- Field injection (`@Autowired` / `@Value`) adopted uniformly across `core-server`.

## [0.6.0]
### Added
- **Pluggable file storage** (database default, MinIO alternative). Files submitted with a prompt
  are stored and linked to the prompt in the same transaction. Server-side validation rejects more
  than 5 files per prompt. Helm configures multipart size limits.

## [0.5.0]
### Added
- **End-to-end problem decomposition.** A submitted prompt flows UI → `core-server` → LLM,
  producing a structured task plan that is validated, persisted as a workflow, and returned to the
  client. A static orchestrator provides deterministic results with no LLM required, used by
  Helm/CI tests.

## [0.4.0]
### Added
- Local LLM orchestration via Ollama, selected at startup by configuration.
### Changed
- Established the `Interface*` naming convention for all interfaces.

## [0.3.0]
### Added
- `core-ui`: Angular frontend that submits prompts and displays results.
### Changed
- `core-server` database backend switched from HSQL to H2.

## [0.2.1]
### Changed
- Docker images published under the `jeremyacase` Docker Hub namespace.

## [0.2.0]
### Added
- Database persistence and a `ClientPrompt` endpoint in `core-server`.
### Changed
- CI builds and Docker-packages `core-server`, gated on system tests before publishing.

## [0.1.0]
### Added
- Initial project scaffolding: repo structure, Gradle builds, GitHub Actions pipeline, local
  Kind scripts, and Helm chart skeleton. Minimal Spring Boot `core-server`.
