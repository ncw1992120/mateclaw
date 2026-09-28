# ===== 造数示例与上游数据读取 =====
# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。

# 造数示例：默认注释；取消对应注释即可用上游「查看数据-查询」的最近结果进行本地调试。
# 造数优先：变量已在此处赋值时，下面会跳过对应的真实数据读取。
# import json
# import polars as pl
# strategy_contribution_wd：尚未获取真实查询结果行，不生成 schema 造数；请检查上游数据集查询。
# strategy_contribution_zb：尚未获取真实查询结果行，不生成 schema 造数；请检查上游数据集查询。

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
JOIN_KEYS = ["attribution_plan_id", "attribution_strategy_id", "platform_id", "channel"]
METRIC_ID_BY_NAME = {"经纪个人客户场内公募非货当年净买入": "trd_fund_amt_inout_cy_jjgr", "经纪个人场内公募非货交易量": "fund_trd_amt_a566_fh_kgdb_jj0", "经纪个人场内公募非货加仓交易量": "pub_fh_kgdb_trdamt_ppcadd_jjgr"}
def to_pandas(frame):
    if hasattr(frame, "to_pandas"): return frame.to_pandas()
    if hasattr(frame, "to_dicts"): return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()
wd, zb = to_pandas(strategy_contribution_wd), to_pandas(strategy_contribution_zb)
for frame, required, name in ((wd, JOIN_KEYS + ["metric_name"], "table_wd"), (zb, JOIN_KEYS, "table_zb")):
    missing = [column for column in required if column not in frame.columns]
    if missing: raise ValueError(name + " 缺少必要字段: " + ", ".join(missing))
wd = wd.copy()
wd["metric_id"] = wd["metric_name"].map(METRIC_ID_BY_NAME)
unknown = wd.loc[wd["metric_id"].isna(), "metric_name"].dropna().unique().tolist()
if unknown: raise ValueError("未配置对应转化指标 ID: " + ", ".join(map(str, unknown)))
join_keys = JOIN_KEYS.copy()
if "metric_time" in wd.columns and "metric_time" in zb.columns: join_keys.insert(0, "metric_time")
metric_columns = []
for metric_id in wd["metric_id"].dropna().astype(str).unique():
    metric_columns.extend(column for column in ("digo_" + metric_id, "digo_" + metric_id + "_trans_user_cnt") if column in zb.columns)
if not metric_columns: raise ValueError("table_zb 中没有找到 table_wd 指标对应的 digo_ 指标列")
long = zb[join_keys + metric_columns].melt(id_vars=join_keys, var_name="metric_column", value_name="metric_value")
long["metric_id"] = long["metric_column"].str.removeprefix("digo_")
long["metric_kind"] = "转化规模"
mask = long["metric_id"].str.endswith("_trans_user_cnt")
long.loc[mask, "metric_id"] = long.loc[mask, "metric_id"].str.removesuffix("_trans_user_cnt")
long.loc[mask, "metric_kind"] = "转化人数"
long["metric_value"] = pd.to_numeric(long["metric_value"], errors="coerce").fillna(0)
wide = long.groupby(join_keys + ["metric_id", "metric_kind"], dropna=False)["metric_value"].sum().unstack("metric_kind", fill_value=0).reset_index()
for column in ("转化规模", "转化人数"):
    if column not in wide.columns: wide[column] = 0
left, right = wd.copy(), wide.copy()
for key in join_keys + ["metric_id"]:
    left[key], right[key] = left[key].astype("string"), right[key].astype("string")
joined = left.merge(right[join_keys + ["metric_id", "转化规模", "转化人数"]], on=join_keys + ["metric_id"], how="left", validate="many_to_one")
joined[["转化规模", "转化人数"]] = joined[["转化规模", "转化人数"]].apply(pd.to_numeric, errors="coerce").fillna(0)
result = joined[["转化规模", "转化人数"]].sum().to_frame().T.to_dict(orient="records")

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}