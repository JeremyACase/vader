# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0]
### Added
- Initial service: a small FastAPI app that runs submitted Python code as a subprocess against a
  persistent per-pod workspace and returns its stdout/stderr/exit code. Deployed as the Python
  sandbox pod's own container command, replacing the previous idling placeholder, and reachable
  only from `core-server` over the sandbox's already-provisioned Service. `pandas`/`openpyxl` are
  preinstalled so an agent can analyze an uploaded spreadsheet without any setup step.
