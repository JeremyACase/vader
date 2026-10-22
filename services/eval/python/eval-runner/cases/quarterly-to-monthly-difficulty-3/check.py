"""Grades one run of quarterly-to-monthly-difficulty-3 from the files it delivered.

A run passes when it delivered monthly_metrics.csv naming each of the input's months exactly once,
with revenue treated as a flow -- every quarter's months summing to that quarter's revenue -- and
headcount treated as a stock: each quarter's last month carries that quarter's headcount, and no
month leaves the range between the quarter-end headcounts either side of it. Those invariants hold
for any sensible stock conversion and fail for a headcount divided or summed like revenue. Months
are judged by the month each label names, however it is spelled, and columns by what they hold.
It is also strict when it followed the request to the letter: exactly the requested columns,
months spelled YYYY-MM and in chronological order, an even revenue split, and headcount
interpolated linearly as asked.

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

DELIVERABLE = "monthly_metrics.csv"
INPUT = "quarterly_metrics.csv"
EXPECTED_COLUMNS = ["month", "revenue", "headcount"]
QUARTER_PATTERN = re.compile(r"(\d{4})-Q([1-4])")
# Leaves room for a run that rounds each month's revenue to cents.
QUARTER_SUM_TOLERANCE = 0.015
MONTH_TOLERANCE = 0.005


@dataclass(frozen=True)
class CheckResult:
    passed: bool
    strict: bool
    reasons: list[str]


@dataclass(frozen=True)
class MonthlyDeliverable:
    """The delivered file as read, and its revenue and headcount as numbers in file order."""

    table: MonthlyTable
    revenue: list[float]
    headcount: list[float]


@dataclass(frozen=True)
class QuarterlyRow:
    revenue: float
    headcount: float


@dataclass(frozen=True)
class ExpectedMonth:
    """What the request asks of one month, and the range any stock conversion keeps it in."""

    revenue: float
    headcount: float
    headcount_low: float
    headcount_high: float
    quarter_end: bool


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
    table = read_monthly_table(path, ["revenue", "headcount"])
    return MonthlyDeliverable(
        table=table,
        revenue=[parse_number("revenue", text) for text in table.values["revenue"]],
        headcount=[parse_number("headcount", text) for text in table.values["headcount"]],
    )


def parse_number(column: str, text: str) -> float:
    try:
        return float(text)
    except ValueError as error:
        raise DeliverableError(f"{column} {text!r} is not a number") from error


def read_quarterly(path: Path) -> dict[pd.Period, QuarterlyRow]:
    frame = pd.read_csv(path, dtype=str)
    rows = zip(frame["quarter"], frame["revenue"], frame["headcount"], strict=True)
    return {
        parse_quarter(quarter): QuarterlyRow(revenue=float(revenue), headcount=float(headcount))
        for quarter, revenue, headcount in sorted(rows)
    }


def parse_quarter(value: str) -> pd.Period:
    match = QUARTER_PATTERN.fullmatch(value.strip())
    if match is None:
        raise ValueError(f"input quarter {value!r} is not formatted YYYY-Qn")
    return pd.Period(year=int(match.group(1)), quarter=int(match.group(2)), freq="Q")


def expected_monthly(quarterly: dict[pd.Period, QuarterlyRow]) -> dict[pd.Period, ExpectedMonth]:
    """The conversion the request asks for, keyed by month in chronological order. Each quarter's
    headcount is interpolated from the one before it; the first quarter's from itself."""
    rows = list(quarterly.items())
    previous_headcounts = [rows[0][1].headcount] + [row.headcount for _, row in rows[:-1]]
    return {
        month: expected_month(row, previous, offset)
        for (quarter, row), previous in zip(rows, previous_headcounts, strict=True)
        for offset, month in enumerate(months_of(quarter))
    }


