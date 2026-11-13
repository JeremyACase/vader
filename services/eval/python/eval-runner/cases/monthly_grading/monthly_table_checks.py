"""Checks every quarterly-to-monthly case applies the same way.

Coverage is substance: a missing, extra or repeated month misstates the year. Everything else
here is presentation the request asks for but no reader needs, since a table can be re-sorted,
relabelled and its columns renamed without changing a single figure, so it only costs a run its
strict pass.
"""

from __future__ import annotations

import pandas as pd

from monthly_grading.month_label_parser_chain import is_canonical
from monthly_grading.monthly_table import MonthlyTable


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


def presentation_problems(table: MonthlyTable, expected_columns: list[str]) -> list[str]:
    return (
        column_problems(table.columns, expected_columns)
        + label_spelling_problems(table.labels)
        + order_problems(table.months)
    )


def column_problems(columns: list[str], expected_columns: list[str]) -> list[str]:
    return (
        []
        if columns == expected_columns
        else [f"columns are {columns}, requested exactly {expected_columns}"]
    )


def label_spelling_problems(labels: list[str]) -> list[str]:
    misspelled = [label for label in labels if not is_canonical(label)]
    return (
        [f"{len(misspelled)} months not formatted YYYY-MM, such as {misspelled[:3]}"]
        if misspelled
        else []
    )


def order_problems(months: list[pd.Period]) -> list[str]:
    in_order = all(earlier < later for earlier, later in zip(months, months[1:], strict=False))
    return [] if in_order else ["months are not in chronological order"]
