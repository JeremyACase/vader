# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This is a project-level summary. Individual modules under `common/` and `services/` keep
their own, more detailed changelogs.

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
  pipeline, local KIND install scripts, and Helm chart skeleton, mirroring the established
  Ubiquia pattern.
- `core-server`: minimal Spring Boot service with a UTC-initialized default timezone.
