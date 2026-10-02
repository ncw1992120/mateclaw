import json
from pathlib import Path

import pandas as pd


SCRIPT_PATH = (
    Path(__file__).parents[3]
    / "docs"
    / "策略解读"
    / "策略贡献.py"
)
MOCK_QUERY_DATA_PATH = (
    Path(__file__).parents[3]
    / "mateclaw-dataagent"
    / "src"
    / "main"
    / "resources"
    / "mock"
    / "aloudata"
    / "analysis_view_query_data.json"
)


class FakePolarsFrame:
    def __init__(self, frame):
        self.frame = frame

    def to_polars(self):
        return self.frame


class FakeDatasets:
    def __init__(self, frames):
        self.frames = frames

    def input(self, input_name):
        return FakePolarsFrame(self.frames[input_name])


def execute_script(wd, zb):
    namespace = {
        "datasets": FakeDatasets(
            {
                "strategy_contribution_wd": wd,
                "strategy_contribution_zb": zb,
            }
        )
    }
    source = SCRIPT_PATH.read_text(encoding="utf-8")
    exec(compile(source, str(SCRIPT_PATH), "exec"), namespace)
    return namespace["result"]


def mock_view_rows(view_name):
    payload = json.loads(MOCK_QUERY_DATA_PATH.read_text(encoding="utf-8"))[view_name]
    columns = payload["data"]["table"]["columns"]
    row_count = payload["data"]["table"]["total"]
    return pd.DataFrame(
        {
            name: [cell["value"] for cell in cells]
            for name, cells in columns.items()
        },
        index=range(row_count),
    )


def test_uses_dataset_metric_ids_names_and_shared_dimensions():
    wd = pd.DataFrame(
        [
            {
                "metric_time": "2026-09-01",
                "campaign_code": "A",
                "metric_id": "conversion_a",
                "metric_name": "新客转化",
            },
            {
                "metric_time": "2026-09-01",
                "campaign_code": "A",
                "metric_id": "conversion_b",
                "metric_name": "复购转化",
            },
        ]
    )
    zb = pd.DataFrame(
        [
            {
                "metric_time": "2026-09-01",
                "campaign_code": "A",
                "digo_conversion_a": 120,
                "digo_conversion_a_trans_user_cnt": 12,
                "digo_conversion_b": 80,
                "digo_conversion_b_trans_user_cnt": 8,
                "digo_unselected_metric": 999,
            }
        ]
    )

    result = execute_script(wd, zb)

    assert result == [
        {
            "新客转化": 120,
            "新客转化-转化人数": 12,
            "复购转化": 80,
            "复购转化-转化人数": 8,
        }
    ]


def test_shared_dimensions_prevent_cross_period_aggregation():
    wd = pd.DataFrame(
        [
            {
                "metric_time": "2026-09-01",
                "shop_code": "S1",
                "metric_id": "conversion_a",
                "metric_name": "新客转化",
            }
        ]
    )
    zb = pd.DataFrame(
        [
            {
                "metric_time": "2026-09-01",
                "shop_code": "S1",
                "digo_conversion_a": 120,
            },
            {
                "metric_time": "2026-09-02",
                "shop_code": "S1",
                "digo_conversion_a": 900,
            },
            {
                "metric_time": "2026-09-01",
                "shop_code": "S2",
                "digo_conversion_a": 700,
            },
        ]
    )

    result = execute_script(wd, zb)

    assert result == [{"新客转化": 120, "新客转化-转化人数": 0}]


def test_matches_strategy_readout_fixture_schema_and_display_names():
    wd = mock_view_rows("cljd_zcl_wd_view")
    zb = mock_view_rows("cljd_zcl_zb_view")

    result = execute_script(wd, zb)

    assert len(result) == 1
    assert result[0] == {
        "经纪个人客户场内公募非货当年净买入": 7_620_000,
        "经纪个人客户场内公募非货当年净买入-转化人数": 787,
        "经纪个人场内公募非货交易量": 5_007_000,
        "经纪个人场内公募非货交易量-转化人数": 454,
        "经纪个人场内公募非货加仓交易量": 1_347_000,
        "经纪个人场内公募非货加仓交易量-转化人数": 241,
    }
