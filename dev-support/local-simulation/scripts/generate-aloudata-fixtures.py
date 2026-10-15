#!/usr/bin/env python3
"""生成 mateclaw-dataagent 的 Aloudata 本地 mock fixture。

只服务本地开发和自动化验证，不连接任何外部服务。产物写入：
    mateclaw-dataagent/src/main/resources/mock/aloudata/

数据来源：仓库根目录的 generated_cljd_zcl.sql（策略解读 PG 造数脚本）。
该脚本的两张表被映射为两个 Aloudata 指标视图：

  cljd_zcl_zb（策略解读-子策略-指标） -> cljd_zcl_zb_view  5 维度 / 103 指标 / 1000 行
  cljd_zcl_wd（策略解读-子策略-维度） -> cljd_zcl_wd_view 13 维度 /   6 指标 / 1012 行

本地压测/脚本联调用扩展数据：宽表含 100+ 个 digo_ 指标物理列和 1000 行；维度表保留
原始 18 行并新增与宽表业务键、metric_id 对应的数据。

报文结构严格对齐真实 Aloudata 响应（实测自 demo 租户）：
  - 顶层固定为 data / success / code / errorMsg / detailErrorMsg / traceId，code 是字符串 "200"
  - 视图详情的 metrics/dimensions 是**字符串数组**，展示名单独放在 displayNameMap
  - 结果查询是列式 data.table.columns = {列名: [{value, flag, count}]}（与官方「指标视图结果查询」一致，
    另含 data.metas / data.queryId / data.warning）

用法：
    python3 generate-aloudata-fixtures.py [--out <dir>]
"""

from __future__ import annotations

import argparse
import json
import re
from datetime import date, timedelta
from pathlib import Path

# ---------------------------------------------------------------------------
# 目录与视图
# ---------------------------------------------------------------------------

CATEGORY_ID = "CAT_CLJD"
CATEGORY_NAME = "策略解读"
METRIC_CATEGORY_ROOT_ID = "metric-strategy"
METRIC_CATEGORY_ID = "metric-sub-strategy"
DIMENSION_CATEGORY_ROOT_ID = "dim-strategy"
DIMENSION_CATEGORY_ID = "dim-sub-strategy"

ZB_VIEW = "cljd_zcl_zb_view"
WD_VIEW = "cljd_zcl_wd_view"
VIEW_DISPLAY = {ZB_VIEW: "策略解读-子策略-指标", WD_VIEW: "策略解读-子策略-维度"}
VIEW_DESCRIPTION = {
    ZB_VIEW: "策略解读：子策略粒度的策略归因指标宽表（计划/策略/子策略/渠道）",
    WD_VIEW: "策略解读：子策略维度的下发/触达指标",
}
VIEW_ID = {ZB_VIEW: 9001, WD_VIEW: 9002}
SOURCE_TABLE = {ZB_VIEW: "cljd_zcl_zb", WD_VIEW: "cljd_zcl_wd"}

# 视图时间约束：与真实平台同为表达式（本 mock 不对它做实际过滤，仅保证转发结构真实）。
TIME_CONSTRAINT = (
    '(((dateTrunc([\'metric_time\'], "DAY")) >= '
    '(DATEADD(DateTrunc(TODAY(), "DAY"), -(364), "DAY")))) AND '
    '(((dateTrunc([\'metric_time\'], "DAY")) <= '
    '(DATEADD(DateTrunc(TODAY(), "DAY"), 0, "DAY"))))'
)

# ---------------------------------------------------------------------------
# 字段目录：metricName / 展示名 / 业务口径 / 单位
# ---------------------------------------------------------------------------

