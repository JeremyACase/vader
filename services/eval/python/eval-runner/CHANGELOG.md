# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0]
### Added
- The benchmark cases EVAL runs measure prompts against: a quarterly-to-monthly ladder of three
  difficulty levels, from an even split of whole-dollar revenue (difficulty 1), through cent-exact
  rounding that must keep every quarter's total (difficulty 2), to a headcount column that must be
  carried as a point-in-time count rather than split like revenue (difficulty 3). Each case's
  checker grades a run's delivered files by invariants any correct conversion keeps, plus a strict
  pass for following the request exactly, and is itself tested against good, lenient and bad
  fixtures.
- A baseline runner that submits each case to a live EVAL release, waits for its workflow,
  grades what it delivered and totals the tokens it spent, writing one result per case and seed
  plus a summary with each seed's verdict, so a later run can be compared pair by pair. Runs that
  only hit an unreachable core-server are retried rather than scored. A run in flight reports
  each change in its workflow and attempts (with why an attempt failed) and each seed ends with a
  table, all of it also written, timestamped, to a log in the results directory. The prompt
  templates in effect are saved beside the results under their call kind and fingerprint.
