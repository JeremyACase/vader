# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.10.0]
### Added
- An ingress-response receipt model: an endpoint that accepts work for asynchronous processing
  hands back the id and type of what it persisted rather than the finished result.
- Backpressure models describing how backed up a queue is — records waiting, the rate that
  backlog is changing, and how much consumer capacity is free.
- Queue-message entities backing an inbox/outbox: a message references its payload and carries a
  status (pending → claimed → processed / failed) with claim and completion timestamps. Client
  prompts get a concrete message type; the base is reusable for other payloads.

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
