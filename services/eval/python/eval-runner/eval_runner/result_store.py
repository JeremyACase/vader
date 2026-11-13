"""Where a baseline's results live on disk."""

from __future__ import annotations

import json
import shutil
from pathlib import Path

from eval_runner.prompt_set import PromptSet
from eval_runner.run_record import RunRecord
from eval_runner.run_trace import RunTrace


class ResultStore:
    """One JSON file per run, at runs/<case>/seed-<seed>.json beside the files it delivered and,
    in seed-<seed>-trace/, what its workflow did: workflow.json (its task plan), attempts.json
    (each attempt's transcript and tool calls) and task-updates.json (the verdicts on its tasks).
    Plus summary.json, plus the prompts the runs were made with, at
    prompts/<kind>-<fingerprint>/<template>.st, so each fingerprint a result records can be read
    as the wording it stands for. A run whose file exists and was scored is done, so an
    interrupted baseline resumes where it stopped; one that hit an infrastructure error, or whose
    checker raised, is run again."""

    def __init__(self, root: Path) -> None:
        self._root = root

    def record_path(self, case_id: str, seed: int) -> Path:
        return self._root / "runs" / case_id / f"seed-{seed}.json"

    def deliverables_dir(self, case_id: str, seed: int) -> Path:
        return self._root / "runs" / case_id / f"seed-{seed}"

    def trace_dir(self, case_id: str, seed: int) -> Path:
        return self._root / "runs" / case_id / f"seed-{seed}-trace"

    def save_trace(self, case_id: str, seed: int, trace: RunTrace | None) -> None:
        """Replaces whatever an earlier try at the same run left, so a trace never sits beside a
        result it didn't produce."""
        directory = self.trace_dir(case_id, seed)
        shutil.rmtree(directory, ignore_errors=True)
        if trace is not None:
            write_json(directory / "workflow.json", trace.workflow)
            attempts = [attempt.to_json() for attempt in trace.attempts]
            write_json(directory / "attempts.json", attempts)
            write_json(directory / "task-updates.json", trace.task_updates)

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

    def save_prompts(self, prompt_sets: list[PromptSet]) -> Path:
        directory = self._root / "prompts"
        for prompt_set in prompt_sets:
            for name, text in prompt_set.templates.items():
                path = directory / prompt_set.directory_name() / f"{name}.st"
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text + "\n", encoding="utf-8", newline="\n")
        return directory

    @staticmethod
    def _read(path: Path) -> RunRecord:
        return RunRecord.from_json(json.loads(path.read_text(encoding="utf-8")))


def write_json(path: Path, data: object) -> None:
    """Writes through a temporary file, so a crash mid-write never leaves a half-written result
    that a resumed run would trust."""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)
