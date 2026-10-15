# Changelog

All notable changes to this module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0]
### Added
- The benchmark cases EVAL runs measure prompts against: a three-rung quarterly-to-monthly
  ladder, from an even split of whole-dollar revenue (level 1), through cent-exact rounding
  that must keep every quarter's total (level 2), to a headcount column that must be carried as
  a point-in-time count rather than split like revenue (level 3). Each case's checker grades a
  run's delivered files by invariants any correct conversion keeps, plus a strict pass for
  following the request exactly, and is itself tested against good, lenient and bad fixtures.
