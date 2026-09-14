from __future__ import annotations

import base64

import pytest

from core_python_sandbox_server.models import ExecutionRequest
from core_python_sandbox_server.workspace_code_executor import WorkspaceCodeExecutor


def test_execute_returns_stdout(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    result = executor.execute(ExecutionRequest(code="print('hello')"))

    assert result.stdout.strip() == "hello"
    assert result.exit_code == 0
    assert not result.timed_out


def test_execute_stages_files_before_running(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)
    encoded = base64.b64encode(b"a,b\n1,2\n").decode()

    result = executor.execute(
        ExecutionRequest(
            code="print(open('data.csv').read())",
            files={"data.csv": encoded},
        )
    )

    assert "1,2" in result.stdout


def test_files_persist_across_calls(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)
    encoded = base64.b64encode(b"42").decode()
    executor.execute(ExecutionRequest(code="pass", files={"answer.txt": encoded}))

    result = executor.execute(ExecutionRequest(code="print(open('answer.txt').read())"))

    assert result.stdout.strip() == "42"


def test_execute_captures_nonzero_exit(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    result = executor.execute(ExecutionRequest(code="import sys; sys.exit(3)"))

    assert result.exit_code == 3
    assert not result.timed_out


def test_execute_captures_stderr(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    result = executor.execute(ExecutionRequest(code="raise ValueError('boom')"))

    assert "boom" in result.stderr
    assert result.exit_code != 0


def test_execute_times_out(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=10)

    result = executor.execute(
        ExecutionRequest(code="import time; time.sleep(5)", timeout_seconds=0.2)
    )

    assert result.timed_out
    assert result.exit_code == -1


def test_requested_timeout_cannot_exceed_the_server_maximum(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=0.2)

    result = executor.execute(
        ExecutionRequest(code="import time; time.sleep(5)", timeout_seconds=60)
    )

    assert result.timed_out


def test_execute_rejects_path_traversal(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)
    encoded = base64.b64encode(b"x").decode()

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.execute(ExecutionRequest(code="pass", files={"../evil.txt": encoded}))
