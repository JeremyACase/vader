# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.3]
### Fixed
- An empty reply from the model (no text and no tool calls) no longer ends the run as a success
  with no output. The model is nudged to continue instead; one that keeps answering blank trips
  the stall detector and is reported as stalled. A reply cut off at the output token cap now
  fails the attempt with a reason saying so, rather than being treated as a finished answer.

## [0.3.2]
### Changed
- A failed call to core-server is now reported as a failed call carrying the underlying cause
  (e.g. the HTTP status and message), rather than always as "unreachable" -- core-server answering
  with an error is not the same as it being down.

## [0.3.1]
### Changed
- The task instructions no longer ask the model to create a sandbox or stage files: attached files
  are already in its working directory and `run_python_code` needs no sandbox name. It is also told
  to wait for a tool's result before depending on it in a later call.

## [0.3.0]
### Added
- **The harness now runs a real, multi-turn agent action loop with tool use.** A run seeds the
  conversation with the task's objective, takes an inference turn, and -- if the model requests
  one or more tools -- invokes each of them against `core-server` and folds the results back into
  the conversation before taking another turn. This repeats until a turn comes back with no tool
  calls (the model's final answer) or the turn/token/deadline budget or the stall detector trips.
  The stall detector now fingerprints the actual tool name and arguments a turn requested, rather
  than only ever having a whole turn's free-text output to go on.
- Every registered tool is now offered to the model on every inference turn, not just during the
  initial decomposition step, so a task can actually retrieve and act on data (e.g. an uploaded
  file) instead of guessing at an answer with no way to look anything up.
- The assignment a harness fetches now carries background its own task description never did:
  the original client-submitted request, any files attached to it, and the results of any
  prerequisite tasks. The system prompt also now explicitly tells the model no human is present
  to answer follow-up questions, so it doesn't waste its one and only reply asking a clarifying
  question that would otherwise be reported and accepted as though it were the finished result.
### Fixed
- The harness's local turn/token/deadline budget now comes from the assignment `core-server`
  actually dispatched, instead of a fixed local default -- a task with a shorter or longer budget
  than the default is now bounded correctly rather than by a value that didn't reflect it.

## [0.2.0]
### Changed
- **The `CoreServerAdapter` is now real, not stubbed.** Every `ControlPlane` and
  `InferenceGateway` method makes an actual HTTP call to `core-server`'s `/agent/*` endpoints —
  fetching the work order, sending heartbeats, completing inference turns, and reporting a
  terminal outcome — using the assignment id as the call's credential.
- **The turn loop runs a real (v1) turn.** `AgentHarnessRunner::run` fetches the assignment and,
  budget and stall checks permitting, performs a single inference turn against the task's
  objective and reports the resulting outcome. This is intentionally single-turn for now; the
  same budget/stall-detector checks stay in place as the bound a future multi-turn action loop
  will plug into.

## [0.1.0]
### Added
- **Initial scaffold.** Cargo project for the agent harness: an ephemeral k8s Job that executes
  exactly one task-graph subtask, identified by a `TASK_ID`/`ASSIGNMENT_ID` pair. The
  `ControlPlane` and `InferenceGateway` traits define the harness's only contract with the
  outside world — both implemented by a single `CoreServerAdapter`, so `core-server` is the
  harness's sole path to any LLM and its sole control plane. `HarnessBudget` (turn/token/deadline
  caps) and `StallDetector` (repeated-action detection) are fully implemented and unit-tested;
  the adapter's HTTP calls and the turn loop itself (`AgentHarnessRunner::run`) are stubbed
  pending the corresponding `core-server` `/agent/*` endpoints. Wired into `./gradlew build` and
  the devops Docker build/push pipeline; not deployed by Helm yet (spawned on demand once the
  scheduler and operator exist).
