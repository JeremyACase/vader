# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This is a project-level summary. Individual modules under `common/` and `services/` keep
their own, more detailed changelogs.

## [0.6.0]
### Added
- File storage strategy pattern (`vader.storage.type`), selected at startup via
  `@ConditionalOnProperty` — the same approach as the orchestrator strategy:
  - `database` (default, no extra infrastructure) — stores uploaded file bytes as BLOBs in
    the relational database via a new `FileContentEntity`, linked to `ObjectMetadataEntity`
    by a nullable FK.
  - `minio` — uploads to a MinIO object store and persists only the storage metadata;
    `FileContentEntity` is not populated in this path.
- `FileContentEntity` in `common:java:model:vader` — holds raw file bytes, kept separate from
  `ObjectMetadataEntity` so metadata queries never load binary content.
- `ObjectMetadataEntity` gains an optional `@OneToOne fileContent` FK (`file_content_id`),
  cascade all, orphan removal; null in the MinIO path.
- Server-side file count guard: `ClientPrompt.files` is annotated `@Size(max=5)`; the
  controller maps the resulting `BindException` to HTTP 400 (`validation_failed`).
- `core-ui`: file selection capped at 5 — selecting more clears the batch and shows an inline
  error. Selected filenames are listed below the input, and the native file input is reset
  after a successful submit.
- `spring.servlet.multipart` limits injected via Helm: 10 MB per file, 51 MB per request,
  1 MB memory threshold before buffering to disk. Configurable via `vader.multipart.*` values.
- Helm test: multipart upload case (prompt + one file → 200 + decomposition) and a 6-file
  rejection case (→ 400 `validation_failed`). Prompt test timeouts tightened from 60 s to
  10 s now that tests run against the static orchestrator.
### Changed
- `WorkflowService.decompose()` accepts `List<MultipartFile>` as a second parameter; files are
  stored via the active strategy before the prompt is persisted so the JPA cascade handles both
  in one transaction.

## [0.5.0]
### Added
- End-to-end problem decomposition. A client prompt now flows UI -> `core-server` -> orchestrator
  LLM: `core-server` asks the orchestrator to decompose the prompt, validates the response
  against the `TaskPlan` schema (jakarta bean validation) before persisting anything, saves the
  task plan / task graph / tasks under a new `Workflow`, and returns that `Workflow` as JSON.
  `core-ui` renders the returned objective and task list.
- `common:java:library:implementation`: a new module of entity<->DTO mappers (egress and
  ingress), used by `core-server` to build the persisted graph and shape the HTTP response.
- `vader.orchestrator.type=static`: a `StaticLlmOrchestrationStrategy` that returns a fixed,
  schema-valid decomposition with no LLM call. The `test` Helm configuration uses it, so the
  end-to-end Helm test runs deterministically with no Ollama deployment or model download.
- `core-server` maps orchestrator failures to HTTP status: `502` when the LLM response is
  unusable, `503` when the LLM is unreachable.
### Changed
- `LocalLlmOrchestrationStrategy` now instructs the model to decompose the problem and pins
  Ollama's structured-output `format` to the task-plan JSON schema; added request timeouts.
- The `core-server` Helm test hook drives a full prompt -> decomposition round trip instead of
  only checking that the endpoint returns `200`.

## [0.4.0]
### Added
- A `local` LLM orchestrator option (`vader.orchestrator.type`, defaulted to `local`). When set,
  the Helm chart installs an Ollama deployment/service, and `core-server` gets a
  `LocalLlmOrchestrationStrategy` bean (registered only via `@ConditionalOnProperty`) that
  coordinates REST calls to it.
- `vader.orchestrator.local.model`, the Ollama model to download and run. The Helm chart injects
  it into the Ollama container and pulls/loads it via a `postStart` lifecycle hook once the
  server is ready. Model *selection* logic in `core-server` is left for a follow-up feature.
- `core-server`: unit tests for `LocalLlmOrchestrationStrategy`.
### Changed
- `core-server`'s orchestration strategy interfaces now live under an `orchestrator/interfaces`
  package, prefixed `Interface*` (e.g. `InterfaceLlmOrchestrationStrategy`) -- the convention to
  follow for future interfaces.
### Fixed
- The `dev` Helm config overlay pinned `imagePullPolicy` to `Never` chart-wide, assuming every
  image was built and loaded locally. That broke the new Ollama deployment, which is always
  pulled from Docker Hub -- Ollama's pull policy is now tracked separately from `core-server`/
  `core-ui`'s, and the `dev` overlay itself switched to `IfNotPresent`.

## [0.3.0]
### Added
- `core-ui`: a new Angular application, built and packaged with the Gradle `node-gradle` plugin
  and a multi-stage Docker build, deployed as a first-class core component with its own Helm
  templates. It submits a client prompt (text plus optional file attachments) to `core-server`,
  proxied through NGINX.
- A dynamic Helm `NOTES.txt` that explains how to reach `core-server`'s health endpoint, browse
  the embedded H2 database, and access `core-ui` directly via NodePort (with a `kind`-specific
  port-mapping snippet and a `kubectl port-forward` fallback).
### Changed
- `core-server`'s database backend switched from HSQL to H2. HSQL's embedded in-memory database
  has no browser-based console; H2 provides one (`/h2-console`) with no other infrastructure
  changes needed.
### Fixed
- `core-ui`'s NGINX container crash-looped under its non-root user because nginx defaulted to
  writing `/run/nginx.pid`, which that user can't write to. Pinned the pid file to the
  already-writable `/tmp/nginx` directory instead.

## [0.2.1]
### Changed
- Docker images now publish under the `jeremyacase` Docker Hub namespace, with `core-server`
  named `vader-core-server`.

## [0.2.0]
### Added
- `core-server`: HSQL-backed persistence, a `ClientPrompt` model/controller, and supporting
  Helm templates and tests.
- `common:java:model:vader`: base model/entity pattern plus `ClientPrompt`/`ClientPromptEntity`.
### Changed
- CI now builds and Docker-packages `core-server`, gated on systems tests before publishing.
- Cleaned up dead configuration (unused Gradle properties, stray references, broken script
  paths) across the build and deployment tooling.

## [0.1.0]
### Added
- Initial project scaffolding: repo/folder structure, Gradle build files, GitHub Actions
  pipeline, local KIND install scripts, and Helm chart skeleton.
- `core-server`: minimal Spring Boot service with a UTC-initialized default timezone.
