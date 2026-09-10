# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
