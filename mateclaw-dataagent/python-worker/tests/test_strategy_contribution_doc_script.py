"""Regression coverage for the documented strategy contribution pipeline."""
from pathlib import Path

import pandas as pd


REPO_ROOT = Path(__file__).parents[3]
SCRIPT_PATH = REPO_ROOT / "docs" / "策略解读" / "策略贡献.py"


class FakeDatasets:
    def __init__(self, frames):
        self.frames = frames

    def input(self, input_name):
        return FakeInput(self.frames[input_name])


class FakeInput:
    def __init__(self, frame):
        self.frame = frame

    def to_polars(self):
        return self.frame


def execute_script(wd, zb):
    namespace = {
        "datasets": FakeDatasets({
            "table_wd": wd,
            "table_zb": zb,
        }),
    }
    source = SCRIPT_PATH.read_text(encoding="utf-8")
    exec(compile(source, str(SCRIPT_PATH), "exec"), namespace)
    return namespace["result"]


def test_empty_inputs_return_empty_result_even_when_schema_is_unavailable():
    result = execute_script(pd.DataFrame(), pd.DataFrame())

    assert result == []


def test_script_requires_metric_id_in_dimension_dataset():
    dimensions = {
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
    }
    wd = pd.DataFrame([{**dimensions, "metric_name": "metric_x"}])
    zb = pd.DataFrame([{**dimensions, "digo_metric_x": 120, "digo_metric_x_trans_user_cnt": 8}])

    import pytest
    with pytest.raises(ValueError, match="缺少 metric_id 字段"):
        execute_script(wd, zb)


def test_script_uses_dataset_metric_id_as_authority_over_display_name():
    dimensions = {
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
    }
    wd = pd.DataFrame([{
        **dimensions,
        "metric_id": "metric_x",
        "metric_name": "经纪个人客户场内公募非货当年净买入",
    }])
    zb = pd.DataFrame([{
        **dimensions,
        "digo_metric_x": 120,
        "digo_metric_x_trans_user_cnt": 8,
    }])

    result = execute_script(wd, zb)

    assert result == [{"转化规模": 120, "转化人数": 8}]
