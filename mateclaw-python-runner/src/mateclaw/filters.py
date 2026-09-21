from dataclasses import dataclass
from typing import Any

SUPPORTED_OPERATORS = frozenset({
    "eq", "neq", "gt", "gte", "lt", "lte", "in", "not_in",
    "between", "contains", "is_null", "is_not_null",
})

# 单个 in/not_in 条件允许的最大值数量（与 DataAgent 读取策略一致）
MAX_IN_VALUES = 1000


def validate_filter_value(operator: str, value: Any) -> None:
    """按操作符校验值形状；跨数据集场景在发请求前拦截非法集合。"""
    if operator in {"in", "not_in"}:
        if not isinstance(value, (list, tuple)):
            raise ValueError(f"filter operator {operator} requires a list value")
        if len(value) > MAX_IN_VALUES:
            raise ValueError(f"filter value count exceeds {MAX_IN_VALUES}")
    elif operator == "between":
        if not isinstance(value, (list, tuple)) or len(value) != 2:
            raise ValueError("filter operator between requires a two-element list value")
    elif operator in {"is_null", "is_not_null"}:
        if value is not None:
            raise ValueError(f"filter operator {operator} must not carry a value")


@dataclass(frozen=True)
class Filter:
    field: str
    operator: str
    value: Any = None
    role: str = "dimension"

    def as_dict(self) -> dict[str, Any]:
        if not isinstance(self.field, str) or not self.field or self.field.startswith("__"):
            raise ValueError("invalid filter field")
        if not isinstance(self.operator, str) or self.operator.lower() not in SUPPORTED_OPERATORS:
            raise ValueError(f"unsupported filter operator: {self.operator}")
        if not isinstance(self.role, str) or self.role.lower() not in {"dimension", "measure"}:
            raise ValueError(f"unsupported filter role: {self.role}")
        validate_filter_value(self.operator.lower(), self.value)
        return {"field": self.field, "role": self.role.lower(), "operator": self.operator, "value": self.value}
