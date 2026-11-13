"""Checks the label parser chain reads every accepted spelling as the month it means, and
refuses the spellings it cannot be sure of rather than guessing."""

from __future__ import annotations

import pandas as pd
import pytest

from monthly_grading.month_label_parser_chain import is_canonical, resolve_month

APRIL_2024 = pd.Period(year=2024, month=4, freq="M")


@pytest.mark.parametrize(
    "label",
    [
        "2024-04",
        "2024-4",
        "2024/04",
        "04/2024",
        "4-2024",
        "2024-04-30",
        "2024-04-01 00:00:00",
        "2024-04-01T00:00:00",
        "Apr 2024",
        "April 2024",
        "apr-2024",
        "2024 Apr",
        "2024-Q2-01",
        "2024-Q201",
        "2024-Q2-M1",
        "2024 Q2 M1",
        "2024-Q2-04",
        " 2024-04 ",
    ],
)
def test_resolves_every_spelling_of_a_month_to_it(label: str) -> None:
    assert resolve_month(label) == APRIL_2024


@pytest.mark.parametrize(
    "label",
    [
        "YYYY-MM",
        "YYYY-MM-01",
        "2024-00",
        "2024-13",
        "2024-02-30",
        "2024-Q2",
        "2024-Q2-07",
        "2024-Q1-04",
        "01/02/2024",
        "Smarch 2024",
        "nan",
        "",
    ],
)
def test_leaves_a_label_naming_no_certain_month_unresolved(label: str) -> None:
    assert resolve_month(label) is None


def test_only_year_dash_two_digit_month_is_canonical() -> None:
    assert is_canonical("2024-04")
    assert not any(is_canonical(label) for label in ["2024-4", "04/2024", "2024-Q2-01"])
