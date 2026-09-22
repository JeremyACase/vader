# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.17.0]
### Added
- `TaskUpdate` now records who authored it (system, task agent, orchestrator, or evaluator) and
  two new update types, `CREATED` and `RUNNING`, so a task's history starts the moment it's
  planned rather than only once something happens to it.

## [0.16.0]
### Added
- A queue-message entity backing the new attempt-review pipeline, plus two new local-LLM request
  kinds (evaluation, reattempt decision) sharing the existing durable request queue.
### Changed
- `TaskUpdate` now references the specific attempt it's about, not just the task.

## [0.15.0]
### Added
- New `TaskUpdate` model recording an evaluator/orchestrator verdict or progress note against a
  task; `Task` carries a shallow reference to its updates.

## [0.14.0]
### Added
- New `TaskAttemptToolCall` model, recording one tool call a model requested and had executed
  during a task attempt -- the tool, its arguments, and its result -- as its own immutable,
  durable audit record, written at the moment of execution rather than only ever appearing
  embedded in a later transcript entry.

## [0.13.0]
### Added
- `ObjectMetadata` now records the exact key an object was stored under when the MinIO strategy
  is active, so its content can be read back later instead of only ever being written. This is a
  storage-strategy implementation detail and is not exposed on the API-facing DTO.

## [0.12.0]
### Added
- `TaskAttempt` and `TaskAttemptTranscript` models, recording each dispatch of a task to a
  harness — its status, timing, and the turn-by-turn record of what happened — so a task's
  execution history survives retries and is queryable independent of the workflow.
- A queue-message entity backing the new task-assignment outbox, mirroring the existing
  inbox/outbox message shape.
### Changed
- `Workflow` gains lifecycle status tracking so a workflow's overall progress (and not just its
  task plan) can be queried directly.

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
