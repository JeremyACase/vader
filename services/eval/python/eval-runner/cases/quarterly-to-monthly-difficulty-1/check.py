"""Grades one run of quarterly-to-monthly-difficulty-1 from the files it delivered.

A run passes when it delivered monthly_revenue.csv naming each of the input's months exactly once,
with every quarter's months summing to that quarter's revenue -- the invariant any correct
conversion of a flow keeps, whatever else it gets wrong. Months are judged by the month each label
names, however it is spelled, and columns by what they hold. It is also strict when it followed
the request to the letter: exactly the requested columns, months spelled YYYY-MM and in
chronological order, and an even three-way split.

Prints one JSON object -- {"passed": ..., "strict": ..., "reasons": [...]} -- and exits 0 whether
or not the run passed, so a non-zero exit always means the checker itself failed. Needs only the
standard library and pandas, so it runs in the same image as the sandbox.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections.abc import Iterable
from dataclasses import asdict, dataclass
from pathlib import Path

import pandas as pd

# The grading every quarterly-to-monthly case shares lives beside the cases; a checker is loaded
# by its path or run as a script, so neither puts that directory on the import path for it.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from monthly_grading.monthly_table import (  # noqa: E402
    DeliverableError,
    MonthlyTable,
    read_monthly_table,
)
from monthly_grading.monthly_table_checks import (  # noqa: E402
    coverage_problems,
    presentation_problems,
)

DELIVERABLE = "monthly_revenue.csv"
INPUT = "quarterly_revenue.csv"
EXPECTED_COLUMNS = ["month", "revenue"]
QUARTER_PATTERN = re.compile(r"(\d{4})-Q([1-4])")
# Leaves room for a run that rounds each month to cents.
QUARTER_SUM_TOLERANCE = 0.015
MONTH_TOLERANCE = 0.005


@dataclass(frozen=True)
class CheckResult:
    passed: bool
    strict: bool
    reasons: list[str]


@dataclass(frozen=True)
class MonthlyDeliverable:
    """The delivered file as read, and its revenue as numbers in file order."""

    table: MonthlyTable
    revenue: list[float]


def check(deliverables: Path, inputs: Path) -> CheckResult:
    try:
        deliverable = read_deliverable(deliverables / DELIVERABLE)
    except DeliverableError as error:
        return CheckResult(passed=False, strict=False, reasons=[str(error)])
    expected = expected_monthly(read_quarterly(inputs / INPUT))
    structural = structural_problems(deliverable, expected)
    strict_only = [] if structural else strict_problems(deliverable, expected)
    return CheckResult(
        passed=not structural,
        strict=not structural and not strict_only,
        reasons=structural + strict_only,
    )


def read_deliverable(path: Path) -> MonthlyDeliverable:
    table = read_monthly_table(path, ["revenue"])
    return MonthlyDeliverable(
        table=table, revenue=[parse_revenue(text) for text in table.values["revenue"]]
    )


def parse_revenue(text: str) -> float:
    try:
        return float(text)
    except ValueError as error:
        raise DeliverableError(f"revenue {text!r} is not a number") from error


def read_quarterly(path: Path) -> dict[pd.Period, float]:
    frame = pd.read_csv(path, dtype=str)
    return {
        parse_quarter(quarter): float(revenue)
        for quarter, revenue in zip(frame["quarter"], frame["revenue"], strict=True)
    }


def parse_quarter(value: str) -> pd.Period:
    match = QUARTER_PATTERN.fullmatch(value.strip())
    if match is None:
        raise ValueError(f"input quarter {value!r} is not formatted YYYY-Qn")
    return pd.Period(year=int(match.group(1)), quarter=int(match.group(2)), freq="Q")


def expected_monthly(quarterly: dict[pd.Period, float]) -> dict[pd.Period, float]:
    """The even split the request asks for, keyed by month in chronological order."""
    return {
        month: revenue / 3
        for quarter, revenue in sorted(quarterly.items())
        for month in months_of(quarter)
    }


def months_of(quarter: pd.Period) -> list[pd.Period]:
    first = quarter.asfreq("M", how="start")
    return [first + offset for offset in range(3)]


def structural_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, float]
) -> list[str]:
    return coverage_problems(deliverable.table.months, list(expected)) + quarter_sum_problems(
        deliverable, expected
    )


def quarter_sum_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, float]
) -> list[str]:
    delivered = quarter_sums(zip(deliverable.table.months, deliverable.revenue, strict=True))
    return [
        f"{quarter} sums to {delivered.get(quarter, 0.0):.2f}, expected {total:.2f}"
        for quarter, total in quarter_sums(expected.items()).items()
        if abs(delivered.get(quarter, 0.0) - total) > QUARTER_SUM_TOLERANCE
    ]


def quarter_sums(monthly: Iterable[tuple[pd.Period, float]]) -> dict[pd.Period, float]:
    sums: dict[pd.Period, float] = {}
    for month, revenue in monthly:
        quarter = month.asfreq("Q")
        sums[quarter] = sums.get(quarter, 0.0) + revenue
    return sums


def strict_problems(deliverable: MonthlyDeliverable, expected: dict[pd.Period, float]) -> list[str]:
    uneven = [
        str(month)
        for month, revenue in zip(deliverable.table.months, deliverable.revenue, strict=True)
        if abs(revenue - expected[month]) > MONTH_TOLERANCE
    ]
    split = [f"months not split evenly from their quarter: {uneven}"] if uneven else []
    return presentation_problems(deliverable.table, EXPECTED_COLUMNS) + split


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    parser.add_argument("--deliverables", type=Path, required=True)
    parser.add_argument("--inputs", type=Path, required=True)
    arguments = parser.parse_args(argv)
    result = check(arguments.deliverables, arguments.inputs)
    print(json.dumps(asdict(result)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
