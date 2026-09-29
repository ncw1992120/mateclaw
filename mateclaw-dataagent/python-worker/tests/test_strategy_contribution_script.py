from pathlib import Path
import importlib.util

import pandas as pd


SCRIPT_PATH = (
    Path(__file__).parents[3]
    / "docs"
    / "策略解读"
    / "策略贡献.py"
)
FIXTURE_GENERATOR_PATH = (
    Path(__file__).parents[3]
    / "dev-support"
    / "local-simulation"
    / "scripts"
    / "generate-aloudata-fixtures.py"
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
    spec = importlib.util.spec_from_file_location(
        "aloudata_fixtures",
        FIXTURE_GENERATOR_PATH,
    )
    fixtures = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixtures)
    wd = pd.DataFrame(
        fixtures.wd_rows(),
        columns=fixtures.WD_DIMS + fixtures.WD_METRICS,
    )
    zb = pd.DataFrame(
        fixtures.ZB_ROWS,
        columns=fixtures.ZB_DIMS + fixtures.ZB_METRICS,
    )

    result = execute_script(wd, zb)

    expected_names = [name for _, name in fixtures.CONVERSION_METRICS]
    assert len(result) == 1
    assert list(result[0]) == [
        field_name
        for name in expected_names
        for field_name in (name, f"{name}-转化人数")
    ]
    assert all(value > 0 for value in result[0].values())
