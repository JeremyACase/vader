"""Runs a submitted script, echoing a bare last expression's value the way a notebook does.

Executed as the subprocess entry point (``python last_expression_echo_runner.py <script>``), not
imported by the server: it deliberately imports nothing from this package, so it runs the same
regardless of what is on the subprocess's import path.
"""

from __future__ import annotations

import ast
import sys
from pathlib import Path
from types import CodeType


class LastExpressionEchoRunner:
    """Executes a script and, if its last statement is a bare expression, echoes that value.

    Models write notebook-style code -- ``df.head()`` or ``sheet_names`` as the last line -- far
    more often than they remember to ``print`` it. Run as a plain script, that value is silently
    discarded and the model gets back empty stdout, which it tends to answer by resubmitting the
    same code until the harness's stall detector ends the run. Echoing it through
    ``sys.displayhook`` matches the interactive interpreter exactly: the value's ``repr`` is
    printed, and ``None`` (e.g. a trailing ``df.info()`` call, which prints for itself) is not.

    The script is compiled from its own path with its own line numbers, so a traceback points at
    the submitted code's real lines, and it runs as ``__main__`` with the script's directory first
    on ``sys.path`` -- exactly as ``python <script>`` would have run it.
    """

    def __init__(self, script: Path) -> None:
        self._script = script

    def run(self) -> None:
        tree = ast.parse(self._script.read_text(), filename=str(self._script))
        last_expression = self._pop_last_expression(tree)
        namespace = self._main_namespace()
        exec(self._compile(tree, "exec"), namespace)
        if last_expression is not None:
            value = eval(self._compile(ast.Expression(last_expression.value), "eval"), namespace)
            sys.displayhook(value)

    @staticmethod
    def _pop_last_expression(tree: ast.Module) -> ast.Expr | None:
        has_trailing_expression = bool(tree.body) and isinstance(tree.body[-1], ast.Expr)
        last = tree.body.pop() if has_trailing_expression else None
        return last if isinstance(last, ast.Expr) else None

    def _compile(self, node: ast.Module | ast.Expression, mode: str) -> CodeType:
        return compile(node, filename=str(self._script), mode=mode)

    def _main_namespace(self) -> dict[str, object]:
        sys.argv = [str(self._script)]
        sys.path[0] = str(self._script.parent)
        return {"__name__": "__main__", "__file__": str(self._script), "__builtins__": __builtins__}


if __name__ == "__main__":
    LastExpressionEchoRunner(Path(sys.argv[1])).run()
