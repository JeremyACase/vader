# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.8.0]
### Added
- OpenAPI / Swagger UI (`springdoc-openapi-starter-webmvc-ui`) — the spec is at
  `/v3/api-docs` and the UI at `/swagger-ui.html`, documenting every REST controller. Gated on
  `vader.swagger.enabled` (default true); the Helm `NOTES.txt` now points at Swagger instead of
  `/actuator/health`.
- `controller.dao` + `tools.query` — the vader database is now queryable via REST and MCP,
  built on the new `common:java:library:dao` engine:
  - Six `GenericVaderDaoController` subclasses at `/vader/core-server/data/{entity}` for
    `Workflow`, `ClientPrompt`, `TaskPlan`, `TaskGraph`, `Task`, `ObjectMetadata` (each reuses
    the existing `*DtoMapper`; `FileContentEntity` is deliberately not exposed).
  - `tools.query.VaderDaoRegistry` indexes those controllers by DTO name;
    `DatabaseQueryService` adds schema reflection (`describe()` → filterable fields, skipping
    `@Transient` / `modelType`) and trims pages to a lean `QueryResult`.
  - `tools.query.mcp.DatabaseQueryTools` — `@Tool` methods `list_queryable_entities`,
    `query_database`, `count_matching`, registered with the Spring AI MCP server. Gated on
    `vader.mcp.database-query.enabled` (default true); bad field names come back as
    `{"error": ...}` for the model to correct.
  - `commons-lang3` added for field reflection.
- `tools.operators` — a Kubernetes operator framework ported and de-duplicated from ubiquia's
  `BeliefStateOperator` / `ComponentOperator`:
  - `tools.operators.interfaces.InterfaceOperator<S>` — the `Interface*` strategy contract:
    `init` / `teardown` / `reconcile` / `delete` / `deleteAll` / `list`.
  - `tools.operators.AbstractOperator<S>` — non-Spring base class. Caches the owning Deployment
    on startup with a bounded retry loop, opens a fabric8 watch on that Deployment and
    cascade-runs `deleteAll()` on its deletion, stamps `operators.vader.org/{managed-by,operator}`
    labels + an owner reference on every manifest, and scopes list/delete to the operator label.
  - `tools.operators.ManagedResource`, `OperatorLabels`, `OperatorProperties`.
  - `tools.operators.KubernetesClientConfig` — fabric8 `KubernetesClient` bean, created only
    when the operator subsystem is on (`@ConditionalOnProperty vader.operators.enabled`); each
    operator additionally has its own `vader.operators.<name>.enabled` flag.
- `tools.operators.pythonsandbox` — the first concrete operator (lifecycle only):
  - `PythonSandboxOperator` (`@ConditionalOnProperty vader.operators.python-sandbox.enabled`)
    extends `AbstractOperator`; `PythonSandboxManifestBuilder` builds a hardened idling
    `python:3.12-slim` Deployment (`sleep infinity`, non-root, caps dropped, no SA token) +
    ClusterIP Service; `SandboxNaming` resolves an optional friendly name to a unique DNS-1123
    label; `PythonSandboxOperatorInitializer` drives `init()` on `ApplicationReadyEvent`
    (failure logged, non-fatal).
  - `PythonSandboxService` — shared domain layer over the operator.
  - `mcp.PythonSandboxTools` + `mcp.PythonSandboxToolsConfig` — `@Tool` methods
    (`create_sandbox`, `list_sandboxes`, `delete_sandbox`) registered with the Spring AI MCP
    server via a `ToolCallbackProvider` bean.
  - `controller.PythonSandboxController` — REST surface at
    `/vader/core-server/python-sandbox/sandboxes`; `KubernetesClientException` → 502.
- `spring-ai-starter-mcp-server-webmvc` (Spring AI BOM `1.0.9`) — SSE MCP server on the
  existing web port; `io.fabric8:kubernetes-client:7.8.0`;
  `io.fabric8:kubernetes-server-mock` (test).
