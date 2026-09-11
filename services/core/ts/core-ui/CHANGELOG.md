# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.10.0]
### Changed
- Prompt submission is asynchronous now: after the server accepts a prompt the UI shows a
  "queued" state, polls for the workflow, and renders the decomposition once it is ready. While
  waiting it shows how many prompts are ahead in the queue and how fast it is moving.

## [0.9.0]
### Added
- The task-plan model carries the optional reasoning field now returned by `core-server`.

## [0.5.0]
### Changed
- The decomposition returned by `core-server` is now rendered — the objective and task list —
  rather than just a submission confirmation.

## [0.3.0]
### Added
- Initial Angular application: submits client prompts (text and optional file attachments) to
  `core-server` and displays results. Built via Gradle, packaged as a Docker image, and served
  through NGINX.
