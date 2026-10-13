"""pandas 数据转换诊断：行号按当前输入顺序从 1 开始，最多返回 10 个错误样例。"""

from decimal import Decimal, InvalidOperation
from typing import Any

import pandas as pd


class DataValidationError(ValueError):
    """保留结构化定位信息；异常文本不含问题值，避免它进入普通服务日志。"""

    def __init__(self, dataset: str, field: str, error_count: int, issues: list[dict[str, Any]]):
        self.detail = {"dataset": dataset[:240], "field": field[:240], "errorCount": error_count, "issues": issues}
        super().__init__(f"数据集 {dataset[:240]}，字段 {field[:240]}：{error_count} 条数据校验失败")

    def as_dict(self) -> dict[str, Any]:
        return self.detail


def _empty(value: Any) -> bool:
    if isinstance(value, str):
        return not value.strip()
    return pd.api.types.is_scalar(value) and bool(pd.isna(value))


def _preview(value: Any) -> str:
    # 只返回一个单元格的预览，不返回整条记录。
    text = repr(value)
    return text if len(text) <= 120 else text[:120] + "…"


def _number(value: Any) -> float:
    if isinstance(value, bool):
        raise ValueError("boolean is not numeric")
    number = Decimal(str(value).strip())
    if not number.is_finite():
        raise ValueError("non-finite number")
    converted = float(number)
    if not Decimal(str(converted)).is_finite():
        raise ValueError("number out of range")
    return converted


def _integer(value: Any) -> int:
    if isinstance(value, bool):
        raise ValueError("boolean is not integer")
    number = Decimal(str(value).strip())
    if not number.is_finite() or number != number.to_integral_value() or not -(2**63) <= number < 2**63:
        raise ValueError("not a signed 64-bit integer")
    return int(number)


def _convert(frame, *, dataset, field, row_keys, null_policy, default, converter, reason, dtype=None, null_value=pd.NA):
    if not isinstance(frame, pd.DataFrame):
        raise TypeError("diagnostics 需要 pandas DataFrame")
    if null_policy not in {"error", "keep", "fill"}:
        raise ValueError("null_policy 必须是 error、keep 或 fill")
    keys = list(row_keys or [])
    if len(keys) > 5:
        raise ValueError("row_keys 最多配置 5 个业务标识字段")
    for name in [field, *keys]:
        if name not in frame.columns:
            raise ValueError(f"数据集 {dataset} 缺少字段 {name}")
        if list(frame.columns).count(name) != 1:
            raise ValueError(f"数据集 {dataset} 字段 {name} 重复，无法定位")
    fill_value = None
    if null_policy == "fill":
        if _empty(default):
            raise ValueError("null_policy=fill 必须提供非空 default")
        fill_value = converter(default)
    values, issues, error_count = [], [], 0
    # 不依赖 DataFrame index：即使 index 重复，也能准确报告当前数据第几行。
    for position, value in enumerate(frame[field].tolist()):
        empty = _empty(value)
        try:
            if empty and null_policy == "error":
                raise ValueError("null")
            converted = (null_value if null_policy == "keep" else fill_value) if empty else converter(value)
            values.append(converted)
        except (ValueError, TypeError, OverflowError, InvalidOperation):
            error_count += 1
            values.append(pd.NA)
            if len(issues) < 10:
                issues.append({
                    "rowNumber": position + 1,
                    "rowKeys": {key: str(frame[key].iloc[position])[:120] for key in keys},
                    "valuePreview": _preview(value),
                    "reason": "空值不允许" if empty else reason,
                })
    if error_count:
        raise DataValidationError(str(dataset), str(field), error_count, issues)
    return pd.Series(values, index=frame.index, name=field, dtype=dtype)


def to_numeric(frame, *, dataset, field, row_keys=None, null_policy="error", default=None):
    """转换为 Float64 数字。精确整数使用 to_integer；空值策略需显式选择。"""
    return _convert(frame, dataset=dataset, field=field, row_keys=row_keys, null_policy=null_policy,
                    default=default, converter=_number, reason="无法转换为数字", dtype="Float64")


def to_integer(frame, *, dataset, field, row_keys=None, null_policy="error", default=None):
    """转换有符号 64 位整数；拒绝小数和越界值，避免静默截断。"""
    return _convert(frame, dataset=dataset, field=field, row_keys=row_keys, null_policy=null_policy,
                    default=default, converter=_integer, reason="无法转换为整数或超出范围", dtype="Int64")


def to_datetime(frame, *, dataset, field, row_keys=None, null_policy="error", default=None, format=None):
    """转换日期；建议显式传 format，例如 %Y-%m-%d，避免日期格式歧义。"""
    def convert(value):
        parsed = pd.to_datetime(value, format=format, errors="raise")
        if not isinstance(parsed, pd.Timestamp) or pd.isna(parsed):
            raise ValueError("invalid date")
        return parsed
    return _convert(frame, dataset=dataset, field=field, row_keys=row_keys, null_policy=null_policy,
                    default=default, converter=convert, reason="无法转换为日期", null_value=pd.NaT)


def required(frame, *, dataset, field, row_keys=None):
    """检查必填字段：None、NaN、NaT、空字符串和纯空格均视为空值。"""
    return _convert(frame, dataset=dataset, field=field, row_keys=row_keys, null_policy="error",
                    default=None, converter=lambda value: value, reason="必填字段不允许为空")
