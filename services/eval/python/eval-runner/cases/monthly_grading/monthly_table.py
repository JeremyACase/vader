"""Reads a delivered monthly CSV by what its columns hold, not by exactly how they are named.

Each value column the case needs is found by its role's name anywhere in a header, ignoring case
(`Revenue` and `monthly_revenue` both hold revenue); the month column is the first remaining
column whose header mentions a month, date or period, or else the first remaining column. Every
label must name a calendar month however it is spelled. One that names none leaves the run with
no way to tell which month a number belongs to, which no other check can make up for.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pandas as pd

from monthly_grading.month_label_parser_chain import resolve_month

MONTH_HEADER_HINTS = ("month", "date", "period")


class DeliverableError(Exception):
    """The delivered file is missing or unreadable, so nothing else about it can be judged."""


@dataclass(frozen=True)
class MonthlyTable:
    """The delivered file, read: its headers and month labels as written, the month each label
    names, and each requested value column's cells as written, all in file order."""

    columns: list[str]
    labels: list[str]
    months: list[pd.Period]
    values: dict[str, list[str]]


def read_monthly_table(path: Path, roles: list[str]) -> MonthlyTable:
    if not path.is_file():
        raise DeliverableError(f"{path.name} was not delivered")
    frame = read_csv(path)
    value_columns = {role: value_column(frame, role, path.name) for role in roles}
    labels = cells(frame, month_column(frame, set(value_columns.values()), path.name))
    return MonthlyTable(
        columns=list(frame.columns),
        labels=labels,
        months=[month_named_by(label) for label in labels],
        values={role: cells(frame, column) for role, column in value_columns.items()},
    )


def read_csv(path: Path) -> pd.DataFrame:
    try:
        frame = pd.read_csv(path, dtype=str, skipinitialspace=True)
    except (pd.errors.ParserError, pd.errors.EmptyDataError, UnicodeDecodeError) as error:
        raise DeliverableError(f"{path.name} is not a readable CSV: {error}") from error
    return frame.rename(columns=lambda column: str(column).strip())


def value_column(frame: pd.DataFrame, role: str, file_name: str) -> str:
    """The column named exactly for the role, else the first whose header contains it."""
    matching = [column for column in frame.columns if role in normalized(column)]
    if not matching:
        raise DeliverableError(f"{file_name} has no {role} column")
    return min(matching, key=lambda column: normalized(column) != role)


def month_column(frame: pd.DataFrame, value_columns: set[str], file_name: str) -> str:
    remaining = [column for column in frame.columns if column not in value_columns]
    if not remaining:
        raise DeliverableError(f"{file_name} has no month column")
    hinted = [
        column
        for column in remaining
        if any(hint in normalized(column) for hint in MONTH_HEADER_HINTS)
    ]
    return (hinted or remaining)[0]


def normalized(column: str) -> str:
    return column.strip().lower()


def cells(frame: pd.DataFrame, column: str) -> list[str]:
    return [str(value).strip() for value in frame[column]]


def month_named_by(label: str) -> pd.Period:
    month = resolve_month(label)
    if month is None:
        raise DeliverableError(f"month {label!r} does not name a calendar month")
    return month
