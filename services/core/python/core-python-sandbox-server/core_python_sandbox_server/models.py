"""Request/response models for the sandbox execution API.

Every field name is exposed on the wire as camelCase, matching the convention the rest of
Vader's Java-emitted API surface already uses -- core-server deserializes these responses
straight into its own camelCase DTOs with no field-name translation needed.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Base model that serializes and accepts field names as camelCase."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ExecutionRequest(CamelModel):
    """Code to run in the sandbox's persistent workspace, plus any files to stage first."""

    code: str
    files: dict[str, str] = Field(default_factory=dict)
    timeout_seconds: float | None = None


class ExecutionResult(CamelModel):
    """The outcome of running one ExecutionRequest."""

    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool
