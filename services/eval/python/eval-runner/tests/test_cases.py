"""Checks every benchmark case is complete, and that its checker grades its own fixtures right.

A checker that passes bad output, or fails good output, would silently corrupt every baseline
measured with it, so each case ships fixtures: a directory of delivered files whose name says
how it must grade -- `good-*` passes strictly, `lenient-*` passes but not strictly, `bad-*` fails.
"""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
from pathlib import Path
from types import ModuleType

import pytest
import yaml

CASES = Path(__file__).resolve().parent.parent / "cases"
REQUIRED_KEYS = {"id", "difficulty", "prompt", "inputs", "deliverable", "samples"}
EXPECTATIONS = {"good": (True, True), "lenient": (True, False), "bad": (False, False)}


def case_dirs() -> list[Path]:
    return sorted(path.parent for path in CASES.glob("*/case.yaml"))


def fixture_dirs() -> list[Path]:
    return sorted(path for case in case_dirs() for path in (case / "fixtures").iterdir())


def definition_of(case: Path) -> dict[str, object]:
    loaded = yaml.safe_load((case / "case.yaml").read_text(encoding="utf-8"))
    assert isinstance(loaded, dict)
    return loaded


def checker_of(case: Path) -> ModuleType:
    spec = importlib.util.spec_from_file_location(f"check_{case.name}", case / "check.py")
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    # Dataclasses resolve their annotations through sys.modules, so register before executing.
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def test_there_is_at_least_one_case() -> None:
    assert case_dirs()


@pytest.mark.parametrize("case", case_dirs(), ids=lambda case: case.name)
def test_case_definition_is_complete(case: Path) -> None:
    definition = definition_of(case)
    inputs = definition["inputs"]

    assert REQUIRED_KEYS <= definition.keys()
    assert definition["id"] == case.name
    assert (case / "check.py").is_file()
    assert isinstance(inputs, list) and inputs
    assert all((case / "input" / str(name)).is_file() for name in inputs)
    assert str(definition["deliverable"]) in str(definition["prompt"])


@pytest.mark.parametrize("case", case_dirs(), ids=lambda case: case.name)
def test_case_has_good_and_bad_fixtures(case: Path) -> None:
    prefixes = {path.name.split("-", 1)[0] for path in (case / "fixtures").iterdir()}

    assert {"good", "bad"} <= prefixes
    assert prefixes <= EXPECTATIONS.keys()


@pytest.mark.parametrize(
    "fixture", fixture_dirs(), ids=lambda path: path.parent.parent.name + "/" + path.name
)
def test_checker_grades_fixture_as_its_name_says(fixture: Path) -> None:
    case = fixture.parent.parent
    expected_passed, expected_strict = EXPECTATIONS[fixture.name.split("-", 1)[0]]

    result = checker_of(case).check(fixture, case / "input")

    assert (result.passed, result.strict) == (expected_passed, expected_strict), result.reasons
    assert bool(result.reasons) != result.strict


@pytest.mark.parametrize("case", case_dirs(), ids=lambda case: case.name)
def test_checker_cli_prints_its_verdict_as_json_and_exits_zero(case: Path) -> None:
    bad = next(
        path for path in sorted((case / "fixtures").iterdir()) if path.name.startswith("bad")
    )

    completed = subprocess.run(
        [
            sys.executable,
            str(case / "check.py"),
            "--deliverables",
            str(bad),
            "--inputs",
            str(case / "input"),
        ],
        capture_output=True,
        text=True,
        check=False,
    )

    assert completed.returncode == 0, completed.stderr
    assert json.loads(completed.stdout)["passed"] is False
