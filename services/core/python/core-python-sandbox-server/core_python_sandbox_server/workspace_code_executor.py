"""Runs submitted code in the sandbox's persistent workspace as an isolated subprocess."""

from __future__ import annotations

import base64
import logging
import subprocess
import sys
from pathlib import Path

from core_python_sandbox_server.models import ExecutionRequest, ExecutionResult

SUBMITTED_CODE_FILENAME = "_submitted_code.py"

logger = logging.getLogger(__name__)


class WorkspaceCodeExecutor:
    """Stages files into a persistent workspace directory, then runs code against it.

    The workspace is deliberately persistent across calls, not a fresh directory per request: an
    agent typically stages a file once (e.g. an uploaded spreadsheet) and then runs several
    analysis snippets against it without re-uploading each time. Code runs as a separate
    subprocess -- not via in-process exec()/eval() -- so a crash or infinite loop in submitted
    code can't take the server down with it, and a timeout can actually be enforced.
    """

    def __init__(self, workspace: Path, max_timeout_seconds: float) -> None:
        self._workspace = workspace
        self._max_timeout_seconds = max_timeout_seconds
        self._workspace.mkdir(parents=True, exist_ok=True)

    def execute(self, request: ExecutionRequest) -> ExecutionResult:
        """Stages any supplied files, then runs the submitted code against the workspace.

        Returns the process's stdout/stderr/exit code, or timed_out=True if it ran too long.
        """
        self._stage_files(request.files)
        timeout = self._resolve_timeout(request.timeout_seconds)
        script = self._workspace / SUBMITTED_CODE_FILENAME
        script.write_text(request.code)

        try:
            completed = subprocess.run(
                [sys.executable, str(script)],
                cwd=self._workspace,
                capture_output=True,
                text=True,
                timeout=timeout,
            )
            result = ExecutionResult(
                stdout=completed.stdout,
                stderr=completed.stderr,
                exit_code=completed.returncode,
                timed_out=False,
            )
        except subprocess.TimeoutExpired as error:
            logger.warning("submitted code timed out after %s seconds", timeout)
            result = ExecutionResult(
                stdout=self._decode(error.stdout),
                stderr=self._decode(error.stderr),
                exit_code=-1,
                timed_out=True,
            )
        return result

    def _resolve_timeout(self, requested_seconds: float | None) -> float:
        if requested_seconds is None:
            return self._max_timeout_seconds
        return min(requested_seconds, self._max_timeout_seconds)

    @staticmethod
    def _decode(output: str | bytes | None) -> str:
        if output is None:
            return ""
        return output if isinstance(output, str) else output.decode(errors="replace")

    def stage_file(self, filename: str, content: bytes) -> int:
        """Writes raw bytes into the workspace, returning the byte count written.

        Shared by the base64-staged path (``ExecutionRequest.files``) and the raw-body upload
        endpoint, so both end up going through the same path-safety check.
        """
        path = self._safe_path(filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return len(content)

    def _stage_files(self, files: dict[str, str]) -> None:
        for filename, encoded_content in files.items():
            self.stage_file(filename, base64.b64decode(encoded_content))

    def _safe_path(self, filename: str) -> Path:
        workspace = self._workspace.resolve()
        candidate = (workspace / filename).resolve()
        if not candidate.is_relative_to(workspace):
            logger.warning("rejected file path escaping the workspace: %s", filename)
            raise ValueError(f"'{filename}' escapes the workspace directory")
        return candidate
