"""Grades a run's deliverables with its case's own check.py."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from types import ModuleType

from eval_runner.benchmark_case import BenchmarkCase
from eval_runner.run_record import Grade


class CheckerAdapter:
    """Loads a case's checker as a module and adapts its verdict to a Grade.

    Every case's checker is a module named check, so each is registered under a name unique to
    its case rather than imported normally.
    """

    def grade(self, case: BenchmarkCase, deliverables: Path) -> Grade:
        result = self._checker_of(case).check(deliverables, case.directory / "input")
        return Grade(
            passed=bool(result.passed),
            strict=bool(result.strict),
            reasons=[str(reason) for reason in result.reasons],
        )

    @staticmethod
    def _checker_of(case: BenchmarkCase) -> ModuleType:
        name = f"check_{case.id.replace('-', '_')}"
        spec = importlib.util.spec_from_file_location(name, case.directory / "check.py")
        if spec is None or spec.loader is None:
            raise ImportError(f"cannot load the checker of {case.id}")
        module = importlib.util.module_from_spec(spec)
        # Dataclasses resolve their annotations through sys.modules, so register before executing.
        sys.modules[name] = module
        spec.loader.exec_module(module)
        return module
