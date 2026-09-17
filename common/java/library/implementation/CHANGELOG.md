# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.13.0]
### Added
- Mapper for the new `TaskAttemptToolCall` model.

## [0.12.0]
### Added
- Mappers for the new `TaskAttempt` and `TaskAttemptTranscript` models.
### Changed
- The workflow mapper carries the new lifecycle status field.

## [0.10.0]
### Added
- A builder that turns a persisted entity into an ingress-response receipt carrying its id and
  model type.

## [0.9.0]
### Changed
- Task-plan mapping carries the new reasoning field in both directions (entity ↔ DTO).

## [0.6.0]
### Changed
- Clarified that file handling is the responsibility of the file storage strategy, not the
  client prompt mapper.

## [0.5.0]
### Added
- Initial module: Spring service beans that map between the shared model's JPA entities and DTOs
  in both directions — entity → DTO for API responses, DTO → entity for persistence. Shallow id
  references are used for association sides that would be expensive or circular to fully hydrate.