- Helm: MCP server + `vader.kubernetes` + `vader.operators` blocks in the configmap.
### Changed
- `orchestrator.LocalLlmOrchestrationStrategy` — rewritten on Spring AI `ChatClient`
  (`spring-ai-starter-model-ollama`) instead of a hand-rolled `RestTemplate` call to Ollama
  `/api/generate`. It now sends the prompt **with every registered `ToolCallbackProvider`'s
  tools** and lets the model call them (agentic loop), then structured-output-converts the
  final message to a lean `LlmTaskPlan` (new — `objective` + `tasks[{title, description}]`
  only), which it maps to a full `TaskPlan` and re-serializes to JSON. The
  `InterfaceLlmOrchestrationStrategy` `String` contract and `WorkflowService`'s validation gate
  are unchanged. Converting straight to `TaskPlan` made the generated schema ask the model for
  an `id` (UUID-patterned), timestamps and a recursive sub-task tree, which small local models
  filled with nulls → schema-violation 502s. Dropped the embedded task-plan JSON schema,
  `DECOMPOSITION_INSTRUCTIONS` JSON wording, and the `RestTemplate` fields.
  - New `vader.orchestrator.local.fallback-to-static` (default `true`): an unreachable LLM
    (`ResourceAccessException` / `TransientAiException`) yields the canned plan + a `warn` log
    instead of a 503. A reachable LLM returning junk still throws `OrchestratorResponseException`
    (502).
- `orchestrator.StaticTaskPlan` (new) — the canned 4-task plan, extracted from
  `StaticLlmOrchestrationStrategy` so `LocalLlm` can share it as the fallback.
- Dependency injection style: beans now use `@Autowired` / `@Value` field injection rather than
  constructor injection, matching the `common:java:library:implementation` mappers. Converted
  `ClientPromptController`, `WorkflowService`, `MinioFileStorageStrategy`, the orchestrator and
  every operator class; `AbstractOperator` holds the shared `@Autowired KubernetesClient` /
  `@Value` namespace and exposes `ownerDeploymentName()` (was `OperatorProperties`, now
  deleted). `LocalLlmOrchestrationStrategy` builds its `ChatClient` in a `@PostConstruct`.
  Unit tests use Mockito `@InjectMocks` + `ReflectionTestUtils.setField`.
- Config: `spring.ai.model.chat` = `ollama` for `type: local`, else `none` (no chat model in
  static / test contexts); `spring.ai.ollama.*`, bounded `spring.ai.retry`, and
  `spring.http.client` timeouts emitted by the configmap; `vader.orchestrator.local.base-url`
  removed (superseded by `spring.ai.ollama.base-url`).

## [0.6.0]
### Added
- `storage.interfaces.InterfaceFileStorageStrategy` — mirrors the orchestrator strategy
  pattern; the active implementation is selected via
  `@ConditionalOnProperty(prefix="vader.storage", name="type")`.
- `storage.DatabaseFileStorageStrategy` (`matchIfMissing=true`, active by default) — reads
  each `MultipartFile`, creates a `FileContentEntity` for the raw bytes, links it to an
  `ObjectMetadataEntity`, and returns the metadata. No extra infrastructure required.
- `storage.MinioFileStorageStrategy` (active when `vader.storage.type=minio`) — uploads each
  file to MinIO under a UUID-prefixed object name, creates the target bucket on first use if
  absent, and returns only the `ObjectMetadataEntity` (no `FileContentEntity`).
- `storage.MinioConfig` — `@Configuration` / `@ConditionalOnProperty` that constructs the
  `MinioClient` bean from `vader.storage.minio.*` properties; kept separate from the strategy
  so the client is injectable in tests without a running MinIO.
- `storage.FileStorageException` — unchecked; thrown by either strategy on I/O or transport
  failure; mapped to HTTP 500 (`file_storage_failed`) by the controller.
- `io.minio:minio:8.5.17` implementation dependency.
### Changed
- `WorkflowService.decompose()` gains a `List<MultipartFile> files` parameter; calls
  `InterfaceFileStorageStrategy.store()` and attaches the results to the prompt entity before
  `clientPromptRepository.save()`, so the JPA cascade persists files in the same transaction.
- `ClientPromptController`: passes `clientPrompt.getFiles()` through to `WorkflowService`;
  new `@ExceptionHandler` for `BindException` (→ 400 `validation_failed`) and
  `FileStorageException` (→ 500 `file_storage_failed`).
