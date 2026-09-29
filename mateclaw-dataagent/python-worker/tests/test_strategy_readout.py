import pandas as pd
import importlib.util
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "examples" / "strategy_readout_transform.py"
MODULE_SPEC = importlib.util.spec_from_file_location("strategy_readout_transform", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(MODULE_SPEC)
MODULE_SPEC.loader.exec_module(MODULE)
transform_strategy_readout = MODULE.transform_strategy_readout


def test_unpivots_paired_metrics_and_joins_by_metric_time():
    wd = pd.DataFrame([
        {
            "metric_time": "2026-09-01",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "metric_id": "pub_fh_kgdb_trdamt_ppcadd_jjgr",
            "metric_name": "加仓交易量",
        }
    ])
    zb = pd.DataFrame([
        {
            "metric_time": "2026-09-01",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr": 210000,
            "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt": 39,
        }
    ])

    result = transform_strategy_readout(wd, zb)

    assert result[["转化指标id", "转化规模", "转化人数"]].to_dict("records") == [
        {"转化指标id": "pub_fh_kgdb_trdamt_ppcadd_jjgr", "转化规模": 210000, "转化人数": 39}
    ]
    assert result.loc[0, "转化率"] == 210000 / 39
    assert result.loc[0, "metric_name"] == "加仓交易量"


def test_missing_metric_pair_is_zero_and_dates_do_not_cross_join():
    wd = pd.DataFrame([
        {
            "metric_time": "2026-09-01",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "metric_id": "trd_fund_amt_inout_cy_jjgr",
        },
        {
            "metric_time": "2026-09-02",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "metric_id": "trd_fund_amt_inout_cy_jjgr",
        },
    ])
    zb = pd.DataFrame([
        {
            "metric_time": "2026-09-01",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "digo_trd_fund_amt_inout_cy_jjgr": 100,
        },
        {
            "metric_time": "2026-09-02",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt": 4,
        },
    ])

    result = transform_strategy_readout(wd, zb)

    assert len(result) == 2
    assert result[["metric_time", "转化规模", "转化人数", "转化率"]].to_dict("records") == [
        {"metric_time": "2026-09-01", "转化规模": 100, "转化人数": 0, "转化率": 0.0},
        {"metric_time": "2026-09-02", "转化规模": 0, "转化人数": 4, "转化率": 0.0},
    ]


def test_non_metric_columns_are_not_unpivoted():
    wd = pd.DataFrame([
        {
            "metric_time": "2026-09-01",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "metric_id": "cust_asset_in",
        }
    ])
    zb = pd.DataFrame([
        {
            "metric_time": "2026-09-01",
            "attribution_plan_id": "PLAN-001",
            "attribution_strategy_id": "STR-001",
            "platform_id": "SUB-001",
            "channel": "APP",
            "note": "dimension-like field",
            "digo_cust_asset_in": 186,
        }
    ])

    result = transform_strategy_readout(wd, zb)

    assert len(result) == 1
    assert result.loc[0, "转化指标id"] == "cust_asset_in"
    assert "note" not in result.columns
