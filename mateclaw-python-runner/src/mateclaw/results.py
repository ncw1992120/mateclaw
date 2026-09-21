"""统一脚本输出契约：把用户脚本 result 标准化为版本化 envelope。

kind: table | scalar | message；schemaVersion 固定 "1.0"。
标准化失败抛 ResultContractError（含 path/expected/actual/suggestion），
Runner 父进程据此返回 OUTPUT_CONTRACT_ERROR，不掩盖非法输出。
"""
from __future__ import annotations

import math
from datetime import date, datetime
from typing import Any

SCHEMA_VERSION = "1.0"
DATA_TYPES = ("string", "number", "boolean", "date", "datetime")

_NUMBER_TYPES = {"number"}
_MESSAGE_FLAG = "__mateclaw_message__"


class ResultContractError(Exception):
    def __init__(self, path: str, expected: str, actual: str, suggestion: str):
        self.path = path
        self.expected = expected
        self.actual = actual
        self.suggestion = suggestion
        super().__init__(f"{path}: expected {expected}, got {actual}; {suggestion}")

    def as_dict(self) -> dict[str, Any]:
        return {
            "stage": "output-validation",
            "path": self.path,
            "expected": self.expected,
            "actual": self.actual,
            "suggestion": self.suggestion,
        }


def message_result(message: str, level: str = "info") -> dict[str, Any]:
    """用户脚本显式返回说明性消息（例如「没有满足条件的数据」）。"""
    return {_MESSAGE_FLAG: True, "level": level, "message": message}


def _type_name(value: Any) -> str:
    return type(value).__name__


def _is_nan_or_nat(value: Any) -> bool:
    # float('nan') 与 pandas.NaT 都满足 self-unequal；普通值恒等于自身
    if isinstance(value, float) and math.isnan(value):
        return True
    try:
        return bool(value != value)
    except Exception:
        return False


def _infer_data_type(value: Any) -> str | None:
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, datetime):
        return "datetime"
    if isinstance(value, date):
        return "date"
    if isinstance(value, str):
        return "string"
    return None


def _normalize_cell(value: Any, path: str) -> Any:
    if value is None or _is_nan_or_nat(value):
        return None
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        if isinstance(value, float) and math.isinf(value):
            raise ResultContractError(path, "number", "infinity", "结果数值不能是正负无穷，请在上游清洗")
        return value
    if isinstance(value, str):
        return value
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, date):
        return value.isoformat()
    raise ResultContractError(path, "可规范化单元格值", _type_name(value), "统一该列类型或转换为基础类型")


def _columns_from_rows(rows: list[dict[str, Any]], base_path: str, names: list[str] | None = None) -> list[dict[str, Any]]:
    """列从首现顺序收集，类型从**原始值**推断（不能只看第一行、也不能在标准化之后）。"""
    if names is None:
        names = []
        seen: set[str] = set()
        for index, row in enumerate(rows):
            if not isinstance(row, dict):
                raise ResultContractError(
                    f"{base_path}.rows[{index}]", "object", _type_name(row), "列表每一行都必须是对象")
            for key in row:
                if key not in seen:
                    seen.add(key)
                    names.append(key)

    columns: list[dict[str, Any]] = []
    for name in names:
        data_type: str | None = None
        nullable = False
        for index, row in enumerate(rows):
            value = row.get(name)
            if value is None or _is_nan_or_nat(value):
                nullable = True
                continue
            cell_type = _infer_data_type(value)
            if cell_type is None:
                raise ResultContractError(
                    f"{base_path}.rows[{index}].{name}", "可规范化值", _type_name(value),
                    f"统一列 {name} 的类型或转换为基础类型")
            if data_type is None:
                data_type = cell_type
            elif cell_type != data_type and not (data_type in {"date", "datetime"} and cell_type in {"date", "datetime"}):
                raise ResultContractError(
                    f"{base_path}.rows[{index}].{name}", data_type, cell_type,
                    f"统一列 {name} 的类型或转换为基础类型")
        columns.append({
            "name": name,
            "title": name,
            "dataType": data_type or "string",
            "nullable": nullable or not rows,
        })
    return columns


def _table_envelope(columns: list[dict[str, Any]], rows: list[dict[str, Any]], source_inputs: tuple) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": "table",
        "data": {"columns": columns, "rows": rows},
        "meta": {"rowCount": len(rows), "truncated": False, "sourceInputs": list(source_inputs)},
    }


def _scalar_envelope(value: Any, source_inputs: tuple) -> dict[str, Any]:
    normalized = _normalize_cell(value, "result.data.value")
    data_type = _infer_data_type(value) or "string"
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": "scalar",
        "data": {"value": normalized, "dataType": data_type},
        "meta": {"rowCount": 0, "truncated": False, "sourceInputs": list(source_inputs)},
    }


def _message_envelope(raw: dict[str, Any]) -> dict[str, Any]:
    message = raw.get("message")
    level = raw.get("level", "info")
    if not isinstance(message, str) or not message:
        raise ResultContractError("result.data.message", "string", _type_name(message), "消息内容必须是非空字符串")
    if level not in {"info", "warning"}:
        raise ResultContractError("result.data.level", "info|warning", _type_name(level), "消息级别只允许 info 或 warning")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": "message",
        "data": {"level": level, "message": message},
        "meta": {"rowCount": 0, "truncated": False, "sourceInputs": []},
    }


