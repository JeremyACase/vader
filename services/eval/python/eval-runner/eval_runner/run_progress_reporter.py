"""Narrates a run while it is in flight, so a long benchmark can be watched, not waited on."""

from __future__ import annotations

from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass

from eval_runner.interface_core_server import CoreServerError, InterfaceCoreServer, JsonObject
from eval_runner.run_usage_collector import ATTEMPT_WORKFLOW_PATHS, number

HEARTBEAT_SECONDS = 120.0
SETTLED_ATTEMPT_STATUSES = frozenset({"SUCCEEDED", "FAILED", "TIMED_OUT"})
REASON_LIMIT = 200


@dataclass(frozen=True)
class ProgressSnapshot:
    """What a run is doing at one poll: its workflow's status and its attempts, with each task's
    title by id."""

    workflow_status: str
    attempts: list[JsonObject]
    task_titles: dict[str, str]

    def key(self) -> str:
        """What a change is detected on. Turns are left out: they tick on nearly every poll."""
        counts = Counter(str(attempt.get("status")) for attempt in self.attempts)
        tally = ", ".join(f"{count} {status}" for status, count in sorted(counts.items()))
        return f"workflow {self.workflow_status} · attempts: {tally or 'none yet'}"

    def turns(self) -> int:
        return sum(number(attempt, "turnsUsed") for attempt in self.attempts)


class RunProgressReporter:
    """Observer of one run's polls. Reports a line whenever the workflow's status or its attempts'
    statuses change, one line for each attempt as it settles (with its task, and its failure
    reason when it has one), and the current line again after a quiet stretch, so a run that is
    merely slow can be told from a runner that has stopped.

    Progress is best effort: failing to read it is reported, never raised, so it can't affect the
    run it describes.
    """

    def __init__(
        self,
        core_server: InterfaceCoreServer,
        emit: Callable[[str], None] | None = None,
        heartbeat_seconds: float = HEARTBEAT_SECONDS,
    ) -> None:
        self._core_server = core_server
        self._emit = emit or print_line
        self._heartbeat_seconds = heartbeat_seconds
        self.start()

    def start(self) -> None:
        """Forgets the previous run."""
        self._last_key = ""
        self._last_reported_at = 0.0
        self._settled_attempts: set[str] = set()

    def observe(self, workflow: JsonObject | None, elapsed_seconds: float) -> None:
        try:
            snapshot = self._snapshot(workflow)
            self._report_settled_attempts(snapshot, elapsed_seconds)
            line = f"{snapshot.key()} · {snapshot.turns()} turns"
            self._report_status(snapshot.key(), line, elapsed_seconds)
        except CoreServerError as error:
            self._report_status("unavailable", f"progress unavailable: {error}", elapsed_seconds)

    def _snapshot(self, workflow: JsonObject | None) -> ProgressSnapshot:
        snapshot = ProgressSnapshot("not created yet", [], {})
        if workflow is not None:
            workflow_id = str(workflow["id"])
            snapshot = ProgressSnapshot(
                str(workflow.get("status")),
                self._rows_of("task-attempt", workflow_id, ATTEMPT_WORKFLOW_PATHS),
                {
                    str(task["id"]): str(task.get("title"))
                    for task in self._rows_of("task", workflow_id, task_workflow_paths())
                },
            )
        return snapshot

    def _rows_of(self, entity: str, workflow_id: str, paths: tuple[str, ...]) -> list[JsonObject]:
        return [
            row
            for path in paths
            for row in self._core_server.query_all(entity, {path: workflow_id})
        ]

    def _report_settled_attempts(self, snapshot: ProgressSnapshot, elapsed_seconds: float) -> None:
        newly_settled = [
            attempt
            for attempt in snapshot.attempts
            if str(attempt.get("status")) in SETTLED_ATTEMPT_STATUSES
            and str(attempt["id"]) not in self._settled_attempts
        ]
        for attempt in newly_settled:
            self._settled_attempts.add(str(attempt["id"]))
            self._report(describe_attempt(attempt, snapshot.task_titles), elapsed_seconds)

    def _report_status(self, key: str, line: str, elapsed_seconds: float) -> None:
        quiet = elapsed_seconds - self._last_reported_at >= self._heartbeat_seconds
        if key != self._last_key or quiet:
            self._last_key = key
            self._report(line, elapsed_seconds)

    def _report(self, line: str, elapsed_seconds: float) -> None:
        self._last_reported_at = elapsed_seconds
        self._emit(f"    +{format_elapsed(elapsed_seconds)} {line}")


def task_workflow_paths() -> tuple[str, ...]:
    """A task's own paths to its workflow: the attempt's, less the hop from attempt to task."""
    return tuple(path.removeprefix("task.") for path in ATTEMPT_WORKFLOW_PATHS)


def describe_attempt(attempt: JsonObject, task_titles: dict[str, str]) -> str:
    title = task_titles.get(str(attempt.get("taskId")), "an unknown task")
    reason = attempt.get("failureReason")
    because = f": {str(reason)[:REASON_LIMIT]}" if reason else ""
    return (
        f"attempt {attempt.get('attemptNumber')} of '{title}' {attempt.get('status')} "
        f"after {number(attempt, 'turnsUsed')} turns{because}"
    )


def format_elapsed(seconds: float) -> str:
    minutes, remainder = divmod(int(seconds), 60)
    return f"{minutes}m{remainder:02d}s"


def print_line(line: str) -> None:
    print(line, flush=True)