METRICS = [
    ("digo_trd_fund_amt_inout_cy_jjgr", "经纪个人客户场内公募非货当年净买入",
     "经纪个人客户场内公募非货当年净买入-策略归因", "万元"),
    ("digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt", "经纪个人客户场内公募非货当年净买入-转化客户数",
     "经纪个人客户场内公募非货当年净买入-转化客户数-策略归因", "人"),
    ("digo_trd_fund_amt_inout_cy_jjgr_pb", "经纪个人场内公募非货破冰客户当年净买入",
     "经纪个人场内公募非货破冰客户当年净买入-策略归因", "万元"),
    ("digo_pbcnt_kgdb_a566_fh_jjgr", "经纪个人场内公募非货破冰客户数",
     "经纪个人场内公募非货破冰客户数-策略归因", "人"),
    ("digo_fund_trd_amt_a566_fh_kgdb_jj0", "经纪个人场内公募非货交易量",
     "经纪个人场内公募非货交易量-策略归因", "万元"),
    ("digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt", "经纪个人场内公募非货交易量-转化客户数",
     "经纪个人场内公募非货交易量-转化客户数-策略归因", "人"),
    ("digo_pub_fh_kgdb_trdamt_ppcadd_jjgr", "经纪个人场内公募非货加仓交易量",
     "经纪个人场内公募非货加仓交易量-策略归因", "万元"),
    ("digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt", "经纪个人场内公募非货加仓交易量-转化客户数",
     "经纪个人场内公募非货加仓交易量-转化客户数-策略归因", "人"),
    ("digo_cust_asset_in", "入金客户数", "入金客户数_策略归因", "人"),
    ("digo_new_cust_asset_in", "新客入金数", "新客入金数_策略归因", "人"),
    ("digo_cnt_cust_code_new_brok", "新增客户数", "新增客户数_策略归因", "人"),
    ("digo_cust_valid_new_yxh", "新增有效户数", "新增有效户数_策略归因", "人"),
    ("digo_cust_asset_in10000", "万元入金户数", "万元入金户数_策略归因", "户"),
    ("digo_strategy_cnt", "下挂策略数", "下挂策略数", "个"),
    ("digo_strategy_cnt_distr_1", "下发策略数", "下发策略数", "个"),
    ("digo_distr_count_1", "下发次数", "下发次数", "次"),
    ("digo_distr_user_cnt_a", "下发人数", "下发人数", "人"),
    ("digo_touch_cnt_1", "触达次数", "触达次数", "次"),
    ("digo_touch_user_cnt_1", "触达人数", "触达人数", "人"),
]

# 增加 45 个模拟指标 ID，每个 ID 在宽表生成一对规模/转化人数列，
# 与原有 13 列合计 103 个 digo_ 物理指标列。
SYNTHETIC_METRIC_COUNT = 45
SYNTHETIC_METRICS = []
for index in range(1, SYNTHETIC_METRIC_COUNT + 1):
    metric_id = f"strategy_conversion_{index:03d}"
    SYNTHETIC_METRICS.extend([
        (f"digo_{metric_id}", f"转化规模模拟指标{index:03d}",
         f"转化规模模拟指标{index:03d}-策略归因", "次"),
        (f"digo_{metric_id}_trans_user_cnt", f"转化人数模拟指标{index:03d}",
         f"转化人数模拟指标{index:03d}-策略归因", "人"),
    ])
METRICS.extend(SYNTHETIC_METRICS)

# dimName / 展示名 / 描述 / originDataType
DIMENSIONS = [
    ("metric_time", "指标日期", "指标日期", "TIMESTAMP"),
    ("attribution_plan_id", "计划id", "计划id", "VARCHAR"),
    ("attribution_plan_name", "计划名称", "计划名称", "VARCHAR"),
    ("attribution_plan_um_account", "计划负责人", "计划负责人", "VARCHAR"),
    ("attribution_strategy_id", "策略id", "策略id", "VARCHAR"),
    ("attribution_strategy_name", "策略名称", "策略名称", "VARCHAR"),
    ("attribution_over_by", "策略负责人", "策略负责人", "VARCHAR"),
    ("platform_id", "子策略id", "子策略id", "VARCHAR"),
    ("platform_name", "子策略名称", "子策略名称", "VARCHAR"),
    ("create_by", "子策略创建人", "子策略创建人", "VARCHAR"),
    ("channel", "触达渠道", "触达渠道", "VARCHAR"),
    ("metric_id", "转化指标id",
     "转化指标id；对应指标表中 digo_<metric_id>_trans_user_cnt 的中间部分", "VARCHAR"),
    ("metric_name", "转化指标名称", "转化指标名称", "VARCHAR"),
]

