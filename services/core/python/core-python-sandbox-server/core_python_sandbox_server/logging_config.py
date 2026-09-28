"""Configures process-wide logging for the sandbox exec server.

Mirrors how the other two runtime pieces of this call path handle logging: core-server reads
``logging.level.org.vader`` and core-agent-harness reads ``RUST_LOG``, both writing to
stderr/stdout so a deployment's log level is one knob (``logging.vader`` in the Helm values)
rather than three kept in sync by hand. This server reads ``SANDBOX_LOG_LEVEL``, set by
``PythonSandboxManifestBuilder`` from that same value, and also writes to stderr so it shows up
in ``kubectl logs`` with no extra plumbing.
"""

from __future__ import annotations

import logging
import os
import sys

DEFAULT_LOG_LEVEL = "INFO"


def configure_logging() -> None:
    """Installs a stderr handler on the root logger at the level ``SANDBOX_LOG_LEVEL`` names.

    Falls back to :data:`DEFAULT_LOG_LEVEL` when the env var is unset or names an unknown level,
    since a typo here should degrade to a sane default rather than crash the server at startup.
    """
    level_name = os.environ.get("SANDBOX_LOG_LEVEL", DEFAULT_LOG_LEVEL).upper()
    level = logging.getLevelName(level_name)
    if not isinstance(level, int):
        level = logging.getLevelName(DEFAULT_LOG_LEVEL)

    logging.basicConfig(
        level=level,
        stream=sys.stderr,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        force=True,
    )
