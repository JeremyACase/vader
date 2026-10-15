"""What one run of one case on one seed produced, as written to and read from its result file."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import StrEnum


class RunOutcome(StrEnum):
    # The workflow settled (succeeded or failed) and whatever it delivered was graded.
    GRADED = "graded"
    # The workflow was still running at the run timeout. Graded on what it had delivered, but
    # never counted as a pass: the system didn't finish.
    TIMED_OUT = "timed_out"
    # core-server was unreachable or erroring. Says nothing about the prompts, so it is retried on
    # the next invocation and left out of every rate.
    INFRASTRUCTURE_ERROR = "infrastructure_error"


@dataclass(frozen=True)
class Grade:
    passed: bool
    strict: bool
    reasons: list[str]


@dataclass(frozen=True)
class RunUsage:
    attempts: int
    turns: int
    prompt_tokens: int
    completion_tokens: int
    peak_prompt_tokens: int
    peak_prompt_characters: int


@dataclass(frozen=True)
class RunRecord:
    case_id: str
    difficulty: int
    seed: int
    outcome: RunOutcome
    workflow_status: str | None
    elapsed_seconds: float
    grade: Grade | None
    usage: RunUsage | None
    configuration: dict[str, object]
    error: str | None

    def passed(self) -> bool:
        return self.outcome == RunOutcome.GRADED and self.grade is not None and self.grade.passed

    def strict(self) -> bool:
        return self.passed() and self.grade is not None and self.grade.strict

    def scored(self) -> bool:
        return self.outcome != RunOutcome.INFRASTRUCTURE_ERROR

    def to_json(self) -> dict[str, object]:
        return asdict(self)

    @classmethod
    def from_json(cls, data: dict[str, object]) -> RunRecord:
        grade = data.get("grade")
        usage = data.get("usage")
        configuration = data.get("configuration")
        return cls(
            case_id=str(data["case_id"]),
            difficulty=int(str(data["difficulty"])),
            seed=int(str(data["seed"])),
            outcome=RunOutcome(str(data["outcome"])),
            workflow_status=optional_text(data.get("workflow_status")),
            elapsed_seconds=float(str(data["elapsed_seconds"])),
            grade=Grade(**grade) if isinstance(grade, dict) else None,
            usage=RunUsage(**usage) if isinstance(usage, dict) else None,
            configuration=configuration if isinstance(configuration, dict) else {},
            error=optional_text(data.get("error")),
        )


def optional_text(value: object) -> str | None:
    return None if value is None else str(value)
