# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0]
### Changed
- A bare expression on the last line of submitted code is now echoed to stdout, as a notebook
  would show it, instead of being silently discarded. Agents write notebook-style code often
  enough that they were getting back empty output and resubmitting the same code until they
  stalled. Values of `None` aren't echoed, and tracebacks still point at the submitted code's own
  line numbers.

## [0.2.0]
### Added
- `HEAD /workspace/files/{filename}` reports whether a file is already staged (200) or not (404),
  so `core-server` can stage only what's missing before each run instead of re-sending every file.

## [0.1.0]
### Added
- Initial service: a small FastAPI app that runs submitted Python code as a subprocess against a
  persistent per-pod workspace and returns its stdout/stderr/exit code. Deployed as the Python
  sandbox pod's own container command, replacing the previous idling placeholder, and reachable
  only from `core-server` over the sandbox's already-provisioned Service. `pandas`/`openpyxl` are
  preinstalled so an agent can analyze an uploaded spreadsheet without any setup step.
