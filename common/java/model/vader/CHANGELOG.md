# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.0]
### Added
- The task plan carries an optional free-text reasoning field alongside its objective, holding
  the model's chain-of-thought for the decomposition.

## [0.3.0]
### Added
- `FileContentEntity` to hold raw uploaded file bytes, intentionally separated from
  `ObjectMetadataEntity` so metadata queries never load binary content.
### Changed
- `ObjectMetadataEntity` gains an optional relationship to `FileContentEntity`; null when the
  MinIO storage strategy is active.
- `ClientPrompt` enforces a maximum of 5 file attachments.

## [0.2.0]
### Added
- `ClientPrompt` DTO and entity, with support for file attachments via `ObjectMetadataEntity`.

## [0.1.0]
### Added
- Initial base model and entity abstractions, and the first concrete model (`ObjectMetadata`).
