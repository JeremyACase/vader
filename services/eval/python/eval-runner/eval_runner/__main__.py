"""Command line: run the benchmark cases on one seed, or summarize a results directory.

  python -m eval_runner run --server URL --seed N --results DIR [--cases ID ...]
  python -m eval_runner summarize --results DIR

`run` refuses to start unless core-server is in EVAL mode on the requested seed, and exits 2 if
not, so a benchmark never records results under a configuration it didn't ask for.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from eval_runner.baseline_summary_builder import BaselineSummaryBuilder
from eval_runner.benchmark_case import BenchmarkCase, discover_cases
from eval_runner.case_run_service import CaseRunService, RunSettings
from eval_runner.checker_adapter import CheckerAdapter
from eval_runner.core_server_adapter import CoreServerAdapter
from eval_runner.interface_core_server import CoreServerError, JsonObject
from eval_runner.prompt_set import prompt_sets
from eval_runner.result_store import ResultStore
from eval_runner.run_progress_reporter import RunProgressReporter
from eval_runner.run_record import RunRecord
from eval_runner.run_usage_collector import RunUsageCollector

CASES_ROOT = Path(__file__).resolve().parent.parent / "cases"
MISCONFIGURED = 2


def main(argv: list[str]) -> int:
    arguments = parser().parse_args(argv)
    store = ResultStore(Path(arguments.results))
    return run(arguments, store) if arguments.command == "run" else summarize(store)


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="eval_runner")
    commands = root.add_subparsers(dest="command", required=True)
    run_command = commands.add_parser("run", help="run every pending case once on one seed")
    run_command.add_argument("--server", required=True, help="core-server base URL")
    run_command.add_argument("--seed", type=int, required=True)
    run_command.add_argument("--results", required=True)
    run_command.add_argument("--cases", nargs="*", default=[], help="case ids; default all")
    run_command.add_argument("--run-timeout-minutes", type=float, default=60.0)
    run_command.add_argument("--poll-seconds", type=float, default=15.0)
    summarize_command = commands.add_parser("summarize", help="write and print summary.json")
    summarize_command.add_argument("--results", required=True)
    return root


def run(arguments: argparse.Namespace, store: ResultStore) -> int:
    core_server = CoreServerAdapter(arguments.server)
    configuration = core_server.configuration()
    problems = configuration_problems(configuration, arguments.seed)
    if problems:
        print(f"core-server is not configured for this run: {problems}", file=sys.stderr)
    else:
        settings = RunSettings(
            seed=arguments.seed,
            configuration=configuration,
            run_timeout_seconds=arguments.run_timeout_minutes * 60,
            poll_interval_seconds=arguments.poll_seconds,
        )
        save_prompts(core_server, store)
        service = CaseRunService(
            core_server,
            CheckerAdapter(),
            RunUsageCollector(core_server),
            progress=RunProgressReporter(core_server),
        )
        pending = [
            case
            for case in discover_cases(CASES_ROOT, arguments.cases)
            if not store.is_done(case.id, arguments.seed)
        ]
        for case in pending:
            run_case(service, store, case, settings)
        print_seed_table(store, arguments.seed)
    return MISCONFIGURED if problems else 0


def save_prompts(core_server: CoreServerAdapter, store: ResultStore) -> None:
    """Best effort: the prompts explain a result but aren't part of it, so a core-server too old
    to report them still gets measured."""
    try:
        directory = store.save_prompts(prompt_sets(core_server.prompts()))
        print(f"prompts saved to {directory}", flush=True)
    except CoreServerError as error:
        print(f"prompts not saved: {error}", file=sys.stderr, flush=True)


def run_case(
    service: CaseRunService, store: ResultStore, case: BenchmarkCase, settings: RunSettings
) -> None:
    print(f"▶ {case.id} on seed {settings.seed}", flush=True)
    record = service.run(case, settings, store.deliverables_dir(case.id, settings.seed))
    store.save(record)
    print(f"  {describe(record)}", flush=True)


def print_seed_table(store: ResultStore, seed: int) -> None:
    """How this seed went, case by case, so a long run can be read as it goes."""
    records = [record for record in store.load_all() if record.seed == seed]
    print(f"Seed {seed}:\n{BaselineSummaryBuilder().table(records)}", flush=True)


def describe(record: RunRecord) -> str:
    grade = record.grade
    reasons = f" -- {'; '.join(grade.reasons)[:300]}" if grade and grade.reasons else ""
    error = f" -- {record.error}" if record.error else ""
    verdict = "strict pass" if record.strict() else "pass" if record.passed() else "fail"
    return (
        f"{verdict} ({record.outcome.value}, workflow {record.workflow_status}, "
        f"{record.elapsed_seconds}s){reasons}{error}"
    )


def configuration_problems(configuration: JsonObject, seed: int) -> list[str]:
    mode = configuration.get("mode")
    reported_seed = configuration.get("seed")
    return ([f"mode is {mode}, not EVAL"] if mode != "EVAL" else []) + (
        [f"seed is {reported_seed}, not {seed}"] if reported_seed != seed else []
    )


def summarize(store: ResultStore) -> int:
    records = store.load_all()
    builder = BaselineSummaryBuilder()
    path = store.save_summary(builder.build(records))
    print(builder.table(records))
    print(f"summary written to {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
