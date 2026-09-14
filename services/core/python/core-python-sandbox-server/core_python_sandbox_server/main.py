"""FastAPI app exposing the sandbox's code-execution endpoint.

Deployed as the sandbox pod's own container command (see the Dockerfile); core-server proxies
to it over the Service already provisioned for it, at ``http://<sandbox-name>.<namespace>.svc
.cluster.local:8888``.
"""

from __future__ import annotations

import os
import tempfile
from pathlib import Path

from fastapi import FastAPI

from core_python_sandbox_server.models import ExecutionRequest, ExecutionResult
from core_python_sandbox_server.workspace_code_executor import WorkspaceCodeExecutor

# The container sets SANDBOX_WORKSPACE_DIR=/workspace (see Dockerfile). Falling back to a temp
# directory keeps local test/dev runs, which have no such directory, working without root.
WORKSPACE_DIR = Path(
    os.environ.get("SANDBOX_WORKSPACE_DIR", str(Path(tempfile.gettempdir()) / "vader-sandbox"))
)
MAX_TIMEOUT_SECONDS = float(os.environ.get("SANDBOX_EXEC_MAX_TIMEOUT_SECONDS", "30"))

app = FastAPI(title="Vader Sandbox Exec Server")
executor = WorkspaceCodeExecutor(WORKSPACE_DIR, MAX_TIMEOUT_SECONDS)


@app.get("/health")
def health() -> dict[str, str]:
    """Liveness/readiness probe target."""
    return {"status": "ok"}


@app.post("/execute", response_model=ExecutionResult)
def execute(request: ExecutionRequest) -> ExecutionResult:
    """Runs the submitted code in the persistent workspace and returns its output."""
    return executor.execute(request)
