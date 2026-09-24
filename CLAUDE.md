# Vader

An AI agent platform. The backend (`core-server`) decomposes natural-language prompts into
structured task plans via an LLM, manages Kubernetes sandbox operators, and exposes everything
over both REST and MCP (Spring AI). The frontend (`core-ui`) is an Angular app that submits
prompts and displays results.

## Module map

| Path | Purpose |
|---|---|
| `common/java/model/vader` | Shared JPA entities and DTOs |
| `common/java/library/implementation` | Entity → DTO mappers |
| `common/java/library/dao` | Dynamic JPA Criteria query engine + generic REST/MCP controller |
| `services/core/java/core-server` | Spring Boot service (orchestration, operators, storage, MCP) |
| `services/core/ts/core-ui` | Angular frontend |
| `services/core/rust/core-agent-harness` | Ephemeral k8s Job: executes exactly one task-graph subtask. All LLM calls are proxied through `core-server` — the harness never talks to an LLM directly. |
| `services/core/python/core-python-sandbox-server` | Runs inside a Python sandbox pod: a small FastAPI server executing agent-submitted code as a subprocess against a persistent per-pod workspace, reachable only from `core-server`. |

## Build and run

Aside from the JVM and the Java server code, no build environment needs to be installed on the
host. Angular, Rust, and Python builds/tests/lints each run inside a dockerized environment,
wired into and invoked by the top-level `./gradlew build` — a local `node`/`cargo`/`python`
install is only needed if you want to run that module's tooling directly, outside Gradle.

```bash
# Full build (all modules + Angular)
./gradlew build

# Run core-server locally (H2 in-memory DB)
./gradlew :services:core:java:core-server:bootRun

# Run tests for a single module
./gradlew :common:java:library:dao:test

# Angular dev server (from services/core/ts/core-ui)
npm start

# Agent harness: build/test/lint directly with cargo (also wired into ./gradlew build)
cd services/core/rust/core-agent-harness && cargo build

# Python sandbox exec server: lint/test run dockerized (also wired into ./gradlew build) --
# no host Python install required
./gradlew :services:core:python:core-python-sandbox-server:build
```

Checkstyle runs on every build. Config is in `common/java/checkstyle/`. The project follows
Google Java Style with a 100-character line limit.

Prefer `var` for local variable declarations and let the compiler infer the type. Only spell
out the type explicitly when inference is impossible or the inferred type would be genuinely
ambiguous to a reader.

Make full use of Spring dependency injection. Every Spring-managed collaborator (services,
repositories, mappers, templates, validators, etc.) must be injected via `@Autowired` — never
instantiated with `new` inside application code. Configuration values come in via `@Value`.
Prefer field injection (`@Autowired` on the field) consistent with the existing codebase style.

## Code conventions

### Cyclomatic complexity

Keep methods flat and linear. Prefer a single return at the bottom of a method; avoid early
returns except at clear guard-clause boundaries (null checks, preconditions) where they
eliminate nesting. Never use `continue` in loops — restructure the loop body instead.

Aim for a cyclomatic complexity of 1–3 per method. Extract private helpers rather than
nesting conditionals.

### Design patterns — use them and name them explicitly

When applying a pattern, encode the pattern name in the class name. Examples:

| Pattern | Naming example |
|---|---|
| Builder | `PythonSandboxManifestBuilder` |
| Strategy | `LocalLlmOrchestrationStrategy`, `StaticLlmOrchestrationStrategy` |
| Adapter | `WidgetStorageAdapter` |
| Saga | `WidgetSaga` |
| Inbox | `WidgetInbox` |
| Operator | `PythonSandboxOperator` |
| Registry | `VaderDaoRegistry` |
| Mapper | `TaskDtoMapper` |

Both classic GoF patterns and modern distributed-systems patterns (Saga, Inbox/Outbox, etc.)
are welcome. The goal is that a reader can see the pattern from the class name without reading
the implementation.

Abstract base classes are prefixed `Abstract` (`AbstractOperator`, `AbstractModelEntity`).
Interfaces are prefixed `Interface` (`InterfaceOperator`, `InterfaceLlmOrchestrationStrategy`).

### Model naming

Every persistent model has two representations:

