# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