ZB_DIMS = ["metric_time", "attribution_plan_id", "attribution_strategy_id", "platform_id", "channel"]
WD_DIMS = [d[0] for d in DIMENSIONS]
ZB_BASE_METRICS = [m[0] for m in METRICS[:13]]
WD_METRICS = [m[0] for m in METRICS[13:19]]
SYNTHETIC_ZB_METRICS = [m[0] for m in SYNTHETIC_METRICS]
ZB_METRICS = ZB_BASE_METRICS + SYNTHETIC_ZB_METRICS

# ---------------------------------------------------------------------------
# 数据行（generated_cljd_zcl.sql 原文）
# ---------------------------------------------------------------------------

# cljd_zcl_zb：保留 6 条 SQL 基础样本，后续扩展至 1000 行。
ZB_BASE_ROWS = [
    ["2026-09-01", "PLAN-001", "STR-001", "SUB-001", "APP",
     1250000.00, 128, 420000.00, 96, 860000.00, 74, 210000.00, 39, 186, 72, 154, 91, 43],
    ["2026-09-01", "PLAN-001", "STR-001", "SUB-001", "SMS",
     980000.00, 101, 315000.00, 81, 640000.00, 57, 175000.00, 31, 143, 55, 119, 68, 32],
    ["2026-09-01", "PLAN-001", "STR-002", "SUB-002", "APP",
     1480000.00, 152, 510000.00, 112, 930000.00, 88, 265000.00, 46, 211, 84, 179, 108, 51],
    ["2026-09-02", "PLAN-001", "STR-001", "SUB-001", "APP",
     1320000.00, 136, 450000.00, 103, 905000.00, 79, 230000.00, 42, 194, 78, 163, 97, 46],
    ["2026-09-02", "PLAN-001", "STR-001", "SUB-001", "SMS",
     1040000.00, 109, 338000.00, 87, 690000.00, 62, 188000.00, 34, 151, 60, 126, 73, 35],
    ["2026-09-02", "PLAN-001", "STR-002", "SUB-002", "APP",
     1550000.00, 161, 536000.00, 121, 982000.00, 94, 279000.00, 49, 223, 90, 190, 115, 55],
]

# cljd_zcl_wd：6 组 × 3 个 metric_id = 18 条 SQL 基础样本。
# (metric_time, strategy_id, platform_id, channel, 6 指标)
WD_GROUPS = [
    ("2026-09-01", "STR-001", "SUB-001", "APP", [2, 2, 180, 150, 120, 96]),
    ("2026-09-01", "STR-001", "SUB-001", "SMS", [2, 2, 130, 105, 88, 71]),
    ("2026-09-01", "STR-002", "SUB-002", "APP", [3, 3, 180, 150, 120, 96]),
    ("2026-09-02", "STR-001", "SUB-001", "APP", [2, 2, 180, 150, 120, 96]),
    ("2026-09-02", "STR-001", "SUB-001", "SMS", [2, 2, 130, 105, 88, 71]),
    ("2026-09-02", "STR-002", "SUB-002", "APP", [3, 3, 180, 150, 120, 96]),
]

STRATEGY_NAME = {"STR-001": "高净值客户触达策略", "STR-002": "新客增长策略"}
STRATEGY_OWNER = {"STR-001": "strategy_owner_a", "STR-002": "strategy_owner_b"}
PLATFORM_NAME = {"SUB-001": "场内公募非货子策略", "SUB-002": "新客入金子策略"}

# metric_id / metric_name 三元组（generated_cljd_zcl.sql 的 CROSS JOIN 取值）
BASE_CONVERSION_METRICS = [
    ("trd_fund_amt_inout_cy_jjgr", "经纪个人客户场内公募非货当年净买入"),
    ("fund_trd_amt_a566_fh_kgdb_jj0", "经纪个人场内公募非货交易量"),
    ("pub_fh_kgdb_trdamt_ppcadd_jjgr", "经纪个人场内公募非货加仓交易量"),
]
CONVERSION_METRICS = BASE_CONVERSION_METRICS + list(
    (f"strategy_conversion_{index:03d}", f"转化规模模拟指标{index:03d}")
    for index in range(1, SYNTHETIC_METRIC_COUNT + 1)
)

PLAN_NAME = "策略解读示例计划"
PLAN_OWNER = "plan_owner"
CREATOR = "data_owner"


