from __future__ import annotations

import base64
import hashlib
import time
from collections.abc import Callable, Iterator
from pathlib import Path

import pytest

from core_python_sandbox_server import interpreter_session_proxy
from core_python_sandbox_server.models import ExecutionRequest, ExecutionResult
from core_python_sandbox_server.workspace_code_executor import WorkspaceCodeExecutor

ExecutorFactory = Callable[..., WorkspaceCodeExecutor]


@pytest.fixture
def make_executor(tmp_path: Path) -> Iterator[ExecutorFactory]:
    """Builds executors over ``tmp_path``, ending each one's interpreter session afterwards."""
    executors: list[WorkspaceCodeExecutor] = []

    def make(max_timeout_seconds: float = 5) -> WorkspaceCodeExecutor:
        executor = WorkspaceCodeExecutor(tmp_path, max_timeout_seconds=max_timeout_seconds)
        executors.append(executor)
        return executor

    yield make
    for executor in executors:
        executor.close()


def run(
    executor: WorkspaceCodeExecutor, code: str, timeout_seconds: float | None = None
) -> ExecutionResult:
    return executor.execute(ExecutionRequest(code=code, timeout_seconds=timeout_seconds))


def test_execute_returns_stdout(make_executor):
    executor = make_executor()

    result = executor.execute(ExecutionRequest(code="print('hello')"))

    assert result.stdout.strip() == "hello"
    assert result.exit_code == 0
    assert not result.timed_out


def test_execute_stages_files_before_running(make_executor):
    executor = make_executor()
    encoded = base64.b64encode(b"a,b\n1,2\n").decode()

    result = executor.execute(
        ExecutionRequest(
            code="print(open('data.csv').read())",
            files={"data.csv": encoded},
        )
    )

    assert "1,2" in result.stdout


def test_files_persist_across_calls(make_executor):
    executor = make_executor()
    encoded = base64.b64encode(b"42").decode()
    executor.execute(ExecutionRequest(code="pass", files={"answer.txt": encoded}))

    result = executor.execute(ExecutionRequest(code="print(open('answer.txt').read())"))

    assert result.stdout.strip() == "42"


def test_execute_captures_nonzero_exit(make_executor):
    executor = make_executor()

    result = executor.execute(ExecutionRequest(code="import sys; sys.exit(3)"))

    assert result.exit_code == 3
    assert not result.timed_out


def test_execute_captures_stderr(make_executor):
    executor = make_executor()

    result = executor.execute(ExecutionRequest(code="raise ValueError('boom')"))

    assert "boom" in result.stderr
    assert result.exit_code != 0


def test_execute_times_out(make_executor):
    executor = make_executor(10)

    result = executor.execute(
        ExecutionRequest(code="import time; time.sleep(5)", timeout_seconds=0.2)
    )

    assert result.timed_out
    assert result.exit_code == -1


def test_requested_timeout_cannot_exceed_the_server_maximum(make_executor):
    executor = make_executor(0.2)

    result = executor.execute(
        ExecutionRequest(code="import time; time.sleep(5)", timeout_seconds=60)
    )

    assert result.timed_out


def test_execute_rejects_path_traversal(make_executor):
    executor = make_executor()
    encoded = base64.b64encode(b"x").decode()

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.execute(ExecutionRequest(code="pass", files={"../evil.txt": encoded}))


def test_stage_file_writes_raw_bytes_and_returns_their_count(make_executor, tmp_path):
    executor = make_executor()

    size = executor.stage_file("report.xlsx", b"not really an xlsx")

    assert size == len(b"not really an xlsx")
    assert (tmp_path / "report.xlsx").read_bytes() == b"not really an xlsx"


def test_stage_file_result_is_visible_to_a_later_execute_call(make_executor):
    executor = make_executor()
    executor.stage_file("data.csv", b"a,b\n1,2\n")

    result = executor.execute(ExecutionRequest(code="print(open('data.csv').read())"))

    assert "1,2" in result.stdout


def test_stage_file_rejects_path_traversal(make_executor):
    executor = make_executor()

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.stage_file("../evil.bin", b"x")


