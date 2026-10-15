"""core-server's REST API, adapted to what the runner needs of it."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable
from pathlib import Path

from eval_runner.interface_core_server import CoreServerError, JsonObject
from eval_runner.multipart_form_builder import MultipartFormBuilder

# The DAO's own cap on page size (vader.dao.max-page-size).
PAGE_SIZE = 100


class CoreServerAdapter:
    """Talks to core-server over HTTP with only the standard library.

    Reads are retried a few times, because a long benchmark reaches core-server through a
    port-forward that can drop and be restarted under it. The prompt submission is never retried:
    a retry after a lost response would submit the same prompt twice.
    """

    def __init__(
        self,
        base_url: str,
        read_attempts: int = 6,
        retry_delay_seconds: float = 10.0,
        timeout_seconds: float = 30.0,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._read_attempts = read_attempts
        self._retry_delay_seconds = retry_delay_seconds
        self._timeout_seconds = timeout_seconds
        self._sleep = sleep

    def configuration(self) -> JsonObject:
        return as_object(json.loads(self._read("/vader/core-server/llm/configuration")))

    def prompts(self) -> JsonObject:
        return as_object(json.loads(self._read("/vader/core-server/llm/prompts")))

    def submit_prompt(self, text: str, files: list[Path]) -> str:
        form = MultipartFormBuilder().add_field("text", text)
        for path in files:
            form.add_file("files", path)
        request = urllib.request.Request(
            self._base_url + "/vader/core-server/client-prompt",
            data=form.body(),
            headers={"Content-Type": form.content_type()},
            method="POST",
        )
        receipt = as_object(json.loads(self._send(request)))
        return str(receipt["id"])

    def latest_workflow(self, client_prompt_id: str) -> JsonObject | None:
        filters = {"clientPrompt.id": client_prompt_id, "sort-by-fields": "createdAt"}
        content = page_content(self._query_page("workflow", filters, 0, 1))
        return content[0] if content else None

    def query_all(self, entity: str, filters: dict[str, str]) -> list[JsonObject]:
        rows: list[JsonObject] = []
        page_number = 0
        last = False
        while not last:
            page = self._query_page(entity, filters, page_number, PAGE_SIZE)
            rows.extend(page_content(page))
            last = bool(page.get("last", True))
            page_number += 1
        return rows

    def download(self, object_id: str) -> bytes:
        return self._read(f"/vader/core-server/object-storage/{object_id}/content")

    def _query_page(
        self, entity: str, filters: dict[str, str], page_number: int, size: int
    ) -> JsonObject:
        query = urllib.parse.urlencode({"page": page_number, "size": size, **filters})
        return as_object(
            json.loads(self._read(f"/vader/core-server/data/{entity}/query/params?{query}"))
        )

    def _read(self, path: str) -> bytes:
        request = urllib.request.Request(self._base_url + path, method="GET")
        return self._read_with_retries(request, self._read_attempts)

    def _read_with_retries(self, request: urllib.request.Request, attempts_left: int) -> bytes:
        body = self._try_send(request, last_attempt=attempts_left <= 1)
        if body is None:
            self._sleep(self._retry_delay_seconds)
            body = self._read_with_retries(request, attempts_left - 1)
        return body

    def _try_send(self, request: urllib.request.Request, last_attempt: bool) -> bytes | None:
        body: bytes | None = None
        try:
            body = self._send(request)
        except CoreServerError:
            if last_attempt:
                raise
        return body

    def _send(self, request: urllib.request.Request) -> bytes:
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                body: bytes = response.read()
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            raise CoreServerError(f"{request.get_method()} {request.full_url}: {error}") from error
        return body


def as_object(value: object) -> JsonObject:
    if not isinstance(value, dict):
        raise CoreServerError(f"expected a JSON object, got {type(value).__name__}")
    return {str(key): item for key, item in value.items()}


def page_content(page: JsonObject) -> list[JsonObject]:
    content = page.get("content")
    return [as_object(row) for row in content] if isinstance(content, list) else []
