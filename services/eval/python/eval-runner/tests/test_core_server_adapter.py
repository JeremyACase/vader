"""The core-server adapter against a real local HTTP server standing in for core-server."""

from __future__ import annotations

import json
import threading
from collections.abc import Iterator
from email import message_from_bytes, policy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import pytest

from eval_runner.core_server_adapter import CoreServerAdapter
from eval_runner.interface_core_server import CoreServerError


class CoreServerHandler(BaseHTTPRequestHandler):
    """Serves three pages of object metadata, accepts a prompt, and records what it received."""

    received: list[tuple[str, bytes, str]] = []

    def do_GET(self) -> None:
        url = urlparse(self.path)
        query = {key: values[0] for key, values in parse_qs(url.query).items()}
        page = int(query.get("page", "0"))
        body = {
            "content": [{"id": f"object-{page}", "filters": query}],
            "last": page == 2,
        }
        self._reply(json.dumps(body).encode())

    def do_POST(self) -> None:
        length = int(self.headers["Content-Length"])
        CoreServerHandler.received.append(
            (self.path, self.rfile.read(length), self.headers["Content-Type"])
        )
        self._reply(json.dumps({"id": "prompt-1"}).encode())

    def _reply(self, body: bytes) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:
        pass


@pytest.fixture
def server_url() -> Iterator[str]:
    server = ThreadingHTTPServer(("127.0.0.1", 0), CoreServerHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()


def test_query_all_follows_every_page_and_passes_the_filters(server_url: str) -> None:
    rows = CoreServerAdapter(server_url).query_all(
        "object-metadata", {"originalFilename": "monthly_revenue.csv"}
    )

    assert [row["id"] for row in rows] == ["object-0", "object-1", "object-2"]
    assert rows[0]["filters"] == {
        "page": "0",
        "size": "100",
        "originalFilename": "monthly_revenue.csv",
    }


def test_a_prompt_is_submitted_as_multipart_with_its_files(server_url: str, tmp_path: Path) -> None:
    attachment = tmp_path / "quarterly_revenue.csv"
    attachment.write_text("quarter,revenue\n2024-Q1,120000\n", encoding="utf-8")
    CoreServerHandler.received.clear()

    prompt_id = CoreServerAdapter(server_url).submit_prompt("Convert it.", [attachment])

    path, body, content_type = CoreServerHandler.received[0]
    message = message_from_bytes(
        f"Content-Type: {content_type}\r\n\r\n".encode() + body, policy=policy.HTTP
    )
    parts = {
        part.get_param("name", header="content-disposition"): part for part in message.iter_parts()
    }
    assert prompt_id == "prompt-1"
    assert path == "/vader/core-server/client-prompt"
    assert parts["text"].get_content() == "Convert it."
    assert parts["files"].get_filename() == "quarterly_revenue.csv"
    assert parts["files"].get_content_type() == "text/csv"
    assert parts["files"].get_payload(decode=True) == attachment.read_bytes()


def test_reads_are_retried_before_core_server_is_declared_unreachable() -> None:
    delays: list[float] = []
    adapter = CoreServerAdapter(
        "http://127.0.0.1:9",
        read_attempts=3,
        retry_delay_seconds=5.0,
        timeout_seconds=2.0,
        sleep=delays.append,
    )

    with pytest.raises(CoreServerError):
        adapter.configuration()

    assert delays == [5.0, 5.0]
