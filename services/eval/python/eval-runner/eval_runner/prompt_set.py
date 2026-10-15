"""One call kind's prompts as core-server reports them: the fingerprint and the templates behind
it."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath

from eval_runner.interface_core_server import CoreServerError, JsonObject


@dataclass(frozen=True)
class PromptSet:
    kind: str
    fingerprint: str
    templates: dict[str, str]

    def directory_name(self) -> str:
        """Names the wording, not just the kind, so every version of a kind's prompts that a
        results directory ever saw can sit side by side."""
        return f"{self.kind}-{self.fingerprint}"


def prompt_sets(response: JsonObject) -> list[PromptSet]:
    """Parses core-server's /llm/prompts response, refusing a template name that would escape the
    directory it is saved under."""
    sets = [prompt_set(kind, body) for kind, body in response.items()]
    unsafe = [name for one in sets for name in one.templates if not is_safe_name(name)]
    if unsafe:
        raise CoreServerError(f"refusing unsafe prompt template names {unsafe}")
    return sets


def prompt_set(kind: str, body: object) -> PromptSet:
    fields = body if isinstance(body, dict) else {}
    templates = fields.get("templates")
    return PromptSet(
        kind=kind,
        fingerprint=str(fields.get("fingerprint")),
        templates={
            str(name): str(text)
            for name, text in (templates.items() if isinstance(templates, dict) else [])
        },
    )


def is_safe_name(name: str) -> bool:
    path = PurePosixPath(name)
    return bool(name) and not path.is_absolute() and ".." not in path.parts and "\\" not in name
