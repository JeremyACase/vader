# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0]
### Changed
- `ClientPromptDtoToEntityMapper` javadoc updated: file attachments are no longer described as
  dropped here; they are handled by `WorkflowService` via the active
  `InterfaceFileStorageStrategy` before the entity is persisted.

## [0.5.0]
### Added
- Initial module: mappers between the `common:java:model:vader` DTOs and JPA entities, as
  Spring `@Service` beans.
- Entity -> DTO (egress): `InterfaceEntityToDtoMapper` and the `GenericDtoMapper` base (identity
  and audit fields, list/set overloads), plus `ObjectMetadataDtoMapper`, `ClientPromptDtoMapper`,
  `TaskDtoMapper`, `TaskGraphDtoMapper`, `TaskPlanDtoMapper`, `WorkflowDtoMapper`. Parent and
  dependency references are emitted as shallow ids; `ClientPrompt.files` are not reconstructed.
- DTO -> entity (ingress): `InterfaceDtoToEntityMapper`, plus `ClientPromptDtoToEntityMapper`,
  `TaskGraphDtoToEntityMapper` (roots attach to the graph, subtasks nest under their parent,
  `dependsOnTaskIds` resolved against DTO-supplied ids), and `TaskPlanDtoToEntityMapper` (wires
  the plan/graph owning side so one `save` cascades the whole decomposition).
- Unit tests for every mapper.
