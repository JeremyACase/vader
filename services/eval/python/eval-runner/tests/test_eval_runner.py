"""The baseline runner's logic, against a stub core-server and the real difficulty 1 case and its
fixtures."""

from __future__ import annotations

import dataclasses
import json
from pathlib import Path

import pytest

from eval_runner.__main__ import configuration_problems
from eval_runner.baseline_summary_builder import BaselineSummaryBuilder
from eval_runner.benchmark_case import BenchmarkCase, discover_cases
from eval_runner.case_run_service import CaseRunService, RunSettings
from eval_runner.checker_adapter import CheckerAdapter
from eval_runner.interface_core_server import CoreServerError, JsonObject
from eval_runner.prompt_set import prompt_sets
from eval_runner.result_store import ResultStore
from eval_runner.run_progress_reporter import RunProgressReporter
from eval_runner.run_record import Grade, RunOutcome, RunRecord, RunUsage
from eval_runner.run_usage_collector import RunUsageCollector

CASES = Path(__file__).resolve().parent.parent / "cases"
DIFFICULTY_1 = CASES / "quarterly-to-monthly-difficulty-1"
GOOD_DELIVERABLE = (
    DIFFICULTY_1 / "fixtures" / "good-even-split" / "monthly_revenue.csv"
).read_bytes()
BAD_DELIVERABLE = (DIFFICULTY_1 / "fixtures" / "bad-wrong-sum" / "monthly_revenue.csv").read_bytes()


