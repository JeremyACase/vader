"""Aggregates a baseline's run records into the summary a later run is compared against."""

from __future__ import annotations

import statistics
from collections.abc import Callable

from eval_runner.run_record import RunOutcome, RunRecord

# The configuration fields that must agree across every run for them to form one baseline; the
# seed is left out because it varies by design.
CONSISTENT_FIELDS = ("mode", "model", "temperature", "contextTokens", "maxOutputTokens")


class BaselineSummaryBuilder:
    def build(self, records: list[RunRecord]) -> dict[str, object]:
        scored = [record for record in records if record.scored()]
        return {
            "runs": len(records),
            "infrastructureErrors": len(records) - len(scored),
            "passRate": rate(scored, RunRecord.passed),
            "strictRate": rate(scored, RunRecord.strict),
            "configuration": configuration_summary(scored),
            "cases": {
                case_id: case_summary(case_records)
                for case_id, case_records in by_case(scored).items()
            },
        }

    def table(self, records: list[RunRecord]) -> str:
        scored = [record for record in records if record.scored()]
        rows = [
            f"{case_id:<28} {sum(r.passed() for r in rows)}/{len(rows)} passed, "
            f"{sum(r.strict() for r in rows)} strict, "
            f"{sum(r.outcome == RunOutcome.TIMED_OUT for r in rows)} timed out, "
            f"median {median([r.elapsed_seconds for r in rows])}s"
            for case_id, rows in by_case(scored).items()
        ]
        footer = (
            f"overall pass rate {rate(scored, RunRecord.passed)}, "
            f"strict {rate(scored, RunRecord.strict)}, "
            f"{len(records) - len(scored)} infrastructure errors"
        )
        return "\n".join([*rows, footer])


def by_case(records: list[RunRecord]) -> dict[str, list[RunRecord]]:
    grouped: dict[str, list[RunRecord]] = {}
    for record in records:
        grouped.setdefault(record.case_id, []).append(record)
    return dict(sorted(grouped.items()))


def case_summary(records: list[RunRecord]) -> dict[str, object]:
    usages = [record.usage for record in records if record.usage is not None]
    return {
        "level": records[0].level,
        "runs": len(records),
        "passed": sum(record.passed() for record in records),
        "strict": sum(record.strict() for record in records),
        "timedOut": sum(record.outcome == RunOutcome.TIMED_OUT for record in records),
        "medianElapsedSeconds": median([record.elapsed_seconds for record in records]),
        "medianPromptTokens": median([usage.prompt_tokens for usage in usages]),
        "medianTurns": median([usage.turns for usage in usages]),
        # Per seed, so a later run can be compared pair by pair on the same seeds.
        "bySeed": {str(record.seed): verdict(record) for record in records},
    }


def verdict(record: RunRecord) -> str:
    """strict, passed or failed for a graded run; otherwise why it wasn't graded."""
    labels = {(True, True): "strict", (True, False): "passed"}
    otherwise = "failed" if record.outcome == RunOutcome.GRADED else record.outcome.value
    return labels.get((record.passed(), record.strict()), otherwise)


def configuration_summary(records: list[RunRecord]) -> dict[str, object]:
    configurations = [record.configuration for record in records]
    distinct = {
        repr([configuration.get(field) for field in CONSISTENT_FIELDS])
        + repr(configuration.get("promptFingerprints"))
        for configuration in configurations
    }
    first = configurations[0] if configurations else {}
    return {
        **{field: first.get(field) for field in CONSISTENT_FIELDS},
        "promptFingerprints": first.get("promptFingerprints"),
        "seeds": sorted({record.seed for record in records}),
        "consistent": len(distinct) <= 1,
    }


def rate(records: list[RunRecord], counted: Callable[[RunRecord], bool]) -> float | None:
    return round(sum(counted(record) for record in records) / len(records), 3) if records else None


def median(values: list[float] | list[int]) -> float | None:
    return round(statistics.median(values), 1) if values else None
