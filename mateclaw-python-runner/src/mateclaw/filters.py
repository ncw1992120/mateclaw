from dataclasses import dataclass
from typing import Any

SUPPORTED_OPERATORS = frozenset({
    "eq", "neq", "gt", "gte", "lt", "lte", "in", "not_in",
    "between", "is_null", "is_not_null",
})

@dataclass(frozen=True)
class Filter:
    field: str
    operator: str
    value: Any = None

    def as_dict(self) -> dict[str, Any]:
        if not isinstance(self.field, str) or not self.field or self.field.startswith("__"):
            raise ValueError("invalid filter field")
        if not isinstance(self.operator, str) or self.operator.lower() not in SUPPORTED_OPERATORS:
            raise ValueError(f"unsupported filter operator: {self.operator}")
        return {"field": self.field, "operator": self.operator, "value": self.value}