class FakeClock:
    """Time that only moves when the code under test sleeps."""

    def __init__(self) -> None:
        self.now = 0.0
        self.sleeps: list[float] = []

    def __call__(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


class CoreServerStub:
    """Plays one run: the workflow reports each of `statuses` in turn, then stays on the last;
    the requested file exists as `existing` objects before the prompt and gains `produced`
    after it."""

    def __init__(
        self,
        statuses: list[str],
        produced: bytes | None = GOOD_DELIVERABLE,
        existing: int = 1,
        failing: set[str] | None = None,
    ) -> None:
        self.statuses = statuses
        self.produced = produced
        self.objects: list[JsonObject] = [{"id": f"old-{index}"} for index in range(existing)]
        self.failing = failing or set()
        self.submitted: list[tuple[str, list[Path]]] = []

    def configuration(self) -> JsonObject:
        return a_configuration()

    def prompts(self) -> JsonObject:
        return {}

    def submit_prompt(self, text: str, files: list[Path]) -> str:
        self._maybe_fail("submit")
        self.submitted.append((text, files))
        produced: list[JsonObject] = [{"id": "new"}] if self.produced is not None else []
        self.objects = produced + self.objects
        return "prompt-1"

    def latest_workflow(self, client_prompt_id: str) -> JsonObject | None:
        status = self.statuses.pop(0) if len(self.statuses) > 1 else self.statuses[0]
        return {"id": "workflow-1", "status": status}

    def query_all(self, entity: str, filters: dict[str, str]) -> list[JsonObject]:
        self._maybe_fail(entity)
        rows: dict[str, list[JsonObject]] = {
            "object-metadata": list(self.objects),
            "task-attempt": [{"id": "attempt-1"}]
            if "task.taskGraph" in next(iter(filters))
            else [],
            "task": [],
            "task-attempt-transcript": [
                {"promptTokens": 900, "completionTokens": 100, "promptCharacters": 3000},
                {"promptTokens": 1500, "completionTokens": 50, "promptCharacters": 5000},
            ],
        }
        return rows[entity]

    def download(self, object_id: str) -> bytes:
        assert object_id == "new"
        return self.produced or b""

    def _maybe_fail(self, operation: str) -> None:
        if operation in self.failing:
            raise CoreServerError(f"{operation} unreachable")


def a_configuration(**overrides: object) -> JsonObject:
    return {
        "mode": "EVAL",
        "model": "qwen2.5:7b",
        "temperature": 0.0,
        "seed": 42,
        "contextTokens": 16384,
        "maxOutputTokens": 2048,
        "promptFingerprints": {"DECOMPOSITION": "0123456789ab"},
        **overrides,
    }


def settings(timeout_seconds: float = 600.0) -> RunSettings:
    return RunSettings(
        seed=42,
        configuration=a_configuration(),
        run_timeout_seconds=timeout_seconds,
        poll_interval_seconds=15.0,
    )


def a_record(**overrides: object) -> RunRecord:
    record = RunRecord(
        case_id="quarterly-to-monthly-difficulty-1",
        difficulty=1,
        seed=42,
        outcome=RunOutcome.GRADED,
        workflow_status="SUCCEEDED",
        elapsed_seconds=120.0,
        grade=Grade(passed=True, strict=True, reasons=[]),
        usage=RunUsage(2, 6, 9000, 600, 2500, 9000),
        configuration=a_configuration(),
        error=None,
    )
    return dataclasses.replace(record, **overrides)  # type: ignore[arg-type]


def run_l1(core_server: CoreServerStub, tmp_path: Path, timeout: float = 600.0) -> RunRecord:
    clock = FakeClock()
    service = CaseRunService(
        core_server,
        CheckerAdapter(),
        RunUsageCollector(core_server),
        clock=clock,
        sleep=clock.sleep,
    )
    return service.run(
        BenchmarkCase.load(DIFFICULTY_1), settings(timeout), tmp_path / "deliverables"
    )


def test_a_settled_run_is_graded_on_the_file_it_delivered(tmp_path: Path) -> None:
    core_server = CoreServerStub(["RUNNING", "AWAITING_LLM", "SUCCEEDED"])

    record = run_l1(core_server, tmp_path)

    assert record.outcome == RunOutcome.GRADED
    assert record.workflow_status == "SUCCEEDED"
    assert record.strict()
    assert record.elapsed_seconds == 30.0
    assert core_server.submitted[0][1] == [DIFFICULTY_1 / "input" / "quarterly_revenue.csv"]
    assert (tmp_path / "deliverables" / "monthly_revenue.csv").read_bytes() == GOOD_DELIVERABLE


def test_a_failed_workflow_is_still_graded_on_what_it_delivered(tmp_path: Path) -> None:
    record = run_l1(CoreServerStub(["FAILED"], produced=BAD_DELIVERABLE), tmp_path)

    assert record.outcome == RunOutcome.GRADED
    assert record.grade is not None and not record.grade.passed
    assert not record.passed()


def test_a_file_that_existed_before_the_prompt_is_never_taken_as_its_deliverable(
    tmp_path: Path,
) -> None:
    record = run_l1(CoreServerStub(["SUCCEEDED"], produced=None, existing=3), tmp_path)

    assert record.grade is not None
    assert record.grade.reasons == ["monthly_revenue.csv was not delivered"]


def test_a_workflow_still_running_at_the_timeout_never_counts_as_a_pass(tmp_path: Path) -> None:
    record = run_l1(CoreServerStub(["RUNNING"]), tmp_path, timeout=60.0)

    assert record.outcome == RunOutcome.TIMED_OUT
    assert record.grade is not None and record.grade.passed
    assert not record.passed()
    assert record.scored()


def test_an_unreachable_core_server_is_recorded_as_infrastructure_not_scored(
    tmp_path: Path,
) -> None:
    record = run_l1(CoreServerStub(["SUCCEEDED"], failing={"submit"}), tmp_path)

    assert record.outcome == RunOutcome.INFRASTRUCTURE_ERROR
    assert record.error == "submit unreachable"
    assert not record.scored()


def test_usage_totals_every_turn_of_every_attempt(tmp_path: Path) -> None:
    record = run_l1(CoreServerStub(["SUCCEEDED"]), tmp_path)

    assert record.usage == RunUsage(
        attempts=1,
        turns=2,
        prompt_tokens=2400,
        completion_tokens=150,
        peak_prompt_tokens=1500,
        peak_prompt_characters=5000,
    )


def test_a_run_reports_its_progress_on_every_poll(tmp_path: Path) -> None:
    core_server = CoreServerStub(["RUNNING", "AWAITING_LLM", "SUCCEEDED"])
    lines: list[str] = []
    clock = FakeClock()
    service = CaseRunService(
        core_server,
        CheckerAdapter(),
        RunUsageCollector(core_server),
        clock=clock,
        sleep=clock.sleep,
        progress=RunProgressReporter(core_server, emit=lines.append),
    )

    service.run(BenchmarkCase.load(DIFFICULTY_1), settings(), tmp_path / "deliverables")

    assert [line.split(" · ")[0].strip() for line in lines] == [
        "+0m00s workflow RUNNING",
        "+0m15s workflow AWAITING_LLM",
        "+0m30s workflow SUCCEEDED",
    ]


def test_failing_to_total_usage_never_discards_the_grade(tmp_path: Path) -> None:
    record = run_l1(CoreServerStub(["SUCCEEDED"], failing={"task-attempt"}), tmp_path)

    assert record.usage is None
    assert record.strict()


def test_a_record_survives_the_round_trip_through_its_file(tmp_path: Path) -> None:
    store = ResultStore(tmp_path)
    record = a_record(outcome=RunOutcome.TIMED_OUT, usage=None)

    store.save(record)

    assert store.load_all() == [record]
    assert json.loads(store.record_path(record.case_id, 42).read_text())["outcome"] == "timed_out"


def test_only_a_scored_run_counts_as_done(tmp_path: Path) -> None:
    store = ResultStore(tmp_path)
    store.save(a_record(seed=1))
    store.save(a_record(seed=2, outcome=RunOutcome.INFRASTRUCTURE_ERROR, grade=None))

    assert store.is_done("quarterly-to-monthly-difficulty-1", 1)
    assert not store.is_done("quarterly-to-monthly-difficulty-1", 2)
    assert not store.is_done("quarterly-to-monthly-difficulty-1", 3)


def test_the_summary_scores_only_scored_runs_and_keeps_each_seeds_verdict() -> None:
    records = [
        a_record(seed=1),
        a_record(seed=2, grade=Grade(passed=True, strict=False, reasons=["columns"])),
        a_record(seed=3, grade=Grade(passed=False, strict=False, reasons=["sum"])),
        a_record(seed=4, outcome=RunOutcome.TIMED_OUT),
        a_record(seed=5, outcome=RunOutcome.INFRASTRUCTURE_ERROR, grade=None, usage=None),
    ]

    summary = BaselineSummaryBuilder().build(records)

    assert summary["infrastructureErrors"] == 1
    assert summary["passRate"] == 0.5
    assert summary["strictRate"] == 0.25
    case = summary["cases"]
    assert isinstance(case, dict)
    assert case["quarterly-to-monthly-difficulty-1"]["bySeed"] == {
        "1": "strict",
        "2": "passed",
        "3": "failed",
        "4": "timed_out",
    }


def test_runs_under_different_prompts_are_flagged_as_not_one_baseline() -> None:
    other = a_configuration(promptFingerprints={"DECOMPOSITION": "ba9876543210"})
    records = [a_record(seed=1), a_record(seed=2, configuration=other)]

    configuration = BaselineSummaryBuilder().build(records)["configuration"]

    assert isinstance(configuration, dict)
    assert configuration["consistent"] is False
    assert configuration["seeds"] == [1, 2]


def test_the_table_reads_one_line_per_case_and_an_overall_line() -> None:
    table = BaselineSummaryBuilder().table([a_record(seed=1), a_record(seed=2)])

    assert table.splitlines() == [
        "quarterly-to-monthly-difficulty-1    2/2 passed, 2 strict, 0 timed out, median 120.0s",
        "overall pass rate 1.0, strict 1.0, 0 infrastructure errors",
    ]


def test_a_run_refuses_a_core_server_not_in_eval_mode_on_its_seed() -> None:
    assert configuration_problems(a_configuration(), 42) == []
    assert configuration_problems(a_configuration(mode="DEV", seed=7), 42) == [
        "mode is DEV, not EVAL",
        "seed is 7, not 42",
    ]


def test_cases_are_discovered_in_id_order_and_unknown_ids_are_refused() -> None:
    assert [case.id for case in discover_cases(CASES, [])] == sorted(
        path.parent.name for path in CASES.glob("*/case.yaml")
    )
    with pytest.raises(ValueError, match="no-such-case"):
        discover_cases(CASES, ["no-such-case"])


def a_prompts_response(**templates: str) -> JsonObject:
    return {
        "EVALUATION": {
            "fingerprint": "ebbb92238368",
            "templates": templates
            or {"review/evaluation-system": "Judge it.", "review/evaluation-user": "<result>"},
        }
    }


def test_each_fingerprints_templates_are_saved_under_its_kind_and_fingerprint(
    tmp_path: Path,
) -> None:
    directory = ResultStore(tmp_path).save_prompts(prompt_sets(a_prompts_response()))

    saved = directory / "EVALUATION-ebbb92238368" / "review"
    assert (saved / "evaluation-system.st").read_text(encoding="utf-8") == "Judge it.\n"
    assert (saved / "evaluation-user.st").read_text(encoding="utf-8") == "<result>\n"


def test_a_template_name_that_would_escape_the_prompts_directory_is_refused() -> None:
    with pytest.raises(CoreServerError, match="unsafe"):
        prompt_sets(a_prompts_response(**{"../../evil": "x"}))