def synthetic_zb_rows() -> list[list]:
    """生成宽表补充行；每行有唯一计划 ID，确保 table_wd 可按业务键精确关联。"""
    rows = []
    start_day = date(2026, 9, 1)

    def synthetic_metric_values(row_index: int) -> list[int]:
        values = []
        for metric_index in range(SYNTHETIC_METRIC_COUNT):
            # 新增规模值稳定落在 80-120（约 100），人数值落在 20-100。
            values.extend([
                80 + ((row_index * 7 + metric_index * 13) % 41),
                20 + ((row_index * 11 + metric_index * 5) % 81),
            ])
        return values

    for row_index, seed in enumerate(ZB_BASE_ROWS):
        rows.append([*seed[:5], *seed[5:], *synthetic_metric_values(row_index)])

    for row_index in range(len(rows), 1000):
        seed = ZB_BASE_ROWS[row_index % len(ZB_BASE_ROWS)]
        metric_values = list(seed[5:])
        metric_values.extend(synthetic_metric_values(row_index))
        rows.append([
            # 生成数据从 9 月 4 日起，避免污染原始 9 月 1-3 日筛选回归样例。
            (start_day + timedelta(days=3 + row_index % 39)).isoformat(),
            f"PLAN-{row_index + 1:04d}",
            seed[2],
            seed[3],
            seed[4],
            *metric_values,
        ])
    return rows


ZB_ROWS = synthetic_zb_rows()


def wd_rows() -> list[list]:
    """保留 18 条原始维度样本，并为每条新增宽表业务记录生成匹配的指标维值。"""
    rows = []
    for metric_time, strategy_id, platform_id, channel, values in WD_GROUPS:
        for metric_id, metric_name in BASE_CONVERSION_METRICS:
            rows.append([
                metric_time,
                "PLAN-001",
                PLAN_NAME,
                PLAN_OWNER,
                strategy_id,
                STRATEGY_NAME[strategy_id],
                STRATEGY_OWNER[strategy_id],
                platform_id,
                PLATFORM_NAME[platform_id],
                CREATOR,
                channel,
                metric_id,
                metric_name,
                *values,
            ])
    base_values = WD_GROUPS[0][4]
    for row_index, zb_row in enumerate(ZB_ROWS[len(ZB_BASE_ROWS):], start=len(ZB_BASE_ROWS)):
        metric_id, metric_name = CONVERSION_METRICS[
            (row_index - len(ZB_BASE_ROWS)) % len(CONVERSION_METRICS)
        ]
        strategy_id = zb_row[2]
        platform_id = zb_row[3]
        values = [
            max(1, value + ((row_index + offset * 3) % 29) - 14)
            for offset, value in enumerate(base_values)
        ]
        rows.append([
            zb_row[0],
            zb_row[1],
            PLAN_NAME,
            PLAN_OWNER,
            strategy_id,
            STRATEGY_NAME[strategy_id],
            STRATEGY_OWNER[strategy_id],
            platform_id,
            PLATFORM_NAME[platform_id],
            CREATOR,
            zb_row[4],
            metric_id,
            metric_name,
            *values,
        ])
    return rows


VIEW_COLUMNS = {
    ZB_VIEW: ZB_DIMS + ZB_METRICS,
    WD_VIEW: WD_DIMS + WD_METRICS,
}
VIEW_ROWS = {ZB_VIEW: ZB_ROWS, WD_VIEW: wd_rows()}


# ---------------------------------------------------------------------------
# 报文构造
# ---------------------------------------------------------------------------

def envelope(data, trace_id: str) -> dict:
    """真实 Aloudata 响应包络（code 为字符串）。"""
    return {
        "data": data,
        "success": True,
        "code": "200",
        "errorMsg": None,
        "detailErrorMsg": None,
        "traceId": trace_id,
    }


def guid(view: str) -> dict:
    return {
        "catalog": "mock_tenant_aloudata_datasource",
        "schema": "default",
        "table": SOURCE_TABLE[view],
        "validTableGuid": True,
        "validSchemaGuid": True,
        "validCatalogGuid": True,
    }


