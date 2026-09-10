# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.0]
### Changed
- The generic controller's entity-page-to-DTO-page conversion is now open to subclasses and
  collaborators, so services can build per-entity tooling on top of it.

## [0.8.0]
### Added
- Initial module: a dynamic JPA Criteria query engine (ported from ubiquia) that builds
  paginated, filtered, sorted queries at runtime from a structured filter or URL parameters,
  without hand-written repositories. Includes a generic abstract REST controller that subclasses
  inherit to expose read-only query endpoints automatically, and which also auto-registers MCP
  tools per entity via `ToolCallbackProvider`.
