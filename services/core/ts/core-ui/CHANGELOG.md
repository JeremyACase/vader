# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.17.0]
### Added
- Each transcript turn shows why the model stopped generating, and a turn cut off at the output
  token cap is flagged in amber so a truncated reply isn't mistaken for a complete one.
- `./gradlew build` now runs the unit tests, in a container with headless Chromium, so a failing
  spec fails the build and no local browser is needed.

## [0.16.0]
### Added
- Workflows paused on an LLM outage show an amber `AWAITING_LLM` badge, distinct from both
  running and failed.

## [0.15.0]
### Added
- The task update history now shows two new update types (`CREATED`, `RUNNING`) and who authored
  each update (system, task agent, orchestrator, or evaluator).

## [0.14.0]
### Added
- A left-anchored vertical nav rail, icon-only, sits outside the main content column now. Its
  first item, Workflows, toggles the workflow panel open/closed rather than it always being
  shown.

## [0.13.0]
### Added
- The workflow panel now shows one selected workflow's task graph as an interactive DAG instead
  of a flat list, colored by each task's live status (blue active, green succeeded, red failed,
  gray never attempted). Clicking a node shows that task's full detail: its attempt history, each
  attempt's chain-of-thought, and its update history (evaluator/orchestrator verdicts and
  progress notes).
### Changed
- The panel itself is now a paginated, single-select list on the left, rather than an
  inline-expanding accordion — selecting a workflow is independent of whatever page of the list
  is currently showing, and keeps polling live regardless.

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
