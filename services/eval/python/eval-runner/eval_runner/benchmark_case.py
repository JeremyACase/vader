"""A benchmark case as defined by its directory under cases/."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml


@dataclass(frozen=True)
class BenchmarkCase:
    id: str
    difficulty: int
    prompt: str
    inputs: list[Path]
    deliverable: str
    directory: Path

    @classmethod
    def load(cls, directory: Path) -> BenchmarkCase:
        definition = yaml.safe_load((directory / "case.yaml").read_text(encoding="utf-8"))
        return cls(
            id=str(definition["id"]),
            difficulty=int(definition["difficulty"]),
            prompt=str(definition["prompt"]).strip(),
            inputs=[directory / "input" / str(name) for name in definition["inputs"]],
            deliverable=str(definition["deliverable"]),
            directory=directory,
        )


def discover_cases(root: Path, ids: list[str]) -> list[BenchmarkCase]:
    """The named cases, or every case under root when none are named, in id order."""
    available = {path.parent.name: path.parent for path in root.glob("*/case.yaml")}
    unknown = sorted(set(ids) - available.keys())
    if unknown:
        raise ValueError(f"no such benchmark cases: {unknown}")
    return [BenchmarkCase.load(available[case_id]) for case_id in sorted(ids or available)]