def test_has_file_is_false_until_the_file_is_staged(make_executor):
    executor = make_executor()

    assert not executor.has_file("report.xlsx")
    executor.stage_file("report.xlsx", b"bytes")
    assert executor.has_file("report.xlsx")


def test_has_file_is_false_for_a_directory(make_executor, tmp_path):
    executor = make_executor()
    (tmp_path / "subdir").mkdir()

    assert not executor.has_file("subdir")


def test_has_file_rejects_path_traversal(make_executor):
    executor = make_executor()

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.has_file("../evil.bin")


def test_execute_echoes_a_bare_last_expression_like_a_notebook(make_executor):
    executor = make_executor()

    code = "sheet_names = ['Tactics', 'Notes']\nsheet_names"

    result = executor.execute(ExecutionRequest(code=code))

    assert result.stdout == "['Tactics', 'Notes']\n"
    assert result.exit_code == 0


def test_execute_does_not_echo_a_last_expression_that_evaluates_to_none(make_executor):
    executor = make_executor()

    result = executor.execute(ExecutionRequest(code="print('printed once')"))

    assert result.stdout == "printed once\n"


def test_execute_only_echoes_the_last_statement(make_executor):
    executor = make_executor()

    result = executor.execute(ExecutionRequest(code="'not echoed'\nvalue = 2"))

    assert result.stdout == ""


def test_execute_reports_errors_at_the_submitted_codes_own_line_numbers(make_executor):
    executor = make_executor()

    result = executor.execute(ExecutionRequest(code="x = 1\n\nraise ValueError('boom')"))

    assert 'File "<cell 1>", line 3' in result.stderr
    assert "raise ValueError('boom')" in result.stderr
    assert result.exit_code != 0


def test_execute_runs_code_as_main_with_workspace_modules_importable(make_executor):
    executor = make_executor()
    helper = base64.b64encode(b"VALUE = 42\n").decode()

    result = executor.execute(
        ExecutionRequest(
            code="import helper\nif __name__ == '__main__':\n    print(helper.VALUE)",
            files={"helper.py": helper},
        )
    )

    assert result.stdout == "42\n"


def test_workspace_file_is_none_until_the_file_exists(make_executor, tmp_path):
    executor = make_executor()

    assert executor.workspace_file("report.md") is None
    executor.stage_file("report.md", b"bytes")
    assert executor.workspace_file("report.md") == (tmp_path / "report.md").resolve()


def test_workspace_file_is_none_for_a_directory(make_executor, tmp_path):
    executor = make_executor()
    (tmp_path / "subdir").mkdir()

    assert executor.workspace_file("subdir") is None


def test_workspace_file_rejects_path_traversal(make_executor):
    executor = make_executor()

    with pytest.raises(ValueError, match="escapes the workspace"):
        executor.workspace_file("../evil.bin")


def test_list_files_describes_what_code_wrote(make_executor):
    executor = make_executor()
    executor.execute(ExecutionRequest(code="open('out.csv', 'w').write('a,b')"))

    listed = executor.list_files()

    assert [(file.filename, file.size) for file in listed] == [("out.csv", 3)]
    assert listed[0].sha256 == hashlib.sha256(b"a,b").hexdigest()


def test_list_files_leaves_out_hidden_files_and_directories(make_executor):
    executor = make_executor()
    executor.execute(
        ExecutionRequest(code="import os; os.mkdir('cache'); open('.secret', 'w').write('x')")
    )

    assert executor.list_files() == []


def test_list_files_includes_staged_files(make_executor):
    executor = make_executor()
    executor.stage_file("input.csv", b"q,r")

    assert [file.filename for file in executor.list_files()] == ["input.csv"]


def test_variables_imports_and_functions_persist_across_calls(make_executor):
    executor = make_executor()
    run(executor, "import json\nrows = [1, 2, 3]\ndef total(values):\n    return sum(values)")

    result = run(executor, "json.dumps(total(rows))")

    assert result.stdout == "'6'\n"
    assert not result.session_reset


