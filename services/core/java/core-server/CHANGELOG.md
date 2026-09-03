# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
