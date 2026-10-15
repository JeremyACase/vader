"""Grades one run of quarterly-to-monthly-l2 from the files it delivered.

A run passes when it delivered monthly_revenue.csv covering exactly the input's months, in order,
with every month in whole cents and within two cents of an even split, and every quarter's
months adding up to that quarter's revenue to the cent -- the invariant that rounding each month
on its own breaks whenever a quarter doesn't divide by three. Two cents, because any remainder
placement (rounding down and giving the last month the rest, say) is a correct conversion. It is
also strict when it followed the request to the letter: exactly the requested columns, revenue
written with two decimal places, the first two months rounded to the nearest cent and the last
month taking the rest.

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

DELIVERABLE = "monthly_revenue.csv"
INPUT = "quarterly_revenue.csv"
EXPECTED_COLUMNS = ["month", "revenue"]
MONTH_PATTERN = re.compile(r"\d{4}-\d{2}")
QUARTER_PATTERN = re.compile(r"(\d{4})-Q([1-4])")
TWO_DECIMALS_PATTERN = re.compile(r"-?\d+\.\d{2}")
CENT = Decimal("0.01")


@dataclass(frozen=True)
class CheckResult:
    passed: bool
    strict: bool
    reasons: list[str]


@dataclass(frozen=True)
class MonthlyDeliverable:
    """The delivered file, parsed: its column names, and its rows in file order -- each revenue
    both as written and in cents."""

    columns: list[str]
    months: list[pd.Period]
    revenue_text: list[str]
    revenue_cents: list[int]


class DeliverableError(Exception):
    """The delivered file is missing or unreadable, so nothing else about it can be judged."""


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
    if not path.is_file():
        raise DeliverableError(f"{DELIVERABLE} was not delivered")
    frame = read_csv(path)
    missing = [column for column in EXPECTED_COLUMNS if column not in frame.columns]
    if missing:
        raise DeliverableError(f"{DELIVERABLE} is missing columns {missing}")
    revenue_text = [str(value).strip() for value in frame["revenue"]]
    return MonthlyDeliverable(
        columns=list(frame.columns),
        months=[parse_month(value) for value in frame["month"]],
        revenue_text=revenue_text,
        revenue_cents=[parse_cents(text) for text in revenue_text],
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


def parse_cents(text: str) -> int:
    amount = parse_decimal(text)
    if not amount.is_finite() or amount != amount.quantize(CENT):
        raise DeliverableError(f"revenue {text!r} is not an amount in whole cents")
    return int(amount * 100)


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
        coverage_problems(deliverable.months, list(expected_monthly(quarterly)))
        + order_problems(deliverable.months)
        + quarter_sum_problems(deliverable, quarterly)
        + uneven_split_problems(deliverable, quarterly)
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
    for month, cents in zip(deliverable.months, deliverable.revenue_cents, strict=True):
        quarter = month.asfreq("Q")
        sums[quarter] = sums.get(quarter, 0) + cents
    return sums


def uneven_split_problems(
    deliverable: MonthlyDeliverable, quarterly: dict[pd.Period, int]
) -> list[str]:
    # |cents - total / 3| <= 2, kept in integers as |3 * cents - total| <= 6.
    uneven = [
        str(month)
        for month, cents in zip(deliverable.months, deliverable.revenue_cents, strict=True)
        if abs(3 * cents - quarterly.get(month.asfreq("Q"), 0)) > 6
    ]
    return [f"months more than two cents from an even split: {uneven}"] if uneven else []


def strict_problems(deliverable: MonthlyDeliverable, quarterly: dict[pd.Period, int]) -> list[str]:
    columns = (
        []
        if deliverable.columns == EXPECTED_COLUMNS
        else [f"columns are {deliverable.columns}, requested exactly {EXPECTED_COLUMNS}"]
    )
    unformatted = [
        text for text in deliverable.revenue_text if not TWO_DECIMALS_PATTERN.fullmatch(text)
    ]
    formatting = (
        [f"revenue not written with two decimal places: {unformatted}"] if unformatted else []
    )
    expected = expected_monthly(quarterly)
    misplaced = [
        str(month)
        for month, cents in zip(deliverable.months, deliverable.revenue_cents, strict=True)
        if cents != expected[month]
    ]
    remainder = (
        [f"not rounded to the nearest cent with the rest on the last month: {misplaced}"]
        if misplaced
        else []
    )
    return columns + formatting + remainder


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
