"""Grades one run of quarterly-to-monthly-difficulty-2 from the files it delivered.

A run passes when it delivered monthly_revenue.csv naming each of the input's months exactly once,
with every month in whole cents and within two cents of an even split, and every quarter's
months adding up to that quarter's revenue to the cent -- the invariant that rounding each month
on its own breaks whenever a quarter doesn't divide by three. Two cents, because any remainder
placement (rounding down and giving the last month the rest, say) is a correct conversion. Months
are judged by the month each label names, however it is spelled, and columns by what they hold.
It is also strict when it followed the request to the letter: exactly the requested columns,
months spelled YYYY-MM and in chronological order, revenue written with two decimal places, the
first two months rounded to the nearest cent and the last month taking the rest.

Amounts are compared as whole cents, never floats, so "exact" means exact. Prints one JSON
object -- {"passed": ..., "strict": ..., "reasons": [...]} -- and exits 0 whether or not the run
passed, so a non-zero exit always means the checker itself failed. Needs only the standard
library and pandas, so it runs in the same image as the sandbox.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from decimal import Decimal, InvalidOperation
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
TWO_DECIMALS_PATTERN = re.compile(r"-?\d+\.\d{2}")
CENT = Decimal("0.01")
# How far from a whole cent an amount may be and still be one: a cent amount computed in binary
# floating point and written at full precision, like 41666.670000000006, is still that cent.
FLOAT_NOISE = Decimal("0.000001")


@dataclass(frozen=True)
class CheckResult:
    passed: bool
    strict: bool
    reasons: list[str]


@dataclass(frozen=True)
class MonthlyDeliverable:
    """The delivered file as read, and its revenue in cents in file order."""

    table: MonthlyTable
    revenue_cents: list[int]


def check(deliverables: Path, inputs: Path) -> CheckResult:
    try:
        deliverable = read_deliverable(deliverables / DELIVERABLE)
    except DeliverableError as error:
        return CheckResult(passed=False, strict=False, reasons=[str(error)])
    quarterly = read_quarterly(inputs / INPUT)
    structural = structural_problems(deliverable, quarterly)
    strict_only = [] if structural else strict_problems(deliverable, quarterly)
    return CheckResult(
        passed=not structural,
        strict=not structural and not strict_only,
        reasons=structural + strict_only,
    )


def read_deliverable(path: Path) -> MonthlyDeliverable:
    table = read_monthly_table(path, ["revenue"])
    return MonthlyDeliverable(
        table=table, revenue_cents=[parse_cents(text) for text in table.values["revenue"]]
    )


def parse_cents(text: str) -> int:
    amount = parse_decimal(text)
    if not amount.is_finite() or abs(amount - amount.quantize(CENT)) > FLOAT_NOISE:
        raise DeliverableError(f"revenue {text!r} is not an amount in whole cents")
    return int(amount.quantize(CENT) * 100)


def parse_decimal(text: str) -> Decimal:
    try:
        return Decimal(text)
    except InvalidOperation as error:
        raise DeliverableError(f"revenue {text!r} is not a number") from error


def read_quarterly(path: Path) -> dict[pd.Period, int]:
    frame = pd.read_csv(path, dtype=str)
    return {
        parse_quarter(quarter): int(Decimal(revenue.strip()) * 100)
        for quarter, revenue in sorted(zip(frame["quarter"], frame["revenue"], strict=True))
    }


def parse_quarter(value: str) -> pd.Period:
    match = QUARTER_PATTERN.fullmatch(value.strip())
    if match is None:
        raise ValueError(f"input quarter {value!r} is not formatted YYYY-Qn")
    return pd.Period(year=int(match.group(1)), quarter=int(match.group(2)), freq="Q")


def expected_monthly(quarterly: dict[pd.Period, int]) -> dict[pd.Period, int]:
    """The split the request asks for, in cents, keyed by month in chronological order."""
    return {
        month: cents
        for quarter, total in quarterly.items()
        for month, cents in zip(months_of(quarter), split_of(total), strict=True)
    }


def split_of(total_cents: int) -> list[int]:
    # (total + 1) // 3 is a third rounded to the nearest cent; a third is never exactly half a
    # cent, so no tie-breaking rule is needed.
    share = (total_cents + 1) // 3
    return [share, share, total_cents - 2 * share]


def months_of(quarter: pd.Period) -> list[pd.Period]:
    first = quarter.asfreq("M", how="start")
    return [first + offset for offset in range(3)]


def structural_problems(
    deliverable: MonthlyDeliverable, quarterly: dict[pd.Period, int]
) -> list[str]:
    return (
        coverage_problems(deliverable.table.months, list(expected_monthly(quarterly)))
        + quarter_sum_problems(deliverable, quarterly)
        + uneven_split_problems(deliverable, quarterly)
    )


def quarter_sum_problems(
    deliverable: MonthlyDeliverable, quarterly: dict[pd.Period, int]
) -> list[str]:
    delivered = quarter_sums(deliverable)
    return [
        f"{quarter} sums to {format_cents(delivered.get(quarter, 0))}, "
        f"expected exactly {format_cents(total)}"
        for quarter, total in quarterly.items()
        if delivered.get(quarter, 0) != total
    ]


def quarter_sums(deliverable: MonthlyDeliverable) -> dict[pd.Period, int]:
    sums: dict[pd.Period, int] = {}
    for month, cents in zip(deliverable.table.months, deliverable.revenue_cents, strict=True):
        quarter = month.asfreq("Q")
        sums[quarter] = sums.get(quarter, 0) + cents
    return sums


def uneven_split_problems(
    deliverable: MonthlyDeliverable, quarterly: dict[pd.Period, int]
) -> list[str]:
    # |cents - total / 3| <= 2, kept in integers as |3 * cents - total| <= 6.
    uneven = [
        str(month)
        for month, cents in zip(deliverable.table.months, deliverable.revenue_cents, strict=True)
        if abs(3 * cents - quarterly.get(month.asfreq("Q"), 0)) > 6
    ]
    return [f"months more than two cents from an even split: {uneven}"] if uneven else []


def strict_problems(deliverable: MonthlyDeliverable, quarterly: dict[pd.Period, int]) -> list[str]:
    unformatted = [
        text
        for text in deliverable.table.values["revenue"]
        if not TWO_DECIMALS_PATTERN.fullmatch(text)
    ]
    formatting = (
        [f"revenue not written with two decimal places: {unformatted}"] if unformatted else []
    )
    expected = expected_monthly(quarterly)
    misplaced = [
        str(month)
        for month, cents in zip(deliverable.table.months, deliverable.revenue_cents, strict=True)
        if cents != expected[month]
    ]
    remainder = (
        [f"not rounded to the nearest cent with the rest on the last month: {misplaced}"]
        if misplaced
        else []
    )
    presentation = presentation_problems(deliverable.table, EXPECTED_COLUMNS)
    return presentation + formatting + remainder


def format_cents(cents: int) -> str:
    return f"{Decimal(cents) / 100:.2f}"


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
