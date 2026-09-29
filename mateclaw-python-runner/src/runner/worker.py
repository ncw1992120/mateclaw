"""Run one dashboard Python task as a short-lived, non-HTTP process."""

from __future__ import annotations

import argparse
import contextlib
import json
import os
import sys
import tempfile
import traceback
import urllib.request
from pathlib import Path
from typing import Any, Sequence, TextIO

from mateclaw.datasets import DatasetClient
from mateclaw.results import ResultContractError, normalize_result
from .models import TaskRequest, TaskResponse


class _LimitedBuffer:
    def __init__(self, limit: int):
        self.limit = limit
        self.data = bytearray()
        self.total_bytes = 0

    def write(self, value: str) -> int:
        encoded = value.encode("utf-8", errors="replace")
        self.total_bytes += len(encoded)
        remaining = max(0, self.limit - len(self.data))
        self.data.extend(encoded[:remaining])
        return len(value)

    @property
    def exceeded(self) -> bool:
        return self.total_bytes > self.limit

    def getvalue(self) -> str:
        return self.data.decode("utf-8", errors="ignore")


class _Tee:
    """Capture task output for the response while keeping it visible to the supervisor."""

    def __init__(self, target: TextIO, buffer: _LimitedBuffer):
        self.target = target
        self.buffer = buffer

    def write(self, value: str) -> int:
        self.target.write(value)
        self.target.flush()
        self.buffer.write(value)
        return len(value)

    def flush(self) -> None:
        self.target.flush()

    def isatty(self) -> bool:
        return self.target.isatty()

    @property
    def encoding(self) -> str | None:
        return self.target.encoding


def _upload_table_result(envelope: dict[str, Any], endpoint: str, token: str, directory: Path) -> dict[str, Any] | None:
    try:
        import pyarrow as pa
        import pyarrow.parquet as pq

        parquet_path = directory / "result.parquet"
        rows = envelope["data"]["rows"]
        pq.write_table(pa.Table.from_pylist(rows), parquet_path, compression="snappy")
        request = urllib.request.Request(
            endpoint,
            data=parquet_path.read_bytes(),
            method="POST",
            headers={
                "Content-Type": "application/vnd.apache.parquet",
                "Authorization": f"Bearer {token}",
            },
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            body = json.loads(response.read())
        if body.get("code") != 200 or not isinstance(body.get("data"), dict):
            return None
        reference = body["data"]
        return {
            **reference,
            "schemaVersion": envelope.get("schemaVersion"),
            "kind": envelope.get("kind"),
            "columns": envelope["data"]["columns"],
            "rowCount": envelope["meta"]["rowCount"],
        }
    except Exception:
        return None


def execute_task(request: TaskRequest, work_directory: str | Path | None = None) -> TaskResponse:
    """Execute a validated request and return the compatible task response shape."""
    directory = Path(work_directory) if work_directory else Path.cwd()
    directory.mkdir(parents=True, exist_ok=True)
    stdout_buffer = _LimitedBuffer(request.limits.max_stdout_bytes)
    namespace: dict[str, Any] = {
        "__name__": "__main__",
        "__file__": "user_script.py",
        "datasets": DatasetClient(
            request.datasetReadEndpoint,
            request.readToken,
            request.parameters,
            input_endpoint=request.datasetInputEndpoint,
            prefer_prepared_input=request.preferPreparedInputs,
        ),
        "parameters": request.parameters,
        "input_catalog": request.inputCatalog,
    }
    status = "SUCCEEDED"
    result: dict[str, Any] | None = None
    error: str | None = None
    return_code = 0

    try:
        with contextlib.redirect_stdout(_Tee(sys.stdout, stdout_buffer)), contextlib.redirect_stderr(sys.stderr):
            exec(compile(request.script, "user_script.py", "exec"), namespace, namespace)
            try:
                result = normalize_result(namespace.get("result"))
            except ResultContractError as exc:
                status = "OUTPUT_CONTRACT_ERROR"
                error = json.dumps(exc.as_dict(), ensure_ascii=False)
                return_code = 1
    except BaseException as exc:
        status = "FAILED"
        error = "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))[-10_000:]
        return_code = 1

    output = stdout_buffer.getvalue()
    output_limited = stdout_buffer.exceeded
    if output_limited:
        status = "OUTPUT_LIMIT"
        result = None
        error = "stdout exceeded configured byte limit"
    elif status == "SUCCEEDED" and result is not None:
        encoded_result = json.dumps(result, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
        if len(encoded_result) > request.limits.max_result_bytes:
            if request.resultUploadEndpoint and result.get("kind") == "table":
                reference = _upload_table_result(result, request.resultUploadEndpoint, request.readToken, directory)
                if reference is not None:
                    return TaskResponse(
                        taskId=request.taskId,
                        status="RESULT_REF",
                        output=output.replace(request.readToken, "[REDACTED]"),
                        outputRef=reference,
                        error=stderr.replace(request.readToken, "[REDACTED]") or None,
                        stats={"returncode": return_code},
                    )
            status = "RESULT_LIMIT"
            result = None
            error = "result exceeded configured byte limit"

    return TaskResponse(
        taskId=request.taskId,
        status=status,
        output=output.replace(request.readToken, "[REDACTED]"),
        result=result if status == "SUCCEEDED" else None,
        error=error.replace(request.readToken, "[REDACTED]") if error else None,
        stats={"returncode": return_code},
    )


def _write_response(path: Path, response: TaskResponse) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(response.model_dump(mode="json", by_alias=True), ensure_ascii=False, allow_nan=False),
        encoding="utf-8",
    )


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Execute one MateClaw dashboard Python task")
    parser.add_argument("--request", required=True, type=Path)
    parser.add_argument("--response", required=True, type=Path)
    arguments = parser.parse_args(argv)

    try:
        payload = json.loads(arguments.request.read_text(encoding="utf-8"))
        request = TaskRequest.model_validate(payload)
    except Exception as exc:
        response = TaskResponse(
            taskId="invalid-request",
            status="FAILED",
            error=str(exc)[:10_000],
            stats={"returncode": 2},
        )
        _write_response(arguments.response, response)
        return 2

    try:
        response = execute_task(request, arguments.response.parent)
        _write_response(arguments.response, response)
        return 0
    except Exception as exc:
        response = TaskResponse(
            taskId=request.taskId,
            status="FAILED",
            error=str(exc)[:10_000],
            stats={"returncode": 1},
        )
        _write_response(arguments.response, response)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
