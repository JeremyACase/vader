# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.12.0]
### Added
- A submitted prompt now appears in the workflows panel immediately as a "Decomposing…"
  placeholder, rather than leaving the panel unchanged until the server finishes decomposition and
  the next poll surfaces the real workflow. The placeholder is dropped in favor of the real
  workflow row the moment it comes back.
### Changed
- The submit form now allows only one prompt in flight at a time: sending is blocked, and the
  button reads "Waiting on previous prompt…", until the current prompt's workflow has come back
  from the server. This keeps the panel's placeholder-to-real handoff unambiguous instead of
  juggling several in-flight prompts at once.

## [0.11.0]
### Added
- An "Active workflows" panel that lists every workflow currently running, independent of the
  submit form — a new prompt can be sent while others are still in flight. Each workflow expands
  to show its originating prompt, the decomposition's objective and reasoning, and its task
  graph; each task further expands into its dispatch history and, per attempt, the model's
  chain-of-thought turns as they happen.
### Changed
- Submitting a prompt no longer blocks the form while waiting for its decomposition — that
  progress now lives in the active-workflows panel instead. The queue-depth readout is now
  refreshed continuously rather than only while awaiting a single submission.
- The data feeding the active-workflows panel is fetched behind a swappable strategy: today it
  polls the server directly, and the same seam will let it switch to a backend-for-frontend
  pushing updates over a WebSocket once one exists, without changing how the panel is built.

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
