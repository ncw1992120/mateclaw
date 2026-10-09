"""The strategy summary KPI script must return an empty table for empty queries."""
from pathlib import Path

import pandas as pd


REPO_ROOT = Path(__file__).parents[3]
SCRIPT_PATH = REPO_ROOT / "docs" / "策略解读" / "策略概括.py"


class FakeDatasets:
    def __init__(self, frame):
        self.frame = frame

    def input(self, input_name):
        assert input_name == "table1"
        return self

    def to_polars(self):
        return self.frame


def execute(frame):
    namespace = {"datasets": FakeDatasets(frame)}
    source = SCRIPT_PATH.read_text(encoding="utf-8")
    exec(compile(source, str(SCRIPT_PATH), "exec"), namespace)
    return namespace["result"]


def test_empty_strategy_summary_input_returns_empty_records():
    assert execute(pd.DataFrame()) == []


def test_non_empty_strategy_summary_input_returns_records():
    frame = pd.DataFrame([{"metric_time": "2026-09-01", "digo_strategy_cnt_distr_1": 2}])

    assert execute(frame) == [{"metric_time": "2026-09-01", "digo_strategy_cnt_distr_1": 2}]
