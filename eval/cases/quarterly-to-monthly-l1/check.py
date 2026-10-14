"""Grades one run of quarterly-to-monthly-l1 from the files it delivered.

A run passes when it delivered monthly_revenue.csv covering exactly the input's months, in order,
with every quarter's months summing to that quarter's revenue -- the invariant any correct
conversion of a flow keeps, whatever else it gets wrong. It is also strict when it followed the
request to the letter: exactly the requested columns, and an even three-way split.

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

DELIVERABLE = "monthly_revenue.csv"
INPUT = "quarterly_revenue.csv"
EXPECTED_COLUMNS = ["month", "revenue"]
MONTH_PATTERN = re.compile(r"\d{4}-\d{2}")
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
    """The delivered file, parsed: its column names as written, and its rows in file order."""

    columns: list[str]
    months: list[pd.Period]
    revenue: list[float]


class DeliverableError(Exception):
    """The delivered file is missing or unreadable, so nothing else about it can be judged."""


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
    if not path.is_file():
        raise DeliverableError(f"{DELIVERABLE} was not delivered")
    frame = read_csv(path)
    missing = [column for column in EXPECTED_COLUMNS if column not in frame.columns]
    if missing:
        raise DeliverableError(f"{DELIVERABLE} is missing columns {missing}")
    return MonthlyDeliverable(
        columns=list(frame.columns),
        months=[parse_month(value) for value in frame["month"]],
        revenue=[parse_revenue(value) for value in frame["revenue"]],
    )


def read_csv(path: Path) -> pd.DataFrame:
    try:
        frame = pd.read_csv(path, dtype=str, skipinitialspace=True)
    except (pd.errors.ParserError, pd.errors.EmptyDataError, UnicodeDecodeError) as error:
        raise DeliverableError(f"{DELIVERABLE} is not a readable CSV: {error}") from error
    return frame.rename(columns=lambda column: str(column).strip())


def parse_month(value: object) -> pd.Period:
    text = str(value).strip()
    if not MONTH_PATTERN.fullmatch(text):
        raise DeliverableError(f"month {text!r} is not formatted YYYY-MM")
    return pd.Period(text, freq="M")


def parse_revenue(value: object) -> float:
    try:
        return float(str(value).strip())
    except ValueError as error:
        raise DeliverableError(f"revenue {value!r} is not a number") from error


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
    return (
        coverage_problems(deliverable.months, list(expected))
        + order_problems(deliverable.months)
        + quarter_sum_problems(deliverable, expected)
    )


def coverage_problems(months: list[pd.Period], expected_months: list[pd.Period]) -> list[str]:
    delivered = set(months)
    missing = [str(month) for month in expected_months if month not in delivered]
    unexpected = sorted({str(month) for month in delivered if month not in set(expected_months)})
    duplicated = sorted({str(month) for month in months if months.count(month) > 1})
    return (
        ([f"missing months {missing}"] if missing else [])
        + ([f"unexpected months {unexpected}"] if unexpected else [])
        + ([f"duplicated months {duplicated}"] if duplicated else [])
    )


def order_problems(months: list[pd.Period]) -> list[str]:
    in_order = all(earlier < later for earlier, later in zip(months, months[1:], strict=False))
    return [] if in_order else ["months are not in chronological order"]


def quarter_sum_problems(
    deliverable: MonthlyDeliverable, expected: dict[pd.Period, float]
) -> list[str]:
    delivered = quarter_sums(zip(deliverable.months, deliverable.revenue, strict=True))
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
    columns = (
        []
        if deliverable.columns == EXPECTED_COLUMNS
        else [f"columns are {deliverable.columns}, requested exactly {EXPECTED_COLUMNS}"]
    )
    uneven = [
        str(month)
        for month, revenue in zip(deliverable.months, deliverable.revenue, strict=True)
        if abs(revenue - expected[month]) > MONTH_TOLERANCE
    ]
    split = [f"months not split evenly from their quarter: {uneven}"] if uneven else []
    return columns + split


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