- **Entity** (`WidgetEntity`) — the JPA entity in `common/java/model/vader/entity/`. Extends
  `AbstractModelEntity`.
- **DTO** (`Widget`, no suffix) — the API-facing data transfer object in
  `common/java/model/vader/dto/`. Extends `AbstractModel`.

Mappers live in `common/java/library/implementation` and follow the pattern
`WidgetDtoMapper implements InterfaceEntityToDtoMapper<WidgetEntity, Widget>`.

**Mappers must not hydrate the "many" side of relationships by default.** Because the DAO
layer returns dynamic, paginated results, eagerly mapping child collections would produce
uncapped in-memory loads for every row on a page. The default for any `@OneToMany` or
`@ManyToMany` association is to emit only the child ids (e.g. `setDependsOnTaskIds(...)`).
Full child hydration is only permitted when it is structurally load-bearing (the relationship
is the primary purpose of the DTO, as with `TaskGraph → tasks`) and must be called out
explicitly in a Javadoc comment on the mapper explaining why the exception is justified.

### Adding a new queryable entity

1. Create `WidgetEntity` and `Widget` in the model module.
2. Create `WidgetDtoMapper` in the implementation module.
3. Create `WidgetDaoController extends GenericVaderDaoController<WidgetEntity, Widget>` in
   `core-server`. This automatically provides REST endpoints (`GET/POST /query*`) and three
   MCP tools (`query_widget`, `count_widget`, `get_widget_by_id`).
4. Register the entity in `VaderDaoRegistry` if it needs to appear in `list_queryable_entities`.

### TypeScript (Angular)

The same complexity and pattern rules apply as Java. Additional specifics:

- **No `any`.** Use explicit types or generics everywhere. Prefer `unknown` over `any` at
  system boundaries and narrow with type guards.
- **Signals over subscriptions** for component state (Angular signals API). Reserve `Observable`
  for service boundaries and use `toSignal` to cross into component scope.
- **Model naming mirrors the Java side.** Plain interfaces represent DTOs (e.g., `Workflow`,
  `Task`). File name: `widget.model.ts`. Services: `widget.service.ts`. Components:
  `widget.component.ts` / `widget.component.html`.
- **Pattern names are explicit.** `WidgetService`, `WidgetAdapter`, `WidgetStrategy` — same
  convention as Java, adapted to camelCase file names.
- **Single return, no `continue`** — same cyclomatic complexity goal as Java.
- **Strict mode** must remain on (`"strict": true` in `tsconfig.json`).

### Python

`services/core/python/core-python-sandbox-server` is the first Python service and the reference
implementation for these conventions:

- **Full type hints** on every function signature and class attribute. Use `from __future__ import
  annotations` at the top of every module. Avoid `Any` from `typing` except at true system
  boundaries.
- **Separation of concerns** mirrors the Java structure: models (`widget.py`), services
  (`widget_service.py`), adapters, strategies, etc. One class per file is the default unless
  the classes are trivially small value objects.
- **Pattern naming** follows the same convention, snake-cased for files and PascalCase for
  classes: `WidgetService`, `WidgetAdapter`, `WidgetSaga`; files `widget_service.py`,
  `widget_adapter.py`.
- **Cyclomatic complexity** — same rules: single return per function (guard clauses at the top
  are fine), no `continue`, complexity target of 1–3. Use helper functions to flatten nesting.
- **PEP 8** for style. Use a linter (Ruff) and formatter (Black or Ruff format) consistent with
  whatever is configured for the service.
- **Dataclasses or Pydantic models** for structured data; plain `dict` only at true IO
  boundaries before parsing.

### Rust

Used for `core-agent-harness` (and any future latency/footprint-sensitive, single-purpose
service). The same complexity and pattern-naming philosophy as Java applies, adapted to
idiomatic Rust rather than transplanted literally:

- **Traits stand in for `Interface*`.** Rust's `trait` keyword already marks the role that
  Java's `Interface` prefix exists to signal, so trait names are **not** prefixed
  (`ControlPlane`, `InferenceGateway` — not `InterfaceControlPlane`). Concrete implementations
  still encode their pattern in the type name exactly like Java/Python/TS:
  `CoreServerAdapter`, `HarnessBudget`, `StallDetector`. There is no direct analog to
  `Abstract*` base classes — shared behavior is composition (a struct holding the shared state)
  or a trait default method, not inheritance.
