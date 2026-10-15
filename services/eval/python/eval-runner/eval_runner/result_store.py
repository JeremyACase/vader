"""Where a baseline's results live on disk."""

from __future__ import annotations

import json
from pathlib import Path

from eval_runner.run_record import RunRecord


class ResultStore:
    """One JSON file per run, at runs/<case>/seed-<seed>.json beside the files it delivered, plus
    summary.json. A run whose file exists and was scored is done, so an interrupted baseline
    resumes where it stopped; one that hit an infrastructure error is run again."""

    def __init__(self, root: Path) -> None:
        self._root = root

    def record_path(self, case_id: str, seed: int) -> Path:
        return self._root / "runs" / case_id / f"seed-{seed}.json"

    def deliverables_dir(self, case_id: str, seed: int) -> Path:
        return self._root / "runs" / case_id / f"seed-{seed}"

    def is_done(self, case_id: str, seed: int) -> bool:
        path = self.record_path(case_id, seed)
        return path.is_file() and self._read(path).scored()

    def save(self, record: RunRecord) -> Path:
        path = self.record_path(record.case_id, record.seed)
        write_json(path, record.to_json())
        return path

    def load_all(self) -> list[RunRecord]:
        return [self._read(path) for path in sorted(self._root.glob("runs/*/seed-*.json"))]

    def save_summary(self, summary: dict[str, object]) -> Path:
        path = self._root / "summary.json"
        write_json(path, summary)
        return path

    @staticmethod
    def _read(path: Path) -> RunRecord:
        return RunRecord.from_json(json.loads(path.read_text(encoding="utf-8")))


def write_json(path: Path, data: dict[str, object]) -> None:
    """Writes through a temporary file, so a crash mid-write never leaves a half-written result
    that a resumed run would trust."""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)