def display_name_map(metrics: list[str], dimensions: list[str]) -> dict:
    names = {m[0]: m[1] for m in METRICS}
    names.update({d[0]: d[1] for d in DIMENSIONS})
    return {key: names[key] for key in metrics + dimensions}


def view_definition(view: str) -> dict:
    metrics = ZB_METRICS if view == ZB_VIEW else WD_METRICS
    dimensions = ZB_DIMS if view == ZB_VIEW else WD_DIMS
    return {
        "id": VIEW_ID[view],
        "guid": guid(view),
        "viewName": view,
        "displayName": VIEW_DISPLAY[view],
        "description": VIEW_DESCRIPTION[view],
        "categoryId": CATEGORY_ID,
        "metrics": metrics,
        "dimensions": dimensions,
        "timeConstraint": TIME_CONSTRAINT,
        "filters": [],
        "resultFilters": [],
        "orders": [],
        "createFrom": "WEBUI",
        "queryType": None,
        "sourceTableQuery": None,
        "metricDefinitions": None,
        "displayNameMap": display_name_map(metrics, dimensions),
        "dimensionToMetricRelations": {},
        "dynamicConditions": {},
        "fullDimensionConfig": None,
        "basicAttributes": {
            "owner": "__OWNER__",
            "viewName": view,
            "viewDisplayName": VIEW_DISPLAY[view],
            "createTime": 1758100000000,
            "modifyTime": 1758100000000,
        },
        "businessAttributes": {"viewDescription": VIEW_DESCRIPTION[view], "viewCategoryId": CATEGORY_ID},
        "technicalAttributes": {},
        "managementAttributes": {},
    }


def list_item(view: str) -> dict:
    """analysisview/list 的条目：在视图详情基础上带 basicAttributes（owner 用于「只看我的」）。"""
    definition = view_definition(view)
    return definition


def tree_payload() -> dict:
    """data.analysisViewRoots；视图条目形状实测为 {id, viewName, description, displayName}。"""
    entries = [
        {
            "id": VIEW_ID[view],
            "viewName": view,
            "description": VIEW_DESCRIPTION[view],
            "displayName": VIEW_DISPLAY[view],
        }
        for view in (ZB_VIEW, WD_VIEW)
    ]
    return {
        "analysisViewRoots": [
            {
                "categoryId": CATEGORY_ID,
                "categoryName": CATEGORY_NAME,
                "analysisViewList": entries,
                "subCategory": [],
            }
        ]
    }


def list_payload() -> dict:
    items = [list_item(view) for view in (ZB_VIEW, WD_VIEW)]
    return {
        "pageNumber": 1,
        "pageSize": 200,
        "totalPageSize": len(items),
        "data": items,
    }


def metric_batch_detail_payload() -> dict:
    """全量指标目录；由 fixture 层按请求的 metricNames 过滤。"""
    items = []
    for name, display, caliber, unit in METRICS:
        items.append({
            "basicAttributes": {"owner": "__OWNER__"},
            "businessAttributes": None,
            "technicalAttributes": None,
            "managementAttributes": None,
            "code": name,
            "version": 1,
            "metricName": name,
            "metricCode": None,
            "metricDisplayName": display,
            "businessCaliber": caliber,
            "owner": "__OWNER__",
            "businessOwner": "__OWNER__",
            "type": "ATOMIC",
            "caliber": {
                "datasetName": "cljd_zcl",
                "expr": f"sum(['cljd_zcl'/'{name}'])",
                "formula": None,
                "filters": [],
                "metricTime": "metric_time",
                "metricTimeDataType": "DATE",
                "enableNonAdditiveDimensions": False,
                "nonAdditiveDimensions": [],
                "relDefinitions": None,
                "fingerPrint": None,
            },
            "unit": unit,
            "formatConfig": None,
            "cnUnit": unit,
            "metricCategoryId": METRIC_CATEGORY_ID,
            "metricCategoryName": "子策略",
            "metricViewCount": 1,
            "viewDetailCount": 0,
            "publishStatus": "PUBLISHED",
            "status": "ONLINE",
            "displayStatus": "PUBLISHED",
            "isNewDimensionDefaultEnable": False,
            "availableDimensions": list(ZB_DIMS if name in ZB_METRICS else WD_DIMS),
            "disableDimensions": [],
            "factorDimensions": [],
            "restrictedDimensions": [],
            "gmtCreate": 1758100000000,
            "gmtUpdate": 1758100000000,
            "timeGranularity": None,
            "hasDateLimit": False,
            "hasDerivationMethod": False,
            "metricTimeDataType": "DATE",
            "availableDimensionTagRoute": None,
            "dynamicParamAtomicSetting": None,
            "dynamicParamSetting": None,
            "availableDimensionRelations": None,
            "additive": "ADDITIVE",
            "bizProperties": None,
        })
    return items


