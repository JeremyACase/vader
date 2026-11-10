"""Stands in for the interpreter session worker process, so the executor can run code in it."""

from __future__ import annotations

import json
import logging
import os
import select
import signal
import subprocess
import sys
import tempfile
import threading
from pathlib import Path
from typing import IO

from core_python_sandbox_server.models import ExecutionResult

# The subprocess entry point that holds the session (see interpreter_session_worker.py).
WORKER = Path(__file__).with_name("interpreter_session_worker.py")

# How long an interrupted cell gets to unwind before the worker, and its session, are killed.
INTERRUPT_GRACE_SECONDS = 2.0

logger = logging.getLogger(__name__)


class InterpreterSessionProxy:
    """Runs code in one long-lived worker process, so state persists from one call to the next.

    The worker is a separate process -- not in-process ``exec()`` -- so a crash in submitted code
    can't take the server down, and a timeout can be enforced. An overrunning cell is first
    interrupted (SIGINT, which the worker turns into ``KeyboardInterrupt``), keeping the session;
    only a cell that won't stop within :data:`INTERRUPT_GRACE_SECONDS` gets the worker's whole
    process group killed, child processes included. A worker that is killed or dies loses the
    session, and the result of the call that finds this out says so (``session_reset``); the next
    call starts a fresh worker.

    Output is written by the worker straight into two capture files, outside the workspace so
    they never show up among the files an agent's code wrote. Calls are serialized: one session
    can only run one cell at a time.
    """

    def __init__(self, workspace: Path) -> None:
        self._workspace = workspace
        self._captures = Path(tempfile.mkdtemp(prefix="vader-session-"))
        self._lock = threading.Lock()
        self._process: subprocess.Popen[bytes] | None = None
        self._requests: IO[str] | None = None
        self._reply_fd = -1
        self._session_started = False

    def run(self, code: str, timeout: float) -> ExecutionResult:
        with self._lock:
            session_lost = self._ensure_worker()
            self._send(code)
            result = self._await_result(timeout)
        return result.model_copy(update={"session_reset": result.session_reset or session_lost})

    def close(self) -> None:
        with self._lock:
            self._discard_worker()

    def _ensure_worker(self) -> bool:
        """Starts a worker if none is alive, returning whether that loses an earlier session."""
        alive = self._process is not None and self._process.poll() is None
        session_lost = self._session_started and not alive
        if not alive:
            self._discard_worker()
            self._start_worker()
        return session_lost

    def _start_worker(self) -> None:
        request_read, request_write = os.pipe()
        reply_read, reply_write = os.pipe()
        # Semgrep's subprocess-injection rule flags this call, but running submitted code is
        # this server's purpose: the argv is fixed and no shell is involved.
        self._process = subprocess.Popen(  # nosemgrep
            [sys.executable, str(WORKER), str(request_read), str(reply_write)],
            cwd=self._workspace,
            stdin=subprocess.DEVNULL,
            pass_fds=(request_read, reply_write),
            start_new_session=True,
        )
        os.close(request_read)
        os.close(reply_write)
        self._requests = os.fdopen(request_write, "w")
        self._reply_fd = reply_read
        self._session_started = True
        logger.info("started interpreter session worker: pid=%d", self._process.pid)

    def _send(self, code: str) -> None:
        """Hands the worker a cell. The capture files are cleared first, so a worker that dies
        before writing any output can't pass off an earlier cell's as this one's."""
        self._stdout.unlink(missing_ok=True)
        self._stderr.unlink(missing_ok=True)
        request = {"code": code, "stdout": str(self._stdout), "stderr": str(self._stderr)}
        assert self._requests is not None  # _ensure_worker always leaves a worker running
        self._requests.write(json.dumps(request) + "\n")
        self._requests.flush()

    def _await_result(self, timeout: float) -> ExecutionResult:
        reply = self._read_reply(timeout)
        return self._interrupt() if reply is None else self._result(reply, timed_out=False)

    def _interrupt(self) -> ExecutionResult:
        """Interrupts an overrunning cell, killing the worker if it won't stop in time."""
        assert self._process is not None  # only reached while a cell is running
        logger.warning("submitted code timed out; interrupting it")
        self._process.send_signal(signal.SIGINT)
        reply = self._read_reply(INTERRUPT_GRACE_SECONDS)
        if reply is None:
            logger.warning("submitted code ignored the interrupt; killing its session")
            self._kill_worker()
            reply = b""
        return self._result(reply, timed_out=True)

    def _read_reply(self, timeout: float) -> bytes | None:
        """The worker's reply line: ``b""`` if the worker is gone, ``None`` if it hasn't answered
        in time. A reply is one short line written in one go, so it arrives in one read."""
        ready, _, _ = select.select([self._reply_fd], [], [], timeout)
        return os.read(self._reply_fd, 4096) if ready else None

    def _result(self, reply: bytes, timed_out: bool) -> ExecutionResult:
        """Builds the result from a reply line, or from an empty one when the worker is gone."""
        worker_gone = reply == b""
        exit_code = self._worker_exit_code() if worker_gone else json.loads(reply)["exitCode"]
        if worker_gone:
            logger.warning("interpreter session worker ended: exit_code=%s", exit_code)
            self._discard_worker()
        return ExecutionResult(
            stdout=_read_capture(self._stdout),
            stderr=_read_capture(self._stderr),
            exit_code=-1 if timed_out else exit_code,
            timed_out=timed_out,
            session_reset=worker_gone,
        )

    def _worker_exit_code(self) -> int:
        assert self._process is not None  # only asked about a worker this call was talking to
        return self._process.wait()

    def _kill_worker(self) -> None:
        if self._process is not None:
            try:
                os.killpg(self._process.pid, signal.SIGKILL)
            except ProcessLookupError:
                logger.debug("interpreter session worker already gone")
            self._process.wait()

    def _discard_worker(self) -> None:
        self._kill_worker()
        if self._requests is not None:
            self._requests.close()
        if self._reply_fd >= 0:
            os.close(self._reply_fd)
        self._process = None
        self._requests = None
        self._reply_fd = -1
        self._session_started = False

    @property
    def _stdout(self) -> Path:
        return self._captures / "stdout"

    @property
    def _stderr(self) -> Path:
        return self._captures / "stderr"


def _read_capture(path: Path) -> str:
    return path.read_bytes().decode(errors="replace") if path.is_file() else ""
