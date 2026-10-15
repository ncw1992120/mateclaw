"""Run one dashboard Python task as a short-lived, non-HTTP process."""

from __future__ import annotations

import argparse
import contextlib
import json
import os
import sys
import tempfile
import time
import traceback
import urllib.request
from pathlib import Path
from typing import Any, Sequence, TextIO

from mateclaw.datasets import DatasetClient
from mateclaw import diagnostics
from mateclaw.results import ResultContractError, normalize_result
from mateclaw.types import DatasetInput
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


def _error_context(exc: BaseException, script: str) -> dict[str, Any]:
    """只定位脚本和 SDK 输入转换，不收集局部变量或整份业务数据。"""
    context: dict[str, Any] = {}
    frame = exc.__traceback__
    while frame is not None:
        if frame.tb_frame.f_code.co_filename == "user_script.py":
            context["scriptLine"] = frame.tb_lineno
        owner = frame.tb_frame.f_locals.get("self")
        function = frame.tb_frame.f_code.co_name
        if isinstance(owner, DatasetInput) and function in ("to_polars", "to_pandas"):
            context.update(inputName=owner.input_name,
                           conversion="Polars" if function == "to_polars" else "pandas")
        frame = frame.tb_next
    if isinstance(exc, SyntaxError) and exc.filename == "user_script.py":
        context["scriptLine"] = exc.lineno
    lines = script.splitlines()
    line = context.get("scriptLine")
    if isinstance(line, int) and 0 < line <= len(lines):
        marker = next((index + 1 for index, text in enumerate(lines)
                       if text.strip() in ("# ===== 用户处理区域 =====", "# ===== 自定义处理代码（可编辑） =====")), None)
        context["region"] = "unknown" if marker is None else "user" if line > marker else "system"
        if marker is not None and line > marker:
            context["userLine"] = line - marker
        context["sourceLines"] = [{"line": index + 1, "code": lines[index][:240]}
                                  for index in range(max(0, line - 3), min(len(lines), line + 2))]
    return context


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
        "diagnostics": diagnostics,
    }
    status = "SUCCEEDED"
    result: dict[str, Any] | None = None
    error: str | None = None
    return_code = 0
    phase_timings: dict[str, int] = {}

    try:
        with contextlib.redirect_stdout(_Tee(sys.stdout, stdout_buffer)), contextlib.redirect_stderr(sys.stderr):
            phase_started = time.perf_counter_ns()
            try:
                compiled_script = compile(request.script, "user_script.py", "exec")
            finally:
                phase_timings["script_compile_ms"] = (time.perf_counter_ns() - phase_started) // 1_000_000
            phase_started = time.perf_counter_ns()
            try:
                exec(compiled_script, namespace, namespace)
            finally:
                phase_timings["script_execute_ms"] = (time.perf_counter_ns() - phase_started) // 1_000_000
            phase_started = time.perf_counter_ns()
            try:
                result = normalize_result(namespace.get("result"))
            except ResultContractError as exc:
                status = "OUTPUT_CONTRACT_ERROR"
                error = json.dumps(exc.as_dict(), ensure_ascii=False)
                return_code = 1
            finally:
                phase_timings["result_normalize_ms"] = (time.perf_counter_ns() - phase_started) // 1_000_000
    except BaseException as exc:
        status = "FAILED"
        error = "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))[-10_000:]
        context = _error_context(exc, request.script)
        if isinstance(exc, diagnostics.DataValidationError):
            # 沿用 error 字符串契约，内部增加 JSON 诊断；无需新增数据库字段。
            detail = {**exc.as_dict(), "scriptLine": context.get("scriptLine")}
            error = json.dumps({"code": "DATA_VALIDATION_ERROR", "message": str(exc),
                                "errorDetails": detail, "errorContext": context, "traceback": error}, ensure_ascii=False)
        else:
            exception_type = type(exc).__qualname__
            if type(exc).__module__ != "builtins":
                exception_type = f"{type(exc).__module__}.{exception_type}"
            error = json.dumps({"code": "PYTHON_EXECUTION_ERROR", "exceptionType": exception_type,
                                "message": str(exc)[:10_000], "errorContext": context,
                                "traceback": error}, ensure_ascii=False)
        return_code = 1

    output = stdout_buffer.getvalue()
    output_limited = stdout_buffer.exceeded
    if output_limited:
        status = "OUTPUT_LIMIT"
        result = None
        error = "stdout exceeded configured byte limit"
    elif status == "SUCCEEDED" and result is not None:
        phase_started = time.perf_counter_ns()
        encoded_result = json.dumps(result, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
        phase_timings["result_serialize_ms"] = (time.perf_counter_ns() - phase_started) // 1_000_000
        if len(encoded_result) > request.limits.max_result_bytes:
            if request.resultUploadEndpoint and result.get("kind") == "table":
                reference = _upload_table_result(result, request.resultUploadEndpoint, request.readToken, directory)
                if reference is not None:
                    return TaskResponse(
                        taskId=request.taskId,
                        status="RESULT_REF",
                        output=output.replace(request.readToken, "[REDACTED]"),
                        outputRef=reference,
                        error=None,
                        stats={"returncode": return_code, **phase_timings},
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
        stats={"returncode": return_code, **phase_timings},
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
