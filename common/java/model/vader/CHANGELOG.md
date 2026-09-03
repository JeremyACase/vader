# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0]
### Added
- `FileContentEntity` — JPA entity that holds raw uploaded file bytes (`@Lob byte[] data`).
  Kept separate from `ObjectMetadataEntity` so metadata queries never load binary content.
  Only populated by the database storage strategy; null in the MinIO path.
### Changed
- `ObjectMetadataEntity` gains an optional `@OneToOne(cascade=ALL, orphanRemoval=true)
  fileContent` relationship (`file_content_id` FK column); null when the MinIO strategy is
  active, since the content lives in the object store instead.
- `ClientPrompt.files` now carries `@Size(max=5, message="No more than 5 files may be
  attached to a single prompt")`.

## [0.2.0]
### Added
- `ClientPrompt` DTO and `ClientPromptEntity`, with a one-to-many relationship to
  `ObjectMetadataEntity` for file attachments (plus the corresponding optional back-reference).

## [0.1.0]
### Added
- Initial commit: `AbstractModel` / `AbstractModelEntity` base DTO and JPA entity pair, plus
  `ObjectMetadata` / `ObjectMetadataEntity` as a concrete example.
