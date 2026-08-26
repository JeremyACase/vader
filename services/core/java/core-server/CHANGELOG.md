# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0]
### Added
- HSQL-backed persistence, wired through Helm-configurable datasource settings.
- `ClientPromptController`, with a unit test.
### Fixed
- Dockerfile base-image argument now has a sensible default.

## [0.1.0]
### Added
- Initial commit: minimal Spring Boot server with a UTC-initialized default timezone.
