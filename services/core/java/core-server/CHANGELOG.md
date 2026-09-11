# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
