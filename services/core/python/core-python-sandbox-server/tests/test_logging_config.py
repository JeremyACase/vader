from __future__ import annotations

import logging

import pytest

from core_python_sandbox_server.logging_config import DEFAULT_LOG_LEVEL, configure_logging


@pytest.fixture(autouse=True)
def _reset_root_logger():
    yield
    logging.root.handlers.clear()
    logging.root.setLevel(logging.WARNING)


def test_configure_logging_reads_the_level_from_the_env_var(monkeypatch):
    monkeypatch.setenv("SANDBOX_LOG_LEVEL", "DEBUG")

    configure_logging()

    assert logging.root.level == logging.DEBUG


def test_configure_logging_falls_back_to_default_when_env_var_is_unset(monkeypatch):
    monkeypatch.delenv("SANDBOX_LOG_LEVEL", raising=False)

    configure_logging()

    assert logging.root.level == logging.getLevelName(DEFAULT_LOG_LEVEL)


def test_configure_logging_falls_back_to_default_when_env_var_is_invalid(monkeypatch):
    monkeypatch.setenv("SANDBOX_LOG_LEVEL", "not_a_real_level")

    configure_logging()

    assert logging.root.level == logging.getLevelName(DEFAULT_LOG_LEVEL)