def test_a_failing_call_keeps_what_earlier_calls_defined(make_executor):
    executor = make_executor()
    run(executor, "kept = 'still here'")
    run(executor, "raise RuntimeError('boom')")

    result = run(executor, "kept")

    assert result.stdout == "'still here'\n"


def test_sys_exit_sets_the_exit_code_without_ending_the_session(make_executor):
    executor = make_executor()
    run(executor, "kept = 1")

    exited = run(executor, "import sys; sys.exit(3)")
    result = run(executor, "kept")

    assert exited.exit_code == 3
    assert not exited.session_reset
    assert result.stdout == "1\n"


def test_sys_exit_with_a_message_prints_it_and_exits_one(make_executor):
    result = run(make_executor(), "import sys; sys.exit('giving up')")

    assert result.exit_code == 1
    assert "giving up" in result.stderr


def test_a_traceback_shows_the_cell_that_defined_the_failing_function(make_executor):
    executor = make_executor()
    run(executor, "def parse(text):\n\n    return int(text)")

    result = run(executor, "parse('x')")

    assert 'File "<cell 1>", line 3' in result.stderr
    assert "return int(text)" in result.stderr
    assert "interpreter_session_worker" not in result.stderr


def test_a_syntax_error_is_reported_without_ending_the_session(make_executor):
    executor = make_executor()
    run(executor, "kept = 1")

    failed = run(executor, "def broken(:")
    result = run(executor, "kept")

    assert failed.exit_code == 1
    assert "SyntaxError" in failed.stderr
    assert result.stdout == "1\n"


def test_each_call_returns_only_its_own_output(make_executor):
    executor = make_executor()
    run(executor, "print('first')")

    result = run(executor, "print('second')")

    assert result.stdout == "second\n"


def test_output_from_child_processes_is_captured(make_executor):
    result = run(make_executor(), "import subprocess\n_ = subprocess.run(['echo', 'from a child'])")

    assert result.stdout == "from a child\n"


def test_a_timed_out_call_is_interrupted_and_the_session_kept(make_executor):
    executor = make_executor()
    run(executor, "kept = 'survived'")

    timed_out = run(executor, "while True:\n    pass", timeout_seconds=0.2)
    result = run(executor, "kept")

    assert timed_out.timed_out
    assert "KeyboardInterrupt" in timed_out.stderr
    assert not timed_out.session_reset
    assert result.stdout == "'survived'\n"


def test_code_that_ignores_the_interrupt_is_killed_and_reports_the_session_lost(
    make_executor, monkeypatch
):
    monkeypatch.setattr(interpreter_session_proxy, "INTERRUPT_GRACE_SECONDS", 0.2)
    executor = make_executor()
    run(executor, "kept = 1")

    timed_out = run(
        executor,
        "import signal\nsignal.signal(signal.SIGINT, signal.SIG_IGN)\nwhile True:\n    pass",
        timeout_seconds=0.2,
    )
    result = run(executor, "kept")

    assert timed_out.timed_out
    assert timed_out.exit_code == -1
    assert timed_out.session_reset
    assert "NameError" in result.stderr
    assert not result.session_reset


def test_a_dying_session_reports_its_exit_code_and_the_next_call_starts_fresh(make_executor):
    executor = make_executor()
    run(executor, "kept = 1")

    died = run(executor, "print('last words', flush=True)\nimport os\nos._exit(7)")
    result = run(executor, "print('fresh')")

    assert died.exit_code == 7
    assert died.session_reset
    assert died.stdout == "last words\n"
    assert result.stdout == "fresh\n"
    assert not result.session_reset


def test_a_session_that_died_between_calls_is_reported_lost_on_the_next(make_executor):
    executor = make_executor()
    run(
        executor,
        "import os, signal, threading\n"
        "threading.Timer(0.1, os.kill, (os.getpid(), signal.SIGKILL)).start()",
    )
    time.sleep(0.5)

    result = run(executor, "print('fresh')")

    assert result.session_reset
    assert result.stdout == "fresh\n"


def test_the_first_call_does_not_report_a_lost_session(make_executor):
    assert not run(make_executor(), "pass").session_reset