- **Newtypes for identifiers.** Wrap ids in single-field structs (`TaskId(Uuid)`,
  `AssignmentId(Uuid)`) rather than passing bare `Uuid`/`String` — the compiler then rejects a
  task id accidentally passed where an assignment id is expected.
- **`Result<T, E>` and `?` are the guard-clause mechanism.** A function that can fail returns
  `Result`; propagate with `?` at the top of a function exactly like a Java guard-clause early
  return, then let the rest of the function run straight through to one value at the end.
  Never `.unwrap()`/`.expect()` outside tests or a case that is genuinely statically impossible
  (and comment why, at the call site, when it isn't obvious).
- **No `continue`, and prefer iterator adapters to hand-written loops.** `.filter()`, `.map()`,
  `.find()`, `.all()`, `.any()` usually eliminate the loop body branching entirely, which is a
  more idiomatic way to hit the complexity target than Java's single-return discipline. Where a
  loop is genuinely needed, keep the same rule: no `continue`, extract a helper instead of
  nesting.
- **Cyclomatic complexity** — same target as every other language here: 1–3 per function.
- **`rustfmt` and `clippy -D warnings`** are this language's equivalent of checkstyle and must
  be clean on every build.
- **`async fn` in traits (stable, no `async-trait` crate needed)** over generics
  (`fn run<C: ControlPlane, G: InferenceGateway>(...)`) rather than `dyn Trait` — this harness
  has a small, closed set of implementations (one real, one test-double), so static dispatch is
  simpler and keeps the binary small.
- **Models mirror the wire contract**, not a Java entity/DTO split — plain `#[derive(Serialize,
  Deserialize)]` structs matching whatever JSON `core-server` sends/accepts.

## Testing

### Philosophy

Every non-trivial behaviour needs a test. The two required layers are:

1. **Unit tests** — fast, no I/O, cover logic in isolation.
2. **System tests** — run against a live Helm release in Kubernetes and verify end-to-end
   behaviour (`helm test`).

### Java unit tests — use Instancio

Use [Instancio](https://www.instancio.org/) (`INSTANCIO_VERSION` in `gradle.properties`) to
generate valid, fully-populated dummy objects instead of constructing test data by hand.

```java
// Preferred — Instancio fills every field with valid random data
TaskEntity entity = Instancio.create(TaskEntity.class);

// Customise specific fields when the test cares about them
QueryFilter filter = Instancio.of(QueryFilter.class)
    .set(field(QueryFilter::getPage), 0)
    .set(field(QueryFilter::getPageSize), 10)
    .create();
```

Use `ReflectionTestUtils.setField` (already used in the codebase) to inject `@Autowired` fields
into units under test rather than standing up a full Spring context unless integration behaviour
is the point.

### TypeScript unit tests

Use Angular's built-in testing toolchain (Jasmine + `TestBed`) for component and service tests.
Services that depend on `HttpClient` should use `HttpClientTestingModule` and
`HttpTestingController`. Aim for the same coverage bar as the Java code.

### Python unit tests

Use `pytest` with full type-checking via `mypy` or `pyright`. Mirror the Instancio philosophy:
use factories or `faker`/`hypothesis` to generate valid dummy data rather than hardcoding
literals.

### Rust unit tests

Use `cargo test` with `#[cfg(test)]` modules colocated in each source file. Mirror the
Instancio/faker philosophy: prefer small hand-written builder functions (or the `fake` crate)
that produce valid dummy data over hardcoding literals in every test, so tests keep working as
fields are added.

### Helm system tests

System tests live in `deploy/helm/templates/services/test/system/`. Each test is a Kubernetes
`Pod` with annotation `helm.sh/hook: test`. They run with `helm test <release>` (or in CI).

Rules for Helm tests:
- Test the **deployed system**, not mocks. Every scenario in a Helm test must go over the
  network to a real running service.
- Cover the **happy path and key error paths** (e.g., validation rejection, non-2xx codes).
- Tests must be **self-contained** — create any data they need and make no assumptions about
  pre-existing cluster state.
- Use `restartPolicy: Never` and `helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded`.
- When a new service or significant endpoint is added, add (or extend) its test pod in
  `deploy/helm/templates/services/test/system/`.

## Changelogs

Every module has a `CHANGELOG.md`. Entries are grouped by semantic version and written as
**summaries** — one or a few sentences per version describing what changed and why at a
capability level. Do not list class names, package paths, annotation details, or method
signatures; those belong in git and are always recoverable with `git log` / `git diff`.

A good entry reads like a release note to a developer upgrading from the previous version.
A bad entry reads like an annotated file listing.

**Please summarize changes, don't enumerate them.** One version entry is one short paragraph (or
a couple of terse bullets at most) — not a bulleted list with bolded sub-headers, and not one
bullet per class/file touched. If a change needs several bullets to explain, that's a sign to
compress it further, not to add structure.

## Key runtime knobs (application.properties / env)

| Property | Default | Effect |
|---|---|---|
| `vader.mode` | `PROD` | `DEV`, `PROD`, or `TEST`. Canned results are permitted **only** in `TEST` (the devops test pipeline): `vader.orchestrator.type=static` is refused -- by the Helm chart at render time and by core-server at startup -- in any other mode. In every mode an unreachable local LLM fails loudly with `OrchestratorUnavailableException`; no `Local*Strategy` ever substitutes a canned result |
| `vader.orchestrator.type` | `local` (Helm) | `local` (Ollama) or `static` (canned results; requires `vader.mode=TEST`) |
| `vader.orchestrator.local.model` | — | Ollama model name |
| `spring.ai.ollama.chat.options.num-predict` (Helm: `vader.orchestrator.local.maxOutputTokens`) | `2048` (Helm) | Hard cap on tokens generated per response, for every Ollama call. Stops a small model's runaway repetition loop from generating forever and holding Ollama's single slot; tune per model |
| `spring.ai.ollama.chat.options.num-ctx` (Helm: `vader.orchestrator.local.contextTokens`) | `16384` (Helm) | Ollama's context window per call. The prompt budget is this minus `num-predict`; a longer prompt is silently cut from the front, so it must comfortably exceed `num-predict` (the chart refuses to render otherwise) |
| `vader.orchestrator.local.request-timeout-seconds` | `300` | Read timeout on every Ollama HTTP call (decomposition, inference turns, evaluation, reattempt decisions, synthesis) -- the JDK HTTP client Spring auto-detects has none of its own, so an unbounded call would otherwise wedge the single-worker `LlmRequestQueue` forever |
| `vader.orchestrator.max-task-plan-revisions` | `1` | How many times a freshly-decomposed `TaskPlan` may be sent back for revision (a structural problem -- dangling dependency, dependency cycle -- or the refinement critique flags it) before the last plan is used anyway rather than blocking the prompt indefinitely |
| `vader.operators.enabled` | `true` | Master switch for Kubernetes operators. Set to `false` where no cluster is reachable (unit/integration test runs, CI) |
| `vader.operators.python-sandbox.enabled` | `true` | Python sandbox operator |
| `vader.operators.python-sandbox.sandbox.exec-timeout-seconds` | `30` | Ceiling on one `run_python_code` call, enforced by `core-python-sandbox-server` itself regardless of what a caller requests |
| `vader.operators.python-sandbox.sandbox.ready-poll-max-attempts` | `30` | How many times `create_sandbox` re-checks pod readiness before giving up and returning whatever phase it last saw |
| `vader.operators.python-sandbox.sandbox.ready-poll-interval-ms` | `500` | Delay between those readiness checks -- together with the attempt cap, the total wait budget `create_sandbox` blocks for so a caller staging a file or running code immediately after doesn't race the pod's own startup |
| `vader.mcp.database-query.enabled` | `true` | Expose DB query tools over MCP |
| `vader.mcp.backpressure.enabled` | `false` | Expose inbox/outbox backpressure tools over MCP -- an ops-debugging surface, not something task-execution agents need |
| `vader.storage.type` | `database` | `database` or `minio`; picks the `InterfaceFileStorageStrategy` backing both upload and the object-storage download endpoint/tool |
| `vader.mcp.object-storage.enabled` | `true` | Expose `get_object_content` (base64 object retrieval) over MCP |
| `vader.mcp.object-storage.max-inline-bytes` | `2097152` | Objects over this size are rejected by `get_object_content` with the REST download URL instead of being inlined |
| `vader.mcp.task-update.enabled` | `true` | Expose `post_task_update` over MCP -- a task-execution agent leaving an interim progress note against its own task; never a pass/fail/timeout verdict, and never against another task regardless of what the calling model supplies |
| `vader.dao.max-page-size` | `100` | Cap on query page size |
| `vader.kubernetes.namespace` | `default` | Namespace operators manage resources in |
| `vader.scheduling.enabled` | `true` | Master switch for the background pollers (inbox drain, backpressure sampler) |
| `vader.inbox.client-prompt.poll-interval-ms` | `1000` | Scheduled client-prompt inbox drain cadence |
| `vader.inbox.client-prompt.max-concurrency` | `1` | Max concurrent prompt decompositions; also `egress.maxOpenMessages` |
| `vader.backpressure.sample-interval-ms` | `15000` | Queue-depth sampling cadence feeding `queueRatePerMinute` |
| `vader.operators.agent-harness.enabled` | `true` | Agent-harness operator (creates one Job per dispatched `TaskAttempt`) |
| `vader.inbox.task-assignment.poll-interval-ms` | `1000` | Scheduled task-assignment inbox drain cadence |
| `vader.inbox.task-assignment.max-concurrency` | `5` | Max assignments *dispatched* concurrently — throttles Job creation, not how many harness Jobs may run at once |
| `vader.agent-harness.max-turns` | `20` | Turn cap handed to each harness, enforced server-side |
| `vader.agent-harness.max-tokens` | `200000` | Token cap handed to each harness, enforced server-side |
| `vader.agent-harness.deadline-seconds` | `600` | Wall-clock deadline handed to each harness; also the Job's `activeDeadlineSeconds` |
| `vader.agent-harness.max-attempts-per-task` | `3` | Hard ceiling on attempts per task, checked by `OrchestratorAgentService.decideReattempt` before it even consults the reattempt-decision strategy |
| `vader.inbox.task-attempt-review.poll-interval-ms` | `1000` | Scheduled attempt-review inbox drain cadence -- evaluates a newly-terminal attempt and, on failure, asks the orchestrator whether it's worth re-attempting |
| `vader.inbox.task-attempt-review.max-concurrency` | `1` | Max attempts reviewed concurrently; kept low since both review steps' LLM calls are already serialized through the single-worker `LlmRequestQueue` |
| `vader.inbox.task-attempt-review.llm-retry-interval-ms` | `30000` | How often a review that couldn't reach the LLM (unreachable, or the LLM queue timed out) is retried. It retries for as long as the outage lasts; meanwhile its workflow reads `AWAITING_LLM`, returning to `RUNNING` once the LLM answers. Any other review failure is not retried |
| `vader.agent-harness.ttl-seconds-after-finished` | `3600` | Backstop only: `AgentHarnessJobCleanupListener` deletes a finished Job as soon as its `TaskAttempt` settles, regardless of this value; Kubernetes only reaches this TTL if that explicit delete didn't run |
| `vader.agent-harness.reaper.poll-interval-ms` | `30000` | How often to scan for attempts a harness will never report back on |
| `vader.agent-harness.reaper.grace-period-seconds` | `300` | Extra silence allowed past `deadline-seconds` (scheduling/image-pull + final round trip) before an attempt is reaped as `TIMED_OUT` |
| `vader.llm.request-queue.drain-poll-interval-ms` | `200` | `LlmRequestInbox`'s scheduled drain cadence -- the only path by which a replica other than the enqueuer (or one already mid-request) notices a freed claim slot, so this directly adds to every blocked caller's wait |
| `vader.llm.request-queue.result-poll-interval-ms` | `100` | How often a caller blocked in `LlmRequestQueue` re-checks whether its own request has been processed |
| `vader.llm.request-queue.stall-timeout-seconds` | `60` | The real patience control: a caller gives up once no request on the queue, anywhere, by any replica, has been claimed for this long. A deep-but-healthy queue does not trip this -- only a genuinely stuck backend does |
| `vader.llm.request-queue.max-wait-seconds` | `1800` | Absolute backstop regardless of stall detection, so a queue that never stalls but also never keeps up cannot block a caller forever |
