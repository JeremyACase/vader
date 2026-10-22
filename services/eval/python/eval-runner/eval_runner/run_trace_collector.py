"""Fetches what a workflow did from core-server: its plan, attempts, transcripts, tool calls and
the updates on its tasks."""

from __future__ import annotations

from datetime import datetime

from eval_runner.interface_core_server import InterfaceCoreServer, JsonObject
from eval_runner.run_trace import AttemptTrace, RunTrace, number

# A planner task hangs off the workflow's task graph; a runtime subtask only off its parent, and
# runtime subtasks never decompose further at the default vader.task-decomposition.max-depth of 1.
# Valid from any entity that refers to its task as `task`.
ATTEMPT_WORKFLOW_PATHS = (
    "task.taskGraph.taskPlan.workflow.id",
    "task.parentTask.taskGraph.taskPlan.workflow.id",
)


class RunTraceCollector:
    def __init__(self, core_server: InterfaceCoreServer) -> None:
        self._core_server = core_server

    def collect(self, workflow: JsonObject) -> RunTrace:
        workflow_id = str(workflow["id"])
        attempts = chronological(self._rows_of("task-attempt", workflow_id))
        return RunTrace(
            workflow=workflow,
            attempts=[self._attempt_trace(attempt) for attempt in attempts],
            task_updates=chronological(self._rows_of("task-update", workflow_id)),
        )

    def _attempt_trace(self, attempt: JsonObject) -> AttemptTrace:
        filters = {"taskAttempt.id": str(attempt["id"])}
        turns = self._core_server.query_all("task-attempt-transcript", filters)
        tool_calls = self._core_server.query_all("task-attempt-tool-call", filters)
        return AttemptTrace(
            attempt=attempt,
            turns=sorted(turns, key=lambda turn: number(turn, "turnIndex")),
            tool_calls=chronological(tool_calls),
        )

    def _rows_of(self, entity: str, workflow_id: str) -> list[JsonObject]:
        return [
            row
            for path in ATTEMPT_WORKFLOW_PATHS
            for row in self._core_server.query_all(entity, {path: workflow_id})
        ]


def chronological(rows: list[JsonObject]) -> list[JsonObject]:
    """Oldest first, by the instant each row was created rather than its timestamp's text, whose
    fractional seconds vary in length."""
    return sorted(rows, key=created_at)


def created_at(row: JsonObject) -> float:
    value = row.get("createdAt")
    return datetime.fromisoformat(value).timestamp() if isinstance(value, str) else 0.0
