# ===== 造数示例与上游数据读取 =====
# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。

# 造数示例：默认注释；取消对应注释即可用上游「查看数据-查询」的最近结果进行本地调试。
# 造数优先：变量已在此处赋值时，下面会跳过对应的真实数据读取。
# import json
# import polars as pl
# strategy_contribution_wd = pl.DataFrame(json.loads("[{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"channel\":\"APP\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"channel\":\"APP\",\"metric_name\":\"经纪个人场内公募非货交易量\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"channel\":\"APP\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"channel\":\"SMS\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"channel\":\"SMS\",\"metric_name\":\"经纪个人场内公募非货交易量\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"channel\":\"SMS\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-002\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"channel\":\"APP\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-002\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"channel\":\"APP\",\"metric_name\":\"经纪个人场内公募非货交易量\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-002\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"channel\":\"APP\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\"}]"))
# strategy_contribution_zb = pl.DataFrame(json.loads("[{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"channel\":\"APP\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"1250000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"128\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"420000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"96\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"860000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"74\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"210000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"39\",\"digo_cust_asset_in\":\"186\",\"digo_new_cust_asset_in\":\"72\",\"digo_cnt_cust_code_new_brok\":\"154\",\"digo_cust_valid_new_yxh\":\"91\",\"digo_cust_asset_in10000\":\"43\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"channel\":\"SMS\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"980000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"101\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"315000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"81\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"640000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"57\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"175000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"31\",\"digo_cust_asset_in\":\"143\",\"digo_new_cust_asset_in\":\"55\",\"digo_cnt_cust_code_new_brok\":\"119\",\"digo_cust_valid_new_yxh\":\"68\",\"digo_cust_asset_in10000\":\"32\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-002\",\"platform_id\":\"SUB-002\",\"channel\":\"APP\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"1480000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"152\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"510000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"112\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"930000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"88\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"265000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"46\",\"digo_cust_asset_in\":\"211\",\"digo_new_cust_asset_in\":\"84\",\"digo_cnt_cust_code_new_brok\":\"179\",\"digo_cust_valid_new_yxh\":\"108\",\"digo_cust_asset_in10000\":\"51\"}]"))

if "strategy_contribution_wd" not in locals():
    strategy_contribution_wd = datasets.input(
        input_name="strategy_contribution_wd",
    ).to_polars()

if "strategy_contribution_zb" not in locals():
    strategy_contribution_zb = datasets.input(
        input_name="strategy_contribution_zb",
    ).to_polars()

# ===== 数据读取结束 =====

# ===== 自定义处理代码（可编辑） =====

import pandas as pd

# 指标宽表通过列名表达指标及其统计口径：
#   digo_<metric_id>                         -> 指标值
#   digo_<metric_id>_trans_user_cnt          -> 转化人数
# 下面两个常量集中描述这个命名约定，避免在解析逻辑中散落硬编码。
METRIC_PREFIX = "digo_"
USER_COUNT_SUFFIX = "_trans_user_cnt"
def to_pandas(frame):
    """把 Runner 可能返回的 Polars 或兼容对象统一转换成 Pandas DataFrame。"""
    # Polars DataFrame 有 to_pandas；部分轻量对象只暴露 to_dicts。
    # 已经是 Pandas（或提供 copy() 的兼容对象）时，复制一份以免后续修改输入原对象。
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()


def metric_id_and_kind(column_name, selected_metric_ids):
    """解析指标宽表列名，返回 (metric_id, 统计口径)，不匹配时返回 None。

    只接受维度输入中实际选中的 metric_id，因而宽表里其他指标列不会混入结果。
    """
    if not column_name.startswith(METRIC_PREFIX):
        return None

    metric_name = column_name[len(METRIC_PREFIX):]
    kind = "value"
    if metric_name.endswith(USER_COUNT_SUFFIX):
        metric_name = metric_name[:-len(USER_COUNT_SUFFIX)]
        kind = "users"

    if metric_name not in selected_metric_ids:
        return None
    return metric_name, kind


