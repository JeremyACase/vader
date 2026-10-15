"""Runs one benchmark case once against core-server and records the result."""

from __future__ import annotations

import shutil
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from eval_runner.benchmark_case import BenchmarkCase
from eval_runner.checker_adapter import CheckerAdapter
from eval_runner.interface_core_server import CoreServerError, InterfaceCoreServer, JsonObject
from eval_runner.run_record import RunOutcome, RunRecord, RunUsage
from eval_runner.run_usage_collector import RunUsageCollector

SETTLED_STATUSES = frozenset({"SUCCEEDED", "FAILED"})


@dataclass(frozen=True)
class RunSettings:
    seed: int
    configuration: JsonObject
    run_timeout_seconds: float
    poll_interval_seconds: float


class CaseRunService:
    """Submits a case's prompt with its inputs, waits for the workflow to settle, fetches the file
    it was asked to deliver, grades it with the case's checker and totals what the run spent.

    The deliverable is the newest object with the requested filename that didn't exist before the
    prompt was submitted, which relies on runs against one core-server being sequential.
    """

    def __init__(
        self,
        core_server: InterfaceCoreServer,
        checker: CheckerAdapter,
        usage_collector: RunUsageCollector,
        clock: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self._core_server = core_server
        self._checker = checker
        self._usage_collector = usage_collector
        self._clock = clock
        self._sleep = sleep

    def run(self, case: BenchmarkCase, settings: RunSettings, deliverables: Path) -> RunRecord:
        started = self._clock()
        try:
            record = self._run_and_grade(case, settings, deliverables, started)
        except CoreServerError as error:
            record = self._infrastructure_error(case, settings, started, error)
        return record

    def _run_and_grade(
        self, case: BenchmarkCase, settings: RunSettings, deliverables: Path, started: float
    ) -> RunRecord:
        existing = self._deliverable_ids(case)
        prompt_id = self._core_server.submit_prompt(case.prompt, case.inputs)
        workflow = self._await_settled(prompt_id, settings, started)
        elapsed = self._clock() - started
        self._fetch_deliverable(case, existing, deliverables)
        status = workflow_status(workflow)
        return RunRecord(
            case_id=case.id,
            level=case.level,
            seed=settings.seed,
            outcome=RunOutcome.GRADED if status in SETTLED_STATUSES else RunOutcome.TIMED_OUT,
            workflow_status=status,
            elapsed_seconds=round(elapsed, 1),
            grade=self._checker.grade(case, deliverables),
            usage=self._usage_of(workflow),
            configuration=settings.configuration,
            error=None,
        )

    def _await_settled(
        self, prompt_id: str, settings: RunSettings, started: float
    ) -> JsonObject | None:
        workflow = self._core_server.latest_workflow(prompt_id)
        while not self._settled_or_expired(workflow, settings, started):
            self._sleep(settings.poll_interval_seconds)
            workflow = self._core_server.latest_workflow(prompt_id)
        return workflow

    def _settled_or_expired(
        self, workflow: JsonObject | None, settings: RunSettings, started: float
    ) -> bool:
        expired = self._clock() - started >= settings.run_timeout_seconds
        return workflow_status(workflow) in SETTLED_STATUSES or expired

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

    def _usage_of(self, workflow: JsonObject | None) -> RunUsage | None:
        """Best effort: usage only describes a run, so failing to total it never discards the
        run's grade."""
        usage: RunUsage | None = None
        try:
            usage = None if workflow is None else self._usage_collector.collect(str(workflow["id"]))
        except CoreServerError:
            usage = None
        return usage

    def _infrastructure_error(
        self, case: BenchmarkCase, settings: RunSettings, started: float, error: CoreServerError
    ) -> RunRecord:
        return RunRecord(
            case_id=case.id,
            level=case.level,
            seed=settings.seed,
            outcome=RunOutcome.INFRASTRUCTURE_ERROR,
            workflow_status=None,
            elapsed_seconds=round(self._clock() - started, 1),
            grade=None,
            usage=None,
            configuration=settings.configuration,
            error=str(error),
        )


def workflow_status(workflow: JsonObject | None) -> str | None:
    return None if workflow is None else str(workflow.get("status"))
