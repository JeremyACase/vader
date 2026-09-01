# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This is a project-level summary. Individual modules under `common/` and `services/` keep
their own, more detailed changelogs.

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