def transform_contribution(wd, zb):
    # 页面筛选后任一输入无数据时返回合法空表，不继续做列识别和 melt。
    if wd.empty or zb.empty:
        return []

    if "metric_id" not in wd.columns:
        raise ValueError("strategy_contribution_wd 缺少必要字段: metric_id")

    # 仅用两份输入都包含的普通字段关联数据；展示名和指标值列不作为维度关联键。
    join_keys = [
        column for column in wd.columns
        if column in zb.columns
        and column not in ("metric_id", "metric_name")
        and not column.startswith(METRIC_PREFIX)
    ]
    if not join_keys:
        raise ValueError("两个上游数据集没有可用于关联的共有维度字段")

    wd = wd.copy()
    wd["metric_id"] = wd["metric_id"].astype("string")
    selected_metric_ids = set(wd["metric_id"].dropna().astype(str))
    column_kinds = {
        column: metric_id_and_kind(column, selected_metric_ids)
        for column in zb.columns
    }
    metric_columns = [column for column, parsed in column_kinds.items() if parsed]
    if not metric_columns:
        raise ValueError("strategy_contribution_zb 中没有找到与维度数据集 metric_id/metric_name 对应的 digo_ 指标列")

    # 宽转长后，按共有维度与指标 ID 汇总，避免不同日期/业务对象串数。
    long = zb[join_keys + metric_columns].melt(
        id_vars=join_keys,
        var_name="metric_column",
        value_name="metric_value",
    )
    parsed_columns = pd.DataFrame(
        long["metric_column"].map(column_kinds).tolist(),
        columns=["metric_id", "metric_kind"],
        index=long.index,
    )
    long[["metric_id", "metric_kind"]] = parsed_columns
    long["metric_value"] = pd.to_numeric(long["metric_value"], errors="coerce").fillna(0)
    wide = (
        long.groupby(join_keys + ["metric_id", "metric_kind"], dropna=False)["metric_value"]
        .sum().unstack("metric_kind", fill_value=0).reset_index()
    )
    for column in ("value", "users"):
        if column not in wide.columns:
            wide[column] = 0

    left_columns = join_keys + ["metric_id"]
    if "metric_name" in wd.columns:
        left_columns.append("metric_name")
    left = wd[left_columns].drop_duplicates().copy()
    right = wide.copy()
    for key in join_keys + ["metric_id"]:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")
    joined = left.merge(
        right[join_keys + ["metric_id", "value", "users"]],
        on=join_keys + ["metric_id"],
        how="left",
        validate="many_to_one",
    )
    joined[["value", "users"]] = joined[["value", "users"]].apply(
        pd.to_numeric, errors="coerce"
    ).fillna(0)

    metric_names = (
        wd[["metric_id", "metric_name"]].dropna(subset=["metric_id"])
        if "metric_name" in wd.columns
        else wd[["metric_id"]].assign(metric_name=wd["metric_id"])
    )
    metric_names["metric_id"] = metric_names["metric_id"].astype("string")
    metric_names = metric_names.drop_duplicates("metric_id").set_index("metric_id")["metric_name"]
    totals = joined.groupby("metric_id", dropna=False)[["value", "users"]].sum()
    result_row = {}
    used_labels = set()
    for metric_id in wd["metric_id"].dropna().drop_duplicates().astype(str):
        if metric_id not in totals.index:
            continue
        values = totals.loc[metric_id]
        label = metric_names.get(metric_id)
        label = str(label) if pd.notna(label) and str(label).strip() else metric_id
        if label in used_labels:
            label = f"{label} ({metric_id})"
        used_labels.add(label)
        result_row[label] = values["value"].item() if hasattr(values["value"], "item") else values["value"]
        result_row[f"{label}-转化人数"] = values["users"].item() if hasattr(values["users"], "item") else values["users"]
    return [result_row] if result_row else []


wd, zb = to_pandas(strategy_contribution_wd), to_pandas(strategy_contribution_zb)
# Runner 脚本的结果约定为 records 列表；无数据时直接返回 []，避免生成 [{}]。
result = transform_contribution(wd, zb)

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}
