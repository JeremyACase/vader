"""What a run's workflow did, kept beside its result so a grade can be explained and not only
counted."""

from __future__ import annotations

from dataclasses import dataclass

from eval_runner.interface_core_server import JsonObject
from eval_runner.run_record import RunUsage


@dataclass(frozen=True)
class AttemptTrace:
    """One task attempt: its row (status, result, failure reason), each turn's new messages and
    reply in turn order, and each tool call it made with its arguments and result."""

    attempt: JsonObject
    turns: list[JsonObject]
    tool_calls: list[JsonObject]

    def to_json(self) -> JsonObject:
        return {"attempt": self.attempt, "turns": self.turns, "tool_calls": self.tool_calls}


@dataclass(frozen=True)
class RunTrace:
    """The workflow as core-server reports it, with its task plan and task graph (runtime subtasks
    included); every attempt at its tasks; and every update on its tasks in order, which is where
    the evaluator's verdicts and the orchestrator's reattempt reasoning are recorded."""

    workflow: JsonObject
    attempts: list[AttemptTrace]
    task_updates: list[JsonObject]

    def usage(self) -> RunUsage:
        turns = [turn for attempt in self.attempts for turn in attempt.turns]
        return RunUsage(
            attempts=len(self.attempts),
            turns=len(turns),
            prompt_tokens=sum(number(turn, "promptTokens") for turn in turns),
            completion_tokens=sum(number(turn, "completionTokens") for turn in turns),
            peak_prompt_tokens=max((number(turn, "promptTokens") for turn in turns), default=0),
            peak_prompt_characters=max(
                (number(turn, "promptCharacters") for turn in turns), default=0
            ),
        )


def number(row: JsonObject, field: str) -> int:
    value = row.get(field)
    return int(value) if isinstance(value, int | float) else 0
