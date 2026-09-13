from dataclasses import dataclass
from typing import Any

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

    def to_pandas(self):
        import pandas as pd
        return pd.DataFrame.from_records(self.rows)

    def to_polars(self):
        import polars as pl
        return pl.DataFrame(self.rows)

    def iter_batches(self, batch_size: int = 10_000):
        if batch_size <= 0: raise ValueError("batch_size must be positive")
        for start in range(0, len(self.rows), batch_size):
            yield self.rows[start:start + batch_size]