def expected_month(row: QuarterlyRow, previous_headcount: float, offset: int) -> ExpectedMonth:
    step = (row.headcount - previous_headcount) / 3
    return ExpectedMonth(
        revenue=row.revenue / 3,
        headcount=previous_headcount + step * (offset + 1),
        headcount_low=min(previous_headcount, row.headcount),
        headcount_high=max(previous_headcount, row.headcount),
        quarter_end=offset == 2,
    )


def months_of(quarter: pd.Period) -> list[pd.Period]:
    first = quarter.asfreq("M", how="start")
    return [first + offset for offset in range(3)]


def structural_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, ExpectedMonth]
) -> list[str]:
    return (
        coverage_problems(deliverable.table.months, list(expected))
        + quarter_sum_problems(deliverable, expected)
        + quarter_end_headcount_problems(deliverable, expected)
        + headcount_range_problems(deliverable, expected)
    )


def quarter_sum_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, ExpectedMonth]
) -> list[str]:
    delivered = quarter_sums(zip(deliverable.table.months, deliverable.revenue, strict=True))
    wanted = quarter_sums(
        (month, month_expected.revenue) for month, month_expected in expected.items()
    )
    return [
        f"{quarter} revenue sums to {delivered.get(quarter, 0.0):.2f}, expected {total:.2f}"
        for quarter, total in wanted.items()
        if abs(delivered.get(quarter, 0.0) - total) > QUARTER_SUM_TOLERANCE
    ]


def quarter_sums(monthly: Iterable[tuple[pd.Period, float]]) -> dict[pd.Period, float]:
    sums: dict[pd.Period, float] = {}
    for month, revenue in monthly:
        quarter = month.asfreq("Q")
        sums[quarter] = sums.get(quarter, 0.0) + revenue
    return sums


def delivered_headcounts(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, ExpectedMonth]
) -> list[tuple[pd.Period, float, ExpectedMonth]]:
    """Each delivered month's headcount beside what was expected of it; months that shouldn't be
    there at all are left to the coverage check."""
    return [
        (month, headcount, expected[month])
        for month, headcount in zip(deliverable.table.months, deliverable.headcount, strict=True)
        if month in expected
    ]


def quarter_end_headcount_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, ExpectedMonth]
) -> list[str]:
    wrong = [
        f"{month} is {headcount:g}, expected {month_expected.headcount:g}"
        for month, headcount, month_expected in delivered_headcounts(deliverable, expected)
        if month_expected.quarter_end
        and abs(headcount - month_expected.headcount) > MONTH_TOLERANCE
    ]
    return [f"quarter-end headcount differs from the input: {wrong}"] if wrong else []


def headcount_range_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, ExpectedMonth]
) -> list[str]:
    outside = [
        str(month)
        for month, headcount, month_expected in delivered_headcounts(deliverable, expected)
        if not within_range(headcount, month_expected)
    ]
    return (
        [f"headcount outside the quarter-end headcounts either side of it: {outside}"]
        if outside
        else []
    )


def within_range(headcount: float, month_expected: ExpectedMonth) -> bool:
    low = month_expected.headcount_low - MONTH_TOLERANCE
    high = month_expected.headcount_high + MONTH_TOLERANCE
    return low <= headcount <= high


def strict_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, ExpectedMonth]
) -> list[str]:
    uneven = [
        str(month)
        for month, revenue in zip(deliverable.table.months, deliverable.revenue, strict=True)
        if abs(revenue - expected[month].revenue) > MONTH_TOLERANCE
    ]
    not_interpolated = [
        str(month)
        for month, headcount, month_expected in delivered_headcounts(deliverable, expected)
        if abs(headcount - month_expected.headcount) > MONTH_TOLERANCE
    ]
    return (
        presentation_problems(deliverable.table, EXPECTED_COLUMNS)
        + ([f"revenue not split evenly from its quarter: {uneven}"] if uneven else [])
        + ([f"headcount not interpolated linearly: {not_interpolated}"] if not_interpolated else [])
    )


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