- Helm ConfigMap emits `spring.servlet.multipart` (10 MB/file, 51 MB/request, 1 MB threshold)
  and `vader.storage` blocks; limits are driven by `vader.multipart.*` Helm values with
  in-template defaults.
- Helm test: added multipart upload and 6-file rejection cases; test pod annotated with its
  `test.yaml` (static orchestrator) dependency; prompt test `--max-time` reduced 60 s → 10 s.

## [0.5.0]
### Added
- End-to-end problem decomposition. A submitted client prompt is now routed
  UI -> `core-server` -> orchestrator LLM, decomposed into a task plan, validated, persisted,
  and returned:
  - `service.WorkflowService` orchestrates the flow: ask the orchestrator, parse the response,
    validate it against the `TaskPlan` schema (jakarta bean validation) *before* any write, then
    persist the task plan / task graph / tasks as one graph under a new `WorkflowEntity` with the
    plan linked back to that workflow. A malformed response leaves the database untouched.
  - `ClientPromptController` now calls `WorkflowService` and returns the resulting `Workflow`
    (with its task plan) as JSON instead of just logging.
  - `repository`: `ClientPromptRepository`, `WorkflowRepository`, `TaskPlanRepository`.
- `orchestrator.OrchestratorResponseException` -> HTTP `502` (`orchestrator_response_invalid`):
  the LLM answered but the answer was missing, unparseable, or off-schema.
- `orchestrator.OrchestratorUnavailableException` -> HTTP `503` (`orchestrator_unavailable`):
  the LLM could not be reached (still warming up); the caller may retry.
- `orchestrator.StaticLlmOrchestrationStrategy`, active when `vader.orchestrator.type=static`:
  returns a fixed, schema-valid task plan with no LLM call, so the `test` Helm configuration
  runs a deterministic end-to-end check with no Ollama deployment.
- Tests: `WorkflowService` schema-rejection unit tests; `@SpringBootTest` integration tests for
  the persist-and-associate happy path, invalid-schema rollback, and the controller's HTTP
  contract (`MockMvc`, 200 + 502); `StaticLlmOrchestrationStrategy` unit test.
- `@EntityScan` for the shared entity package; component scan widened to `org.vader.common.library`.
### Changed
- `LocalLlmOrchestrationStrategy` wraps the prompt with decomposition instructions and pins
  Ollama's structured-output `format` to the task-plan JSON schema, so the model returns a single
  JSON object. Added connect (10s) and read (4m) timeouts; transport failures now surface as
  `OrchestratorUnavailableException`.
- The `core-server` Helm test hook drives a full prompt -> core-server -> decomposition round
  trip and asserts a `200` with an objective and tasks. It runs against the `test` config's
  static orchestrator, so it is fast and deterministic (no model download).

## [0.4.0]
### Added
- `orchestrator.interfaces.InterfaceLlmOrchestrationStrategy` and its `local` implementation,
  `orchestrator.LocalLlmOrchestrationStrategy`, which coordinates RESTful traffic to/from a
  local Ollama instance. Registered only when `vader.orchestrator.type=local`, via
  `@ConditionalOnProperty`.
- Unit tests for `LocalLlmOrchestrationStrategy`, covering the missing-model failure case, the
  Ollama `/api/generate` request/response shape, and a null-body response.
### Changed
- Interfaces now live under an `interfaces` subpackage and are named with an `Interface` prefix
  (e.g. `InterfaceLlmOrchestrationStrategy`) -- the convention to follow going forward.

## [0.3.0]
### Changed
- Database backend switched from HSQL to H2. Datasource/JPA config, the `hsqldb` runtime
  dependency, and the `Application` javadoc were all updated accordingly. H2's built-in
  browser console is now enabled at `/h2-console` via `spring.h2.console`.

## [0.2.1]
### Changed
- Docker image renamed to `jeremyacase/vader-core-server`.

## [0.2.0]
### Added
- HSQL-backed persistence, wired through Helm-configurable datasource settings.
- `ClientPromptController`, with a unit test.
### Fixed
- Dockerfile base-image argument now has a sensible default.

## [0.1.0]
### Added
- Initial commit: minimal Spring Boot server with a UTC-initialized default timezone.
