# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.26.0]
### Changed
- Every inference turn now records the model's finish reason in its transcript and relays it to
  the harness, so a reply cut off at the output token cap can be failed with a clear reason
  instead of surfacing as an empty or garbled turn. The Helm chart's default local model is now
  `qwen2.5:7b`: `qwen2.5:3b` proved too unreliable at tool calling for task agents.
- A turn in which the model called tools now shows those tool calls in its transcript; Ollama
  reports such turns with empty-string rather than missing content, and they were being
  recorded as blank responses.
- The task agent's `run_python_code` now takes only the code to run. Its optional file-map
  parameter made Ollama silently discard the model's otherwise valid tool calls, leaving every
  turn empty, and the model also misused it in a way that would have overwritten attached files.
- Ollama now gets an explicit context window (Helm `vader.orchestrator.local.contextTokens`,
  default 16384) instead of its 4096 default, which after reserving the output cap left only ~2k
  tokens of prompt and silently cut longer prompts -- decomposition among them -- from the front.

## [0.25.1]
### Fixed
- The re-attempt decision now sees only updates from earlier attempts. It had also been shown the
  failed attempt's own verdict as a "prior update", which a small model read as the same failure
  repeating and so declined to retry tasks that had failed only once.

## [0.25.0]
### Changed
- Task plans now come out with real dependencies. The planner names the earlier tasks a task
  depends on by title instead of by array index, which a small model had been leaving empty for
  every task even in obviously sequential plans. The plan critique, now shown a readable titled
  task list rather than raw JSON, reports the dependencies it finds missing as structured data,
  and they are added to the plan directly -- only edges between real tasks that would not create a
  cycle -- instead of sending the whole plan back to be regenerated with the same omission.
  Re-planning is now reserved for problems that actually need it.

## [0.24.0]
### Changed
- A workflow no longer hangs in `RUNNING` when reviewing a finished attempt can't reach the LLM.
  The review is retried at a configurable interval
  (`vader.inbox.task-attempt-review.llm-retry-interval-ms`, default 30s) for as long as the outage
  lasts, and the workflow reads the new `AWAITING_LLM` status meanwhile -- with one task update
  recording why -- returning to `RUNNING` on its own once the LLM answers. Only genuine outages
  (unreachable, or the LLM queue timing out) are retried; a review that fails for any other
  reason still fails as before rather than retrying a deterministic failure forever.

## [0.23.0]
### Fixed
- Every Ollama response is now capped at a Helm-configurable number of tokens
  (`vader.orchestrator.local.maxOutputTokens`, default 2048), so a small model stuck in a
  repetition loop stops instead of generating forever and holding Ollama's only slot. A request
  queued right after a long-running LLM call no longer fails instantly as "stalled": the stall
  window now starts no earlier than the caller's own wait. A task-plan revision now passes the
  reviewer's critique to the model as separate instructions rather than appending it to the
  user's request, which had led the model to copy the critique into its new plan's reasoning.
  Agents are no longer offered `get_object_content` for attached files, only `run_python_code`,
  and a failed inference call now reports its underlying cause instead of always claiming the LLM
  was unreachable.

## [0.22.0]
### Changed
- Canned results are now permitted only in a devops test pipeline. A new `vader.mode` value,
  `TEST`, is the only mode in which `vader.orchestrator.type=static` may run -- the Helm chart
  refuses to render and core-server refuses to start with it in `DEV` or `PROD` -- and the test
  configuration now sets it. The `DEV`-mode fallbacks that silently answered with the static
  "birthday party" plan, a trusting evaluation, a default retry, or a default plan approval
  whenever Ollama was unreachable are gone: an unreachable LLM now fails loudly in every mode.

## [0.21.0]
### Changed
- Each task attempt now owns one Python sandbox that the server manages end to end: it is created
  lazily on the attempt's first `run_python_code` call, every file attached to the request is
  staged into it (re-staged if missing) before each run, and it is deleted when the attempt
  settles. Task agents now get a `run_python_code(code)` that takes no sandbox name, and are told
  attached files are already in their working directory, instead of having to create, name and
  stage into a sandbox themselves -- which small models reliably got wrong by inventing sandbox
  names. The ad-hoc sandbox tools stay available over MCP for ops use, with their code-running tool
  renamed to `run_python_code_in_sandbox`. Sandbox workspaces now survive container restarts, and a
  failed sandbox call names its root cause instead of ending in `null`.

## [0.20.0]
### Added
- Tasks now get a `CREATED` update the moment a workflow is decomposed (author: system), and a
  `RUNNING` update the moment a harness first makes contact (author: task agent), so a task's
  history no longer starts blank and only accumulates entries once something happens to it.

## [0.19.2]
### Fixed
- Every Ollama HTTP call now has a bounded read timeout (`vader.orchestrator.local.request-timeout-seconds`,
  default 5 minutes). The JDK HTTP client Spring auto-detects had no timeout of its own, so a
  single slow or hung generation could wedge the entire single-worker LLM queue permanently --
  every other request (including a workflow's very first task) would eventually report the
  backend as unreachable, even though it never actually cleared on its own; recovery required an
  operator to manually restart the Ollama pod. Confirmed via a live thread dump before fixing.

## [0.19.1]
### Fixed
- The Python sandbox tools' identifying parameter is now consistently named `sandboxName` across
  `run_python_code` and `delete_sandbox` (matching `stage_object`), reducing a small model's odds
  of swapping which argument holds the code versus the sandbox name. A malformed sandbox name also
  now fails with a short, clear error instead of an unbounded one echoing the offending value's
  full content.

## [0.19.0]
### Added
- An Evaluator Agent now independently judges each task's outcome instead of trusting the
  harness's self-report, and the Orchestrator decides whether a failure is worth retrying instead
  of blindly retrying to the attempt cap. Both run on their own durable review pipeline, off the
  scheduler's own thread, and record their verdicts and reasoning as `TaskUpdate`s.

## [0.18.0]
### Added
- A task-execution agent can now leave an interim progress note against its own task via the new
  `post_task_update` MCP tool -- never a pass/fail verdict, and never against another task
  regardless of what the calling model supplies.

## [0.17.0]
### Added
- `TaskUpdate` is now queryable the same way as every other entity, laying the groundwork for an
  evaluator agent to record a pass/fail/timeout verdict (or an interim note) against a task, and
  for the orchestrator to read that history back when deciding whether a failed task is worth
  re-attempting.

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
