# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
