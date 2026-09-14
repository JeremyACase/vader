from __future__ import annotations

from fastapi.testclient import TestClient

from core_python_sandbox_server.main import app

client = TestClient(app)


def test_health_returns_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_execute_runs_code_and_returns_output():
    response = client.post("/execute", json={"code": "print(1 + 1)"})

    assert response.status_code == 200
    body = response.json()
    assert body["stdout"].strip() == "2"
    assert body["exitCode"] == 0
    assert body["timedOut"] is False


def test_execute_accepts_camel_case_timeout_field():
    response = client.post(
        "/execute", json={"code": "import time; time.sleep(5)", "timeoutSeconds": 0.2}
    )

    assert response.status_code == 200
    assert response.json()["timedOut"] is True
