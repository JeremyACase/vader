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


def test_stage_file_writes_the_raw_request_body_and_acks_with_no_content_echoed():
    response = client.put("/workspace/files/report.xlsx", content=b"raw spreadsheet bytes")

    assert response.status_code == 200
    body = response.json()
    assert body == {"filename": "report.xlsx", "size": len(b"raw spreadsheet bytes")}


def test_staged_file_is_then_visible_to_execute():
    client.put("/workspace/files/staged.csv", content=b"a,b\n1,2\n")

    response = client.post("/execute", json={"code": "print(open('staged.csv').read())"})

    assert "1,2" in response.json()["stdout"]
