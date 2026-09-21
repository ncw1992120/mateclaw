from typing import Any
from pydantic import BaseModel, Field, ConfigDict, field_validator
from urllib.parse import urlparse

class Limits(BaseModel):
    timeout_seconds: int = Field(default=60, ge=1, le=900)
    max_stdout_bytes: int = Field(default=50_000, ge=1, le=5_000_000)
    max_result_bytes: int = Field(default=1_000_000, ge=1, le=5_000_000)
    max_memory_mb: int = Field(default=1024, ge=128, le=4096)
    max_file_bytes: int = Field(default=100 * 1024 * 1024, ge=1 * 1024 * 1024, le=500 * 1024 * 1024)

class TaskRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    taskId: str = Field(min_length=1, max_length=128)
    script: str = Field(min_length=1, max_length=1_000_000)
    inputCatalog: dict[str, Any] = Field(default_factory=dict)
    parameters: dict[str, Any] = Field(default_factory=dict)
    limits: Limits = Field(default_factory=Limits)
    datasetReadEndpoint: str
    resultUploadEndpoint: str | None = None
    readToken: str = Field(min_length=1)

    @field_validator("datasetReadEndpoint")
    @classmethod
    def validate_internal_endpoint(cls, value: str) -> str:
        parsed = urlparse(value)
        if parsed.scheme not in {"http", "https"} or parsed.username or parsed.password or parsed.path == "":
            raise ValueError("datasetReadEndpoint must be a plain HTTP(S) URL")
        # Docker Desktop 本地联调时，Runner 容器通过 host.docker.internal
        # 回调宿主机上的 DataAgent；生产环境仍应使用 dataagent 内部服务名。
        allowed = {"dataagent", "mateclaw-dataagent", "mateclaw-server", "localhost", "127.0.0.1", "host.docker.internal"}
        if parsed.hostname not in allowed:
            raise ValueError("datasetReadEndpoint host is not allowlisted")
        return value.rstrip("/")

    @field_validator("resultUploadEndpoint")
    @classmethod
    def validate_result_endpoint(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return cls.validate_internal_endpoint(value)

class TaskResponse(BaseModel):
    taskId: str
    status: str
    output: str | None = None
    # 结构化 envelope（table/scalar/message）；OUTPUT_CONTRACT_ERROR 时为 None
    result: dict[str, Any] | None = None
    outputRef: dict[str, Any] | None = None
    stats: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
