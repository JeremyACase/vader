"""Builds multipart/form-data request bodies, which the standard library has no encoder for."""

from __future__ import annotations

import mimetypes
import uuid
from pathlib import Path

FALLBACK_CONTENT_TYPE = "application/octet-stream"


class MultipartFormBuilder:
    """Accumulates text fields and files, then renders them as one multipart body."""

    def __init__(self) -> None:
        self._boundary = f"vader-eval-{uuid.uuid4().hex}"
        self._parts: list[bytes] = []

    def add_field(self, name: str, value: str) -> MultipartFormBuilder:
        self._parts.append(self._part_header(f'name="{name}"', None) + value.encode("utf-8"))
        return self

    def add_file(self, name: str, path: Path) -> MultipartFormBuilder:
        content_type = mimetypes.guess_type(path.name)[0] or FALLBACK_CONTENT_TYPE
        disposition = f'name="{name}"; filename="{path.name}"'
        self._parts.append(self._part_header(disposition, content_type) + path.read_bytes())
        return self

    def content_type(self) -> str:
        return f"multipart/form-data; boundary={self._boundary}"

    def body(self) -> bytes:
        delimiter = f"--{self._boundary}\r\n".encode()
        closing = f"--{self._boundary}--\r\n".encode()
        return b"".join(delimiter + part + b"\r\n" for part in self._parts) + closing

    @staticmethod
    def _part_header(disposition: str, content_type: str | None) -> bytes:
        lines = [f"Content-Disposition: form-data; {disposition}"] + (
            [f"Content-Type: {content_type}"] if content_type else []
        )
        return ("\r\n".join(lines) + "\r\n\r\n").encode("utf-8")
