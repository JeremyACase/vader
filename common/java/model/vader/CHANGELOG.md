# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0]
### Added
- `ClientPrompt` DTO and `ClientPromptEntity`, with a one-to-many relationship to
  `ObjectMetadataEntity` for file attachments (plus the corresponding optional back-reference).

## [0.1.0]
### Added
- Initial commit: `AbstractModel` / `AbstractModelEntity` base DTO and JPA entity pair, plus
  `ObjectMetadata` / `ObjectMetadataEntity` as a concrete example.
