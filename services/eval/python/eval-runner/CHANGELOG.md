# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0]
### Added
- Each run now keeps a trace of what its workflow did beside its result: the task plan it was
  decomposed into, every attempt's turn-by-turn transcript and tool calls, and every update on
  its tasks, including the evaluator's verdicts and the orchestrator's reattempt reasoning. A
  grade can now be explained after the cluster is gone, not only counted.

## [0.2.0]
### Changed
- A run now passes on the substance of its conversion rather than its presentation. Each month
  label is judged by the calendar month it names, however it is spelled (`2024-04`, `Apr 2024`,
  `2024-04-30` and `2024-Q2-01` all name April), and columns by what they hold, so a correct
  conversion is no longer failed for its labels, headers, row order or float noise in
  whole-cent amounts; those now cost only the strict pass. A label that names no month, or the
  wrong one, still fails the run, so baselines graded before this version are not comparable
  with later ones.

## [0.1.1]
### Fixed
- A delivered month written as YYYY-MM but naming no month of the year (such as `2024-00`) now
  fails its run instead of crashing the checker, and a checker that raises for any other reason
  no longer ends the baseline: that run is recorded as a checker error, left out of every rate,
  counted in the summary beside infrastructure errors and run again on resume.

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
  A workflow still running when its run times out is cancelled, so it no longer competes with
  the next run for the model.