def _is_dataframe(value: Any) -> bool:
    # pandas DataFrame / polars DataFrame / DatasetInput 都是鸭子类型识别，避免强依赖
    has_pandas_shape = hasattr(value, "columns") and hasattr(value, "to_dict") and not isinstance(value, dict)
    has_polars_shape = hasattr(value, "to_dicts") and hasattr(value, "columns")
    return has_pandas_shape or has_polars_shape


def _table_from_dataframe(value: Any, base_path: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    columns_list = [str(name) for name in list(value.columns)]
    if len(set(columns_list)) != len(columns_list):
        raise ResultContractError(f"{base_path}.columns", "唯一列名", "重复列", "DataFrame 列名必须唯一")
    if hasattr(value, "to_dicts"):
        raw_records = value.to_dicts()
    else:
        raw_records = value.to_dict(orient="records")
    columns = _columns_from_rows(raw_records, base_path, names=columns_list)
    normalized_rows: list[dict[str, Any]] = []
    for index, record in enumerate(raw_records):
        normalized_rows.append({
            name: _normalize_cell(record.get(name), f"{base_path}.rows[{index}].{name}")
            for name in columns_list
        })
    return columns, normalized_rows


def _rows_from_dataframe(value: Any, base_path: str) -> list[dict[str, Any]]:
    try:
        import pandas as pd  # noqa: F401  仅用于 NaN/NaT 判定
        has_pandas = True
    except Exception:
        has_pandas = False
        pd = None  # type: ignore[assignment]

    columns = [str(name) for name in list(value.columns)]
    if len(set(columns)) != len(columns):
        raise ResultContractError(f"{base_path}.columns", "唯一列名", "重复列", "DataFrame 列名必须唯一")
    if hasattr(value, "to_dicts"):
        records = value.to_dicts()
    else:
        records = value.to_dict(orient="records")

    normalized_rows: list[dict[str, Any]] = []
    for index, record in enumerate(records):
        row: dict[str, Any] = {}
        for name in columns:
            cell = record.get(name)
            if has_pandas and pd.isna(cell):
                cell = None
            row[name] = _normalize_cell(cell, f"{base_path}.rows[{index}].{name}")
        normalized_rows.append(row)
    return normalized_rows


def normalize_result(value: Any, source_inputs: tuple = ()) -> dict[str, Any]:
    """用户脚本 result → 统一 envelope；不可规范化时抛 ResultContractError。"""
    if value is None:
        raise ResultContractError("result", "table|scalar|message", "None",
                                  "脚本必须给全局变量 result 赋值（空表用空列表）")

    if isinstance(value, dict) and value.get(_MESSAGE_FLAG):
        return _message_envelope(value)

    if isinstance(value, bool) or isinstance(value, (int, float, str)):
        return _scalar_envelope(value, source_inputs)

    if _is_dataframe(value):
        columns, rows = _table_from_dataframe(value, "result.data")
        return _table_envelope(columns, rows, source_inputs)

    # DatasetInput：schema 直接映射列，rows 标准化
    if hasattr(value, "schema") and hasattr(value, "rows") and hasattr(value, "input_name"):
        rows = [_normalize_record(row, "result.data", index) for index, row in enumerate(value.rows)]
        columns = [{
            "name": column.name,
            "title": column.title,
            "dataType": _map_sdk_data_type(column.data_type),
            "nullable": column.nullable,
        } for column in value.schema]
        return {
            "schemaVersion": SCHEMA_VERSION,
            "kind": "table",
            "data": {"columns": columns, "rows": rows},
            "meta": {"rowCount": len(rows), "truncated": False, "sourceInputs": list(source_inputs)},
        }

    if isinstance(value, dict):
        records = [value]
        return _table_envelope(
            _columns_from_rows(records, "result.data"),
            _rows_from_records(records, "result.data"),
            source_inputs)

    if isinstance(value, (list, tuple)):
        records = list(value)
        return _table_envelope(
            _columns_from_rows(records, "result.data"),
            _rows_from_records(records, "result.data"),
            source_inputs)

    raise ResultContractError("result", "table|scalar|message", _type_name(value),
                              "脚本 result 只支持 DataFrame/列表/字典/标量/message_result")


def _rows_from_records(records: list[Any], source_inputs: tuple) -> list[dict[str, Any]]:
    normalized_rows: list[dict[str, Any]] = []
    for index, record in enumerate(records):
        if not isinstance(record, dict):
            raise ResultContractError(
                f"result.data.rows[{index}]", "object", _type_name(record), "列表每一行都必须是对象")
        normalized_rows.append({
            key: _normalize_cell(cell, f"result.data.rows[{index}].{key}")
            for key, cell in record.items()
        })
    return normalized_rows


def _normalize_record(row: dict[str, Any], base_path: str, index: int) -> dict[str, Any]:
    return {
        key: _normalize_cell(cell, f"{base_path}.rows[{index}].{key}")
        for key, cell in row.items()
    }


def _map_sdk_data_type(data_type: str) -> str:
    lowered = (data_type or "").lower()
    if "bool" in lowered:
        return "boolean"
    if "date" in lowered and "time" in lowered:
        return "datetime"
    if "timestamp" in lowered:
        return "datetime"
    if "int" in lowered or "float" in lowered or "double" in lowered or "number" in lowered or "decimal" in lowered:
        return "number"
    if lowered == "date":
        return "date"
    return "string"
