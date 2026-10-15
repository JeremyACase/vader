"""The progress a run reports while it is in flight."""

from __future__ import annotations

from pathlib import Path

from eval_runner.interface_core_server import CoreServerError, JsonObject
from eval_runner.run_progress_reporter import RunProgressReporter

WORKFLOW: JsonObject = {"id": "workflow-1", "status": "RUNNING"}
TASK: JsonObject = {"id": "task-1", "title": "Convert quarters to months"}


def an_attempt(status: str, **overrides: object) -> JsonObject:
    return {
        "id": "attempt-1",
        "taskId": "task-1",
        "attemptNumber": 1,
        "status": status,
        "turnsUsed": 4,
        **overrides,
    }


class ProgressStub:
    """Serves whatever attempts the test sets, one planner task, and fails when told to."""

    def __init__(self) -> None:
        self.attempts: list[JsonObject] = []
        self.failing = False

    def configuration(self) -> JsonObject:
        return {}

    def prompts(self) -> JsonObject:
        return {}

    def submit_prompt(self, text: str, files: list[Path]) -> str:
        return "prompt-1"

    def latest_workflow(self, client_prompt_id: str) -> JsonObject | None:
        return WORKFLOW

    def query_all(self, entity: str, filters: dict[str, str]) -> list[JsonObject]:
        if self.failing:
            raise CoreServerError("connection refused")
        planner = not next(iter(filters)).removeprefix("task.").startswith("parentTask")
        rows = {"task-attempt": self.attempts, "task": [TASK]}[entity]
        return list(rows) if planner else []

    def download(self, object_id: str) -> bytes:
        return b""


def a_reporter(stub: ProgressStub) -> tuple[RunProgressReporter, list[str]]:
    lines: list[str] = []
    return RunProgressReporter(stub, emit=lines.append, heartbeat_seconds=120.0), lines


def test_a_line_is_reported_only_when_what_the_run_is_doing_changes() -> None:
    stub = ProgressStub()
    reporter, lines = a_reporter(stub)

    reporter.observe(None, 0.0)
    reporter.observe(None, 15.0)
    reporter.observe(WORKFLOW, 30.0)
    stub.attempts = [an_attempt("RUNNING")]
    reporter.observe(WORKFLOW, 45.0)
    stub.attempts = [an_attempt("RUNNING", turnsUsed=9)]
    reporter.observe(WORKFLOW, 60.0)

    assert lines == [
        "    +0m00s workflow not created yet · attempts: none yet · 0 turns",
        "    +0m30s workflow RUNNING · attempts: none yet · 0 turns",
        "    +0m45s workflow RUNNING · attempts: 1 RUNNING · 4 turns",
    ]


def test_a_quiet_run_repeats_its_line_so_it_can_be_told_from_a_stopped_runner() -> None:
    reporter, lines = a_reporter(ProgressStub())

    reporter.observe(WORKFLOW, 0.0)
    reporter.observe(WORKFLOW, 105.0)
    reporter.observe(WORKFLOW, 120.0)

    assert [line.split()[0] for line in lines] == ["+0m00s", "+2m00s"]


def test_each_attempt_is_reported_once_as_it_settles_with_its_task_and_reason() -> None:
    stub = ProgressStub()
    reporter, lines = a_reporter(stub)
    stub.attempts = [an_attempt("FAILED", failureReason="never uploaded monthly_revenue.csv")]

    reporter.observe(WORKFLOW, 90.0)
    reporter.observe(WORKFLOW, 105.0)

    settled = [line for line in lines if "attempt 1 of" in line]
    assert settled == [
        "    +1m30s attempt 1 of 'Convert quarters to months' FAILED after 4 turns: "
        "never uploaded monthly_revenue.csv"
    ]


def test_a_new_run_forgets_the_previous_one() -> None:
    stub = ProgressStub()
    reporter, lines = a_reporter(stub)
    stub.attempts = [an_attempt("SUCCEEDED")]
    reporter.observe(WORKFLOW, 10.0)

    reporter.start()
    reporter.observe(WORKFLOW, 10.0)

    assert sum("SUCCEEDED after" in line for line in lines) == 2


def test_failing_to_read_progress_is_reported_never_raised() -> None:
    stub = ProgressStub()
    stub.failing = True
    reporter, lines = a_reporter(stub)

    reporter.observe(WORKFLOW, 0.0)

    assert lines == ["    +0m00s progress unavailable: connection refused"]
