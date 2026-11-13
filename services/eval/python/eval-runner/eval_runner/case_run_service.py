"""Runs one benchmark case once against core-server and records the result."""

from __future__ import annotations

import shutil
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from eval_runner.benchmark_case import BenchmarkCase
from eval_runner.checker_adapter import CheckerAdapter, CheckerError
from eval_runner.interface_core_server import CoreServerError, InterfaceCoreServer, JsonObject
from eval_runner.run_progress_reporter import RunProgressReporter
from eval_runner.run_record import Grade, RunOutcome, RunRecord
from eval_runner.run_trace import RunTrace
from eval_runner.run_trace_collector import RunTraceCollector

SETTLED_STATUSES = frozenset({"SUCCEEDED", "FAILED"})


@dataclass(frozen=True)
class RunSettings:
    seed: int
    configuration: JsonObject
    run_timeout_seconds: float
    poll_interval_seconds: float


@dataclass(frozen=True)
class CaseRun:
    """A run's result, and what its workflow did to get there when that could be fetched."""

    record: RunRecord
    trace: RunTrace | None


class CaseRunService:
    """Submits a case's prompt with its inputs, waits for the workflow to settle, fetches the file
    it was asked to deliver, grades it with the case's checker and traces what the workflow did,
    totalling what it spent.

    The deliverable is the newest object with the requested filename that didn't exist before the
    prompt was submitted, which relies on runs against one core-server being sequential -- so a
    workflow still running at the timeout is cancelled, rather than left to compete with the next
    run for the LLM and to deliver files into it.
    """

    def __init__(
        self,
        core_server: InterfaceCoreServer,
        checker: CheckerAdapter,
        trace_collector: RunTraceCollector,
        clock: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
        progress: RunProgressReporter | None = None,
    ) -> None:
        self._core_server = core_server
        self._checker = checker
        self._trace_collector = trace_collector
        self._clock = clock
        self._sleep = sleep
        self._progress = progress

    def run(self, case: BenchmarkCase, settings: RunSettings, deliverables: Path) -> CaseRun:
        started = self._clock()
        try:
            case_run = self._run_and_grade(case, settings, deliverables, started)
        except CoreServerError as error:
            case_run = CaseRun(self._infrastructure_error(case, settings, started, error), None)
        return case_run

    def _run_and_grade(
        self, case: BenchmarkCase, settings: RunSettings, deliverables: Path, started: float
    ) -> CaseRun:
        existing = self._deliverable_ids(case)
        prompt_id = self._core_server.submit_prompt(case.prompt, case.inputs)
        workflow = self._await_settled(prompt_id, settings, started)
        elapsed = self._clock() - started
        status = workflow_status(workflow)
        self._cancel_if_unsettled(workflow, status)
        self._fetch_deliverable(case, existing, deliverables)
        grade, checker_error = self._grade(case, deliverables)
        trace = self._trace_of(workflow)
        record = RunRecord(
            case_id=case.id,
            difficulty=case.difficulty,
            seed=settings.seed,
            outcome=outcome_of(status, checker_error),
            workflow_status=status,
            elapsed_seconds=round(elapsed, 1),
            grade=grade,
            usage=None if trace is None else trace.usage(),
            configuration=settings.configuration,
            error=checker_error,
        )
        return CaseRun(record, trace)

    def _grade(self, case: BenchmarkCase, deliverables: Path) -> tuple[Grade | None, str | None]:
        """The run's grade, or why its checker could not give one."""
        graded: tuple[Grade | None, str | None]
        try:
            graded = (self._checker.grade(case, deliverables), None)
        except CheckerError as error:
            graded = (None, str(error))
        return graded

    def _await_settled(
        self, prompt_id: str, settings: RunSettings, started: float
    ) -> JsonObject | None:
        if self._progress is not None:
            self._progress.start()
        workflow = self._poll(prompt_id, started)
        while not self._settled_or_expired(workflow, settings, started):
            self._sleep(settings.poll_interval_seconds)
            workflow = self._poll(prompt_id, started)
        return workflow

    def _poll(self, prompt_id: str, started: float) -> JsonObject | None:
        workflow = self._core_server.latest_workflow(prompt_id)
        if self._progress is not None:
            self._progress.observe(workflow, self._clock() - started)
        return workflow

    def _settled_or_expired(
        self, workflow: JsonObject | None, settings: RunSettings, started: float
    ) -> bool:
        expired = self._clock() - started >= settings.run_timeout_seconds
        return workflow_status(workflow) in SETTLED_STATUSES or expired

    def _cancel_if_unsettled(self, workflow: JsonObject | None, status: str | None) -> None:
        """Best effort: failing to cancel never discards the run's grade."""
        if workflow is not None and status not in SETTLED_STATUSES:
            try:
                self._core_server.cancel_workflow(str(workflow["id"]))
            except CoreServerError:
                pass

    def _deliverable_ids(self, case: BenchmarkCase) -> set[str]:
        return {str(row["id"]) for row in self._deliverable_rows(case)}

    def _deliverable_rows(self, case: BenchmarkCase) -> list[JsonObject]:
        filters = {"originalFilename": case.deliverable, "sort-by-fields": "createdAt"}
        return self._core_server.query_all("object-metadata", filters)

    def _fetch_deliverable(self, case: BenchmarkCase, existing: set[str], directory: Path) -> None:
        shutil.rmtree(directory, ignore_errors=True)
        directory.mkdir(parents=True)
        produced = [row for row in self._deliverable_rows(case) if str(row["id"]) not in existing]
        if produced:
            content = self._core_server.download(str(produced[0]["id"]))
            (directory / case.deliverable).write_bytes(content)

    def _trace_of(self, workflow: JsonObject | None) -> RunTrace | None:
        """Best effort: the trace and the usage totalled from it only describe a run, so failing
        to fetch them never discards the run's grade."""
        trace: RunTrace | None = None
        try:
            trace = None if workflow is None else self._trace_collector.collect(workflow)
        except CoreServerError:
            trace = None
        return trace

    def _infrastructure_error(
        self, case: BenchmarkCase, settings: RunSettings, started: float, error: CoreServerError
    ) -> RunRecord:
        return RunRecord(
            case_id=case.id,
            difficulty=case.difficulty,
            seed=settings.seed,
            outcome=RunOutcome.INFRASTRUCTURE_ERROR,
            workflow_status=None,
            elapsed_seconds=round(self._clock() - started, 1),
            grade=None,
            usage=None,
            configuration=settings.configuration,
            error=str(error),
        )


def outcome_of(status: str | None, checker_error: str | None) -> RunOutcome:
    settled = RunOutcome.GRADED if status in SETTLED_STATUSES else RunOutcome.TIMED_OUT
    return settled if checker_error is None else RunOutcome.CHECKER_ERROR


def workflow_status(workflow: JsonObject | None) -> str | None:
    return None if workflow is None else str(workflow.get("status"))
