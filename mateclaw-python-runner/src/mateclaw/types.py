from dataclasses import dataclass, field
from typing import Any, Callable

# 物化 prepared input 的默认行数上限：超过即显式资源超限，不允许以预览冒充完整结果
DEFAULT_MAX_INPUT_ROWS = 1_000_000


class ResourceLimitError(RuntimeError):
    """prepared input 无法在安全限额内完整读取。"""


class PreparedInputExpiredError(RuntimeError):
    """prepared input 已过期或任务已结束。"""


@dataclass(frozen=True)
class DatasetColumn:
    name: str
    title: str
    data_type: str
    nullable: bool = True
    semantic_role: str | None = None


@dataclass(frozen=True)
class DatasetInput:
    input_name: str
    schema: tuple[DatasetColumn, ...]
    rows: tuple[dict[str, Any], ...] = ()
    object_ref: dict[str, Any] | None = None
    # 服务端 rowCount（object_ref 分批时权威）；缺省与 rows 长度一致
    row_count: int | None = None
    # 受控批次读取器：cursor -> {"rows": [...], "cursor": int, "last": bool}
    # 仅 object_ref 大结果使用；由 DatasetClient 注入，脚本侧不可构造伪造数据
    _batch_reader: Callable[[int], dict[str, Any]] | None = field(default=None, repr=False, compare=False)
    _max_rows: int = field(default=DEFAULT_MAX_INPUT_ROWS, repr=False, compare=False)

    def _materialized_rows(self) -> tuple[dict[str, Any], ...]:
        """完整物化：内联行集直接返回；object_ref 大结果走受控批次读取直至完整或显式超限。"""
        if self.object_ref is None:
            return self.rows
        if self._batch_reader is None:
            raise RuntimeError(
                f"prepared input '{self.input_name}' requires controlled batch reading "
                "but no batch reader is attached; re-read via datasets.input()")
        total = self.row_count if self.row_count is not None else self._max_rows
        if total > self._max_rows:
            raise ResourceLimitError(
                f"prepared input '{self.input_name}' has {total} rows which exceeds the "
                f"materialization limit {self._max_rows}; aborting instead of returning a truncated preview")
        collected: list[dict[str, Any]] = []
        cursor = 0
        while True:
            batch = self._batch_reader(cursor)
            batch_rows = batch.get("rows") or []
            collected.extend(batch_rows)
            if batch.get("last"):
                break
            next_cursor = batch.get("cursor")
            if next_cursor is None or next_cursor <= cursor:
                # 服务端未推进游标：防死循环
                raise RuntimeError(f"prepared input '{self.input_name}' batch cursor did not advance")
            cursor = next_cursor
            if len(collected) > self._max_rows:
                raise ResourceLimitError(
                    f"prepared input '{self.input_name}' exceeds the materialization limit {self._max_rows}")
        if self.row_count is not None and len(collected) != self.row_count:
            raise RuntimeError(
                f"prepared input '{self.input_name}' materialized {len(collected)} rows "
                f"but server reported rowCount={self.row_count}")
        return tuple(collected)

    def to_pandas(self):
        import pandas as pd
        return pd.DataFrame.from_records(self._materialized_rows())

    def to_polars(self):
        import polars as pl
        return pl.DataFrame(self._materialized_rows())

    def iter_batches(self, batch_size: int = 10_000):
        if batch_size <= 0: raise ValueError("batch_size must be positive")
        if self.object_ref is None:
            for start in range(0, len(self.rows), batch_size):
                yield self.rows[start:start + batch_size]
            return
        cursor = 0
        while True:
            batch = self._batch_reader(cursor)
            rows = batch.get("rows") or []
            for start in range(0, len(rows), batch_size):
                yield rows[start:start + batch_size]
            if batch.get("last"):
                break
            next_cursor = batch.get("cursor")
            if next_cursor is None or next_cursor <= cursor:
                raise RuntimeError(f"prepared input '{self.input_name}' batch cursor did not advance")
            cursor = next_cursor
