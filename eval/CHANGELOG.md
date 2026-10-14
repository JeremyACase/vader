# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0]
### Added
- The first benchmark case, quarterly-to-monthly-l1: split quarterly revenue evenly into a
  monthly CSV. Its checker grades a run's delivered files by invariants (complete, ordered
  months; quarterly totals preserved) plus a strict pass for following the request exactly, and
  is itself tested against good, lenient and bad fixture outputs.
