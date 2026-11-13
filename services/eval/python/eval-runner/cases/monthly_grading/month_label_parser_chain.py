"""Reads which calendar month a delivered label names, however it is spelled.

A run is graded on the month a label means, not on its spelling: `2024-04`, `2024-04-30`,
`Apr 2024`, `04/2024` and `2024-Q2-01` all name April 2024. Each parser in the chain recognises
one family of spellings and passes on the rest. Quarter spellings come first, so no date parser
reads `2024-Q1-01` some other way. Only unambiguous spellings are accepted (`01/02/2024` could be
either of two months), so a label the chain can't be sure of is left unresolved, never guessed.
"""

from __future__ import annotations

import calendar
import re
from collections.abc import Callable

import pandas as pd

MonthParser = Callable[[str], pd.Period | None]

CANONICAL_MONTH = re.compile(r"\d{4}-\d{2}")
QUARTER_MONTH = re.compile(r"(\d{4})\s*-?\s*Q([1-4])\s*[-_ ]?\s*M?(\d{1,2})", re.IGNORECASE)
YEAR_FIRST_MONTH = re.compile(r"(\d{4})[-/.](\d{1,2})")
MONTH_FIRST_MONTH = re.compile(r"(\d{1,2})[-/.](\d{4})")
YEAR_FIRST_DATE = re.compile(r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T ].*)?")
NAME_FIRST_MONTH = re.compile(r"([A-Za-z]+)\.?[\s,/-]*(\d{4})")
YEAR_FIRST_NAME = re.compile(r"(\d{4})[\s,/-]*([A-Za-z]+)\.?")
MONTH_NUMBERS_BY_NAME = {
    name.lower(): number
    for names in (calendar.month_name, calendar.month_abbr)
    for number, name in enumerate(names)
    if name
} | {"sept": 9}


def resolve_month(label: str) -> pd.Period | None:
    """The month the label names, or None when no parser in the chain can tell."""
    text = label.strip()
    candidates = (parser(text) for parser in MONTH_PARSER_CHAIN)
    return next((month for month in candidates if month is not None), None)


def is_canonical(label: str) -> bool:
    """Whether the label is spelled YYYY-MM, as every case's request asks."""
    return CANONICAL_MONTH.fullmatch(label.strip()) is not None


def calendar_month(year: int, month: int) -> pd.Period | None:
    return pd.Period(year=year, month=month, freq="M") if 1 <= month <= 12 else None


def quarter_month(text: str) -> pd.Period | None:
    match = QUARTER_MONTH.fullmatch(text)
    return None if match is None else month_of_quarter(*(int(part) for part in match.groups()))


def month_of_quarter(year: int, quarter: int, ordinal: int) -> pd.Period | None:
    """The quarter's first, second or third month for an ordinal of 1-3; otherwise the ordinal is
    read as a month of the year, which must then fall inside the quarter."""
    first = 3 * (quarter - 1) + 1
    month = first + ordinal - 1 if 1 <= ordinal <= 3 else ordinal
    return calendar_month(year, month) if first <= month < first + 3 else None


def year_first_month(text: str) -> pd.Period | None:
    match = YEAR_FIRST_MONTH.fullmatch(text)
    return None if match is None else calendar_month(int(match.group(1)), int(match.group(2)))


def month_first_month(text: str) -> pd.Period | None:
    match = MONTH_FIRST_MONTH.fullmatch(text)
    return None if match is None else calendar_month(int(match.group(2)), int(match.group(1)))


def year_first_date(text: str) -> pd.Period | None:
    match = YEAR_FIRST_DATE.fullmatch(text)
    return None if match is None else month_of_date(*(int(part) for part in match.groups()))


def month_of_date(year: int, month: int, day: int) -> pd.Period | None:
    is_date = 1 <= month <= 12 and 1 <= day <= calendar.monthrange(year, month)[1]
    return calendar_month(year, month) if is_date else None


def name_first_month(text: str) -> pd.Period | None:
    match = NAME_FIRST_MONTH.fullmatch(text)
    return None if match is None else named_month(int(match.group(2)), match.group(1))


def year_first_name(text: str) -> pd.Period | None:
    match = YEAR_FIRST_NAME.fullmatch(text)
    return None if match is None else named_month(int(match.group(1)), match.group(2))


def named_month(year: int, name: str) -> pd.Period | None:
    number = MONTH_NUMBERS_BY_NAME.get(name.lower())
    return None if number is None else calendar_month(year, number)


MONTH_PARSER_CHAIN: tuple[MonthParser, ...] = (
    quarter_month,
    year_first_month,
    month_first_month,
    year_first_date,
    name_first_month,
    year_first_name,
)