def dimension_list_payload() -> dict:
    """全量维度目录；由 fixture 层按视图用到的 dimName 过滤。"""
    items = []
    for name, display, description, origin_type in DIMENSIONS:
        items.append({
            "basicAttributes": None,
            "businessAttributes": None,
            "technicalAttributes": None,
            "managementAttributes": None,
            "name": f"dm_{name}",
            "dimName": name,
            "dimCode": name,
            "dimDisplayName": display,
            "dimCategoryId": DIMENSION_CATEGORY_ID,
            "dimDescription": description,
            "datasetName": "cljd_zcl",
            "originDataType": origin_type,
            "config": {"type": "COLUMN_BIND", "value": name, "relDefinitions": None},
            "displayStatus": "PUBLISHED",
            "status": "ONLINE",
            "publishStatus": "PUBLISHED",
            "businessType": "STANDARD",
            "tags": None,
        })
    return {
        "total": len(items),
        "pageNumber": 1,
        "pageSize": 1000,
        "hasNext": False,
        "data": items,
    }


def category_list_payload() -> dict:
    """指标/维度选择弹窗类目：同为「策略解读 → 子策略」，ID 与字段目录一致。"""
    return {
        "data": [
            {"id": METRIC_CATEGORY_ROOT_ID, "name": CATEGORY_NAME, "parentId": None,
             "categoryType": "CATEGORY_METRIC"},
            {"id": METRIC_CATEGORY_ID, "name": "子策略", "parentId": METRIC_CATEGORY_ROOT_ID,
             "categoryType": "CATEGORY_METRIC"},
            {"id": DIMENSION_CATEGORY_ROOT_ID, "name": CATEGORY_NAME, "parentId": None,
             "categoryType": "CATEGORY_DIMENSION"},
            {"id": DIMENSION_CATEGORY_ID, "name": "子策略", "parentId": DIMENSION_CATEGORY_ROOT_ID,
             "categoryType": "CATEGORY_DIMENSION"},
        ],
        "success": True,
        "code": "200",
        "errorMsg": None,
        "detailErrorMsg": None,
        "traceId": "mock-trace-category-list",
    }


def cell(value) -> dict:
    return {"value": value, "flag": 0, "count": 1}


# 指标视图结果查询（analysisView/query）实测 DECIMAL 类型的指标（其余 measures 为 BIGINT）。
DECIMAL_METRICS = {
    "digo_trd_fund_amt_inout_cy_jjgr",   # 经纪个人客户场内公募非货当年净买入
    "digo_trd_fund_amt_inout_cy_jjgr_pb",  # 经纪个人场内公募非货破冰客户当年净买入
    "digo_fund_trd_amt_a566_fh_kgdb_jj0",  # 经纪个人场内公募非货交易量
    "digo_pub_fh_kgdb_trdamt_ppcadd_jjgr",  # 经纪个人场内公募非货加仓交易量
}


