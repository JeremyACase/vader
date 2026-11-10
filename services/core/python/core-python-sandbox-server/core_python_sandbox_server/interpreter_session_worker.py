"""The long-lived interpreter that runs every piece of submitted code in one persistent session.

Executed as a subprocess entry point (``python interpreter_session_worker.py <request-fd>
<reply-fd>``), not imported by the server: it deliberately imports nothing from this package, so
the session's import path holds only the workspace and the standard locations, exactly as
``python`` started in the workspace would have it.

Protocol: the server writes one JSON request per line to the request pipe -- ``{"code": ...,
"stdout": <path>, "stderr": <path>}`` -- and the worker answers each with one JSON line,
``{"exitCode": <int>}``, on the reply pipe. The cell's output goes to the two files the request
names, at the file-descriptor level, so output from C extensions and child processes is captured
too. The worker exits when the request pipe closes.
"""

from __future__ import annotations

import ast
import json
import linecache
import os
import signal
import sys
import traceback
from types import CodeType, FrameType, TracebackType
from typing import IO


class InterpreterSessionWorker:
    """Runs each request as a notebook cell in a namespace that persists for the worker's life.

    Variables, imports and functions a cell defines are there for every later cell, so an agent
    can load a file once and then work on it in small steps instead of resubmitting a whole
    program each time. A bare expression on a cell's last line is echoed through
    ``sys.displayhook``, matching the interactive interpreter: its ``repr`` is printed, and
    ``None`` is not. Each cell gets its own filename (``<cell 3>``) registered with ``linecache``,
    so a traceback shows the real source line even inside a function an earlier cell defined.

    SIGINT interrupts a running cell with ``KeyboardInterrupt`` and leaves the session intact --
    the server sends it when a cell overruns its timeout. Between cells it is ignored, so an
    interrupt that arrives just after a cell finished cannot kill the session.
    """

    def __init__(self, requests: IO[str], replies: IO[str]) -> None:
        self._requests = requests
        self._replies = replies
        self._namespace: dict[str, object] = {"__name__": "__main__", "__builtins__": __builtins__}
        self._cell_count = 0
        self._cell_running = False

    def serve(self) -> None:
        signal.signal(signal.SIGINT, self._interrupt)
        sys.argv = [""]
        sys.path[0] = os.getcwd()
        for line in self._requests:
            request = json.loads(line)
            exit_code = self._run_captured(request["code"], request["stdout"], request["stderr"])
            self._replies.write(json.dumps({"exitCode": exit_code}) + "\n")
            self._replies.flush()

    def _interrupt(self, signum: int, frame: FrameType | None) -> None:
        if self._cell_running:
            raise KeyboardInterrupt

    def _run_captured(self, code: str, stdout_path: str, stderr_path: str) -> int:
        _redirect(1, stdout_path)
        _redirect(2, stderr_path)
        try:
            exit_code = self._run_cell(code)
        finally:
            sys.stdout.flush()
            sys.stderr.flush()
        return exit_code

    def _run_cell(self, code: str) -> int:
        self._cell_count += 1
        filename = f"<cell {self._cell_count}>"
        linecache.cache[filename] = (len(code), None, code.splitlines(keepends=True), filename)
        try:
            self._cell_running = True
            exit_code = self._execute(code, filename)
        except SystemExit as error:
            exit_code = _exit_code_of(error)
        except BaseException as error:
            _print_user_traceback(error)
            exit_code = 1
        finally:
            self._cell_running = False
        return exit_code

    def _execute(self, code: str, filename: str) -> int:
        tree = ast.parse(code, filename=filename)
        last_expression = _pop_last_expression(tree)
        exec(_compile(tree, filename, "exec"), self._namespace)
        if last_expression is not None:
            expression = ast.Expression(last_expression.value)
            sys.displayhook(eval(_compile(expression, filename, "eval"), self._namespace))
        return 0


def _redirect(fd: int, path: str) -> None:
    target = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.dup2(target, fd)
    os.close(target)


def _pop_last_expression(tree: ast.Module) -> ast.Expr | None:
    has_trailing_expression = bool(tree.body) and isinstance(tree.body[-1], ast.Expr)
    last = tree.body.pop() if has_trailing_expression else None
    return last if isinstance(last, ast.Expr) else None


def _compile(node: ast.Module | ast.Expression, filename: str, mode: str) -> CodeType:
    return compile(node, filename=filename, mode=mode)


def _exit_code_of(error: SystemExit) -> int:
    """Maps ``sys.exit(...)`` to an exit code the way the interpreter does, without ending the
    session: no argument is 0, an int is itself, a message is printed and is 1."""
    code = error.code
    if isinstance(code, str):
        print(code, file=sys.stderr)
    return code if isinstance(code, int) else int(code is not None)


def _print_user_traceback(error: BaseException) -> None:
    """Prints the traceback the way the interpreter would: from the cell's own frames, leaving
    out this worker's, and with none at all for a syntax error, which shows its line itself."""
    frames = None if isinstance(error, SyntaxError) else _user_frames(error.__traceback__)
    traceback.print_exception(type(error), error, frames)


def _user_frames(frames: TracebackType | None) -> TracebackType | None:
    while frames is not None and frames.tb_frame.f_code.co_filename == __file__:
        frames = frames.tb_next
    return frames


if __name__ == "__main__":
    with (
        os.fdopen(int(sys.argv[1]), "r") as request_pipe,
        os.fdopen(int(sys.argv[2]), "w") as reply_pipe,
    ):
        InterpreterSessionWorker(request_pipe, reply_pipe).serve()
