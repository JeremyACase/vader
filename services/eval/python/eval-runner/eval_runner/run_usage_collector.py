"""Totals what a workflow's task attempts spent, from their per-turn transcripts."""

from __future__ import annotations

from eval_runner.interface_core_server import InterfaceCoreServer, JsonObject
from eval_runner.run_record import RunUsage

# A planner task hangs off the workflow's task graph; a runtime subtask only off its parent, and
# runtime subtasks never decompose further at the default vader.task-decomposition.max-depth of 1.
ATTEMPT_WORKFLOW_PATHS = (
    "task.taskGraph.taskPlan.workflow.id",
    "task.parentTask.taskGraph.taskPlan.workflow.id",
)


class RunUsageCollector:
    def __init__(self, core_server: InterfaceCoreServer) -> None:
        self._core_server = core_server

    def collect(self, workflow_id: str) -> RunUsage:
        attempts = self._attempts_of(workflow_id)
        turns = [
            turn
            for attempt in attempts
            for turn in self._core_server.query_all(
                "task-attempt-transcript", {"taskAttempt.id": str(attempt["id"])}
            )
        ]
        return RunUsage(
            attempts=len(attempts),
            turns=len(turns),
            prompt_tokens=sum(number(turn, "promptTokens") for turn in turns),
            completion_tokens=sum(number(turn, "completionTokens") for turn in turns),
            peak_prompt_tokens=max((number(turn, "promptTokens") for turn in turns), default=0),
            peak_prompt_characters=max(
                (number(turn, "promptCharacters") for turn in turns), default=0
            ),
        )

    def _attempts_of(self, workflow_id: str) -> list[JsonObject]:
        return [
            attempt
            for path in ATTEMPT_WORKFLOW_PATHS
            for attempt in self._core_server.query_all("task-attempt", {path: workflow_id})
        ]


def number(row: JsonObject, field: str) -> int:
    value = row.get(field)
    return int(value) if isinstance(value, int | float) else 0
