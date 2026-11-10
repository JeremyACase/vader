"""Runs submitted code in the sandbox's persistent workspace as an isolated subprocess."""

from __future__ import annotations

import base64
import hashlib
import logging
from pathlib import Path

from core_python_sandbox_server.interpreter_session_proxy import InterpreterSessionProxy
from core_python_sandbox_server.models import (
    ExecutionRequest,
    ExecutionResult,
    WorkspaceFileInfo,
)

logger = logging.getLogger(__name__)


class WorkspaceCodeExecutor:
    """Stages files into a persistent workspace directory, then runs code against it.

    The workspace is deliberately persistent across calls, not a fresh directory per request: an
    agent typically stages a file once (e.g. an uploaded spreadsheet) and then runs several
    analysis snippets against it without re-uploading each time. The code itself runs in one
    persistent Python session (``InterpreterSessionProxy``), notebook-style: what one call defines
    is there for the next, and a bare expression on a call's last line is echoed to stdout.
    """

    def __init__(self, workspace: Path, max_timeout_seconds: float) -> None:
        self._workspace = workspace
        self._max_timeout_seconds = max_timeout_seconds
        self._workspace.mkdir(parents=True, exist_ok=True)
        self._session = InterpreterSessionProxy(workspace)

    def execute(self, request: ExecutionRequest) -> ExecutionResult:
        """Stages any supplied files, then runs the submitted code in the session.

        Returns its stdout/stderr/exit code, whether it timed out, and whether the session was
        lost.
        """
        self._stage_files(request.files)
        return self._session.run(request.code, self._resolve_timeout(request.timeout_seconds))

    def close(self) -> None:
        """Ends the interpreter session, and any processes its code started."""
        self._session.close()

    def _resolve_timeout(self, requested_seconds: float | None) -> float:
        if requested_seconds is None:
            return self._max_timeout_seconds
        return min(requested_seconds, self._max_timeout_seconds)

    def stage_file(self, filename: str, content: bytes) -> int:
        """Writes raw bytes into the workspace, returning the byte count written.

        Shared by the base64-staged path (``ExecutionRequest.files``) and the raw-body upload
        endpoint, so both end up going through the same path-safety check.
        """
        path = self._safe_path(filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return len(content)

    def has_file(self, filename: str) -> bool:
        """Whether ``filename`` is already staged in the workspace.

        Lets core-server stage an object only when it's actually missing -- e.g. after a container
        restart -- instead of re-sending the same bytes on every call.
        """
        return self._safe_path(filename).is_file()

    def workspace_file(self, filename: str) -> Path | None:
        """The path of ``filename`` in the workspace, or ``None`` if no such file exists.

        Lets core-server read back a file submitted code wrote -- e.g. a report or an edited
        spreadsheet a task agent is saving to object storage -- through the same path-safety check
        as staging.
        """
        path = self._safe_path(filename)
        return path if path.is_file() else None

    def list_files(self) -> list[WorkspaceFileInfo]:
        """Describes every file at the top of the workspace, by name, size and SHA-256.

        Lets core-server save the files an agent's code wrote without the agent having to name
        them, and tell them apart from files staged in unchanged. Hidden files are left out.
        """
        return [
            self._describe(path) for path in sorted(self._workspace.iterdir()) if _is_listed(path)
        ]

    @staticmethod
    def _describe(path: Path) -> WorkspaceFileInfo:
        with path.open("rb") as content:
            digest = hashlib.file_digest(content, "sha256").hexdigest()
        return WorkspaceFileInfo(filename=path.name, size=path.stat().st_size, sha256=digest)

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


def _is_listed(path: Path) -> bool:
    return path.is_file() and not path.name.startswith(".")