def query_data_payload(view: str) -> dict:
    """指标视图结果查询（analysisView/query）：列式 data.table.columns + data.metas + queryId/warning。

    严格对齐官方文档（API-指标视图结果查询）：
      - 真实响应包络为 {data:{queryId, warning, table:{columns:{列名:[{value,flag,count}]}}, metas:[...]}}
      - metas[] 含 name / dataType(null) / dataTypeName / displaySize / schemaName / scale /
        precision / tableName
    """
    columns = VIEW_COLUMNS[view]
    rows = VIEW_ROWS[view]
    by_name = {m[0]: m[1] for m in METRICS}
    by_dim = {d[0]: d[3] for d in DIMENSIONS}
    column_map = {}
    for index, column in enumerate(columns):
        column_map[column] = [cell(row[index]) for row in rows]
    metas = []
    for column in columns:
        if column in by_name:
            metas.append({
                "name": column,
                "dataType": None,
                "dataTypeName": "DECIMAL" if column in DECIMAL_METRICS else "BIGINT",
                "displaySize": None,
                "schemaName": "default",
                "scale": None,
                "precision": None,
                "tableName": SOURCE_TABLE[view],
            })
        else:
            metas.append({
                "name": column,
                "dataType": None,
                "dataTypeName": by_dim.get(column, "VARCHAR"),
                "displaySize": None,
                "schemaName": "default",
                "scale": None,
                "precision": None,
                "tableName": SOURCE_TABLE[view],
            })
    return {
        "table": {"columns": column_map, "total": len(rows)},
        "metas": metas,
        "total": len(rows),
        "queryId": f"mock-query-{view}",
        "warning": None,
    }


def metrics_query_payload() -> dict:
    """指标数据查询（metrics/query）：与 analysisView/query 同构，真实也是 data.table.columns + metas。"""
    body = query_data_payload(ZB_VIEW)
    return {
        "table": body["table"],
        "metas": body["metas"],
        "total": body["total"],
    }


def write(path: Path, payload) -> None:
    content = json.dumps(payload, ensure_ascii=False, indent=2)
    if path.name in {"analysis_view_query_data.json", "metrics_query.json"}:
        content = compact_column_cells(content)
    path.write_text(content + "\n", encoding="utf-8")
    print(f"  wrote {path.name}")


def compact_column_cells(content: str) -> str:
    """压缩列式结果中重复的 cell JSON，避免 1000 行夹具膨胀成百万行 diff。"""
    content = re.sub(
        r'\{\n([ ]+)"value": ([^\n]*),\n\1"flag": 0,\n\1"count": 1\n[ ]+\}',
        r'{"value": \2, "flag": 0, "count": 1}',
        content,
    )

    def compact_array(match: re.Match) -> str:
        indent, name, body = match.groups()
        cells = re.findall(r'\{"value": .*?, "flag": 0, "count": 1\}', body)
        item_indent = indent + "  "
        lines = [
            item_indent + ", ".join(cells[index:index + 12])
            for index in range(0, len(cells), 12)
        ]
        for index in range(len(lines) - 1):
            lines[index] += ","
        return f'{indent}"{name}": [\n' + "\n".join(lines) + f'\n{indent}]'

    return re.sub(
        r'(?m)^([ ]*)"([^"\n]+)": \[\n((?:[ ]+\{"value": [^\n]+\},?\n)+)[ ]*\]',
        compact_array,
        content,
    )


def main() -> None:
    default_out = Path(__file__).resolve().parents[3] / "mateclaw-dataagent" / "src" / "main" / "resources" / "mock" / "aloudata"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=default_out, help="输出目录")
    args = parser.parse_args()
    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    print(f"输出目录：{out}")

    write(out / "analysis_view_tree.json", envelope(tree_payload(), "mock-trace-tree"))
    write(out / "analysis_view_list.json", envelope(list_payload(), "mock-trace-list"))
    write(out / "analysis_view_query_by_name.json", {
        view: envelope(view_definition(view), f"mock-trace-detail-{view}")
        for view in (ZB_VIEW, WD_VIEW)
    })
    write(out / "metric_batch_detail.json", envelope(metric_batch_detail_payload(), "mock-trace-batch-detail"))
    write(out / "category_list.json", category_list_payload())
    write(out / "dimension_list.json", envelope(dimension_list_payload(), "mock-trace-dimension-list"))
    write(out / "analysis_view_query_data.json", {
        view: envelope(query_data_payload(view), f"mock-trace-data-{view}")
        for view in (ZB_VIEW, WD_VIEW)
    })
    write(out / "metrics_query.json", envelope(metrics_query_payload(), "mock-trace-metrics-query"))

    print(f"完成：zb_view {len(ZB_ROWS)} 行 / {len(ZB_METRICS)} 个指标列 / "
          f"wd_view {len(VIEW_ROWS[WD_VIEW])} 行 / {len(CONVERSION_METRICS)} 个指标 ID")


if __name__ == "__main__":
    main()
