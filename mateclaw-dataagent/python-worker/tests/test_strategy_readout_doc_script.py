"""The documented strategy readout script consumes the same columnar Aloudata fixtures as local-mock."""
import json
from pathlib import Path

import pandas as pd
import pytest


REPO_ROOT = Path(__file__).parents[3]
SCRIPT_PATH = REPO_ROOT / "docs" / "策略解读" / "详情表.py"
FIXTURE_PATH = (
    REPO_ROOT
    / "mateclaw-dataagent"
    / "src"
    / "main"
    / "resources"
    / "mock"
    / "aloudata"
    / "analysis_view_query_data.json"
)


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


def mock_view_rows(view_name):
    payload = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))[view_name]
    columns = payload["data"]["table"]["columns"]
    row_count = payload["data"]["table"]["total"]
    return pd.DataFrame(
        {
            name: [cell["value"] for cell in cells]
            for name, cells in columns.items()
        },
        index=range(row_count),
    )


def execute_documented_script(table_ab, table_wd):
    namespace = {
        "datasets": FakeDatasets({"table_ab": table_ab, "table_wd": table_wd}),
    }
    source = SCRIPT_PATH.read_text(encoding="utf-8")
    exec(compile(source, str(SCRIPT_PATH), "exec"), namespace)
    return namespace["result"]


def test_documented_script_joins_the_checked_in_mock_query_responses():
    zb = mock_view_rows("cljd_zcl_zb_view")
    wd = mock_view_rows("cljd_zcl_wd_view")

    result = execute_documented_script(zb, wd)

    assert len(result) == 18
    first = result[0]
    assert first["metric_id"] == "trd_fund_amt_inout_cy_jjgr"
    assert first["转化指标id"] == "trd_fund_amt_inout_cy_jjgr"
    assert first["转化规模"] == 1_250_000
    assert first["转化人数"] == 128
    assert first["转化率"] == 1_250_000 / 128
    assert first["metric_name"] == "经纪个人客户场内公募非货当年净买入"
    assert all(row["转化规模"] > 0 and row["转化人数"] > 0 for row in result)


def test_documented_script_accepts_the_two_input_aliases_in_either_order():
    zb = mock_view_rows("cljd_zcl_zb_view")
    wd = mock_view_rows("cljd_zcl_wd_view")

    normal = execute_documented_script(zb, wd)
    swapped = execute_documented_script(wd, zb)

    assert swapped == normal


def test_documented_script_accepts_live_inputs_without_unprojected_metric_time():
    # 子策略贡献表当前 table_ab/table_wd 的实际查询结果不含 metric_time；
    # table_wd 还会携带自己的 digo_ 统计列，角色应由 metric_id 与别名确定。
    dimensions = {
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
    }
    wd = pd.DataFrame([
        {
            **dimensions,
            "metric_id": "metric_x",
            "metric_name": "指标X",
            "digo_strategy_cnt": 2,
        },
    ])
    zb = pd.DataFrame([
        {**dimensions, "digo_metric_x": 120, "digo_metric_x_trans_user_cnt": 8},
    ])

    result = execute_documented_script(zb, wd)

    assert result == [{
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
        "metric_id": "metric_x",
        "metric_name": "指标X",
        "digo_strategy_cnt": 2,
        "转化指标id": "metric_x",
        "转化规模": 120,
        "转化人数": 8,
        "转化率": 15.0,
    }]


def test_documented_script_aggregates_duplicate_rows_and_handles_zero_denominator():
    dimensions = {
        "metric_time": "2026-09-01",
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
    }
    wd = pd.DataFrame([{**dimensions, "metric_id": "metric_x", "metric_name": "指标X"}])
    zb = pd.DataFrame([
        {**dimensions, "digo_metric_x": 30, "digo_metric_x_trans_user_cnt": 2},
        {**dimensions, "digo_metric_x": 70, "digo_metric_x_trans_user_cnt": 0},
    ])

    result = execute_documented_script(zb, wd)

    assert len(result) == 1
    assert result[0]["转化规模"] == 100
    assert result[0]["转化人数"] == 2
    assert result[0]["转化率"] == 50


def test_documented_script_treats_null_and_nonnumeric_metrics_as_zero():
    dimensions = {
        "metric_time": "2026-09-01",
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
    }
    wd = pd.DataFrame([{**dimensions, "metric_id": "metric_x"}])
    zb = pd.DataFrame([
        {**dimensions, "digo_metric_x": None, "digo_metric_x_trans_user_cnt": "not-a-number"},
    ])

    result = execute_documented_script(zb, wd)

    assert result[0]["转化规模"] == 0
    assert result[0]["转化人数"] == 0
    assert result[0]["转化率"] == 0


def test_documented_script_reports_all_missing_join_columns():
    zb = mock_view_rows("cljd_zcl_zb_view")
    wd = mock_view_rows("cljd_zcl_wd_view").drop(columns=["platform_id", "channel"])

    with pytest.raises(ValueError, match="维度数据集 缺少必要字段: platform_id, channel"):
        execute_documented_script(zb, wd)


def test_documented_script_returns_empty_rows_when_both_inputs_are_empty():
    zb = pd.DataFrame(columns=[
        "metric_time", "attribution_plan_id", "attribution_strategy_id",
        "platform_id", "channel", "digo_metric_x", "digo_metric_x_trans_user_cnt",
    ])
    wd = pd.DataFrame(columns=[
        "metric_time", "attribution_plan_id", "attribution_strategy_id",
        "platform_id", "channel", "metric_id", "metric_name",
    ])

    assert execute_documented_script(zb, wd) == []


def test_documented_script_returns_empty_rows_when_dimension_input_has_no_rows():
    zb = pd.DataFrame(columns=[
        "metric_time", "attribution_plan_id", "attribution_strategy_id",
        "platform_id", "channel", "digo_metric_x", "digo_metric_x_trans_user_cnt",
    ])
    wd = pd.DataFrame(columns=[
        "metric_time", "attribution_plan_id", "attribution_strategy_id",
        "platform_id", "channel", "metric_id", "metric_name",
    ])

    assert execute_documented_script(zb, wd) == []


def test_documented_script_returns_empty_rows_when_one_filtered_input_is_empty():
    dimensions = {
        "metric_time": "2026-09-01",
        "attribution_plan_id": "PLAN-001",
        "attribution_strategy_id": "STR-001",
        "platform_id": "SUB-001",
        "channel": "APP",
    }
    wd = pd.DataFrame([{**dimensions, "metric_id": "metric_x", "metric_name": "指标X"}])
    zb = pd.DataFrame(columns=[
        "metric_time", "attribution_plan_id", "attribution_strategy_id",
        "platform_id", "channel", "digo_metric_x", "digo_metric_x_trans_user_cnt",
    ])

    assert execute_documented_script(zb, wd) == []
