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


def test_stage_file_writes_raw_bytes_and_returns_their_count(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    size = executor.stage_file("report.xlsx", b"not really an xlsx")

    assert size == len(b"not really an xlsx")
    assert (tmp_path / "report.xlsx").read_bytes() == b"not really an xlsx"


def test_stage_file_result_is_visible_to_a_later_execute_call(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)
    executor.stage_file("data.csv", b"a,b\n1,2\n")

    result = executor.execute(ExecutionRequest(code="print(open('data.csv').read())"))

    assert "1,2" in result.stdout


def test_stage_file_rejects_path_traversal(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.stage_file("../evil.bin", b"x")


def test_has_file_is_false_until_the_file_is_staged(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    assert not executor.has_file("report.xlsx")
    executor.stage_file("report.xlsx", b"bytes")
    assert executor.has_file("report.xlsx")


def test_has_file_is_false_for_a_directory(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)
    (tmp_path / "subdir").mkdir()

    assert not executor.has_file("subdir")


def test_has_file_rejects_path_traversal(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.has_file("../evil.bin")


def test_execute_echoes_a_bare_last_expression_like_a_notebook(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    code = "sheet_names = ['Tactics', 'Notes']\nsheet_names"

    result = executor.execute(ExecutionRequest(code=code))

    assert result.stdout == "['Tactics', 'Notes']\n"
    assert result.exit_code == 0


def test_execute_does_not_echo_a_last_expression_that_evaluates_to_none(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    result = executor.execute(ExecutionRequest(code="print('printed once')"))

    assert result.stdout == "printed once\n"


def test_execute_only_echoes_the_last_statement(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    result = executor.execute(ExecutionRequest(code="'not echoed'\nvalue = 2"))

    assert result.stdout == ""


def test_execute_reports_errors_at_the_submitted_codes_own_line_numbers(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)

    result = executor.execute(ExecutionRequest(code="x = 1\n\nraise ValueError('boom')"))

    assert "line 3" in result.stderr
    assert "_submitted_code.py" in result.stderr
    assert result.exit_code != 0


def test_execute_runs_code_as_main_with_workspace_modules_importable(tmp_path):
    executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=5)
    helper = base64.b64encode(b"VALUE = 42\n").decode()

    result = executor.execute(
        ExecutionRequest(
            code="import helper\nif __name__ == '__main__':\n    print(helper.VALUE)",
            files={"helper.py": helper},
        )
    )

    assert result.stdout == "42\n"
