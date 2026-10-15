"""What the runner needs from core-server, so its logic can be tested against a stub."""

from __future__ import annotations

from pathlib import Path
from typing import Protocol

JsonObject = dict[str, object]


class CoreServerError(Exception):
    """core-server was unreachable or answered with an error. Says nothing about the prompts
    being measured, so a run that hits it is retried rather than scored."""


class InterfaceCoreServer(Protocol):
    def configuration(self) -> JsonObject:
        """The mode, model, sampling and prompt fingerprints currently in effect."""
        ...

    def prompts(self) -> JsonObject:
        """Every call kind's prompt fingerprint and the template texts behind it."""
        ...

    def submit_prompt(self, text: str, files: list[Path]) -> str:
        """Submits a client prompt with its attachments; returns the prompt's id."""
        ...

    def latest_workflow(self, client_prompt_id: str) -> JsonObject | None:
        """The newest workflow for a prompt, or None until decomposition has created one."""
        ...

    def query_all(self, entity: str, filters: dict[str, str]) -> list[JsonObject]:
        """Every row of an entity's query endpoint matching the filters, across all pages."""
        ...

    def download(self, object_id: str) -> bytes:
        """A stored object's content."""
        ...
