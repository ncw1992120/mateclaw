# ===== 造数示例与上游数据读取 =====
# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。

# 造数示例：默认注释；取消对应注释即可用上游「查看数据-查询」的最近结果进行本地调试。
# 造数优先：变量已在此处赋值时，下面会跳过对应的真实数据读取。
# import json
# import polars as pl
# table_wd = pl.DataFrame(json.loads("[{\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\"}]"))
# table_zb = pl.DataFrame(json.loads("[{\"digo_trd_fund_amt_inout_cy_jjgr\":\"7620000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"787\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"2569000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"600\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"5007000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"454\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"1347000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"241\",\"digo_cust_asset_in\":\"1108\",\"digo_new_cust_asset_in\":\"439\",\"digo_cnt_cust_code_new_brok\":\"931\",\"digo_cust_valid_new_yxh\":\"552\",\"digo_cust_asset_in10000\":\"262\"}]"))

if "table_wd" not in locals():
    table_wd = datasets.input(
        input_name="table_wd",
    ).to_polars()

if "table_zb" not in locals():
    table_zb = datasets.input(
        input_name="table_zb",
    ).to_polars()

# ===== 数据读取结束 =====

# ===== 自定义处理代码（可编辑） =====

import pandas as pd


def to_pandas(frame):
    """将 DataAgent 提供的表格转换成 pandas，便于按列名取值。"""
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()


# table_wd 提供 metric_id；table_zb 是 Aloudata 指标宽表。
wd = to_pandas(table_wd)
zb = to_pandas(table_zb)

# 页面筛选后任一数据集为空时，没有可展示的转换指标。
if wd.empty or zb.empty:
    result = []
else:
    if "metric_id" not in wd.columns:
        raise ValueError("table_wd 缺少 metric_id 字段，无法定位 table_zb 中的指标列")

    # metric_id 是列名中 digo_ 前缀后面的部分。
    # 例如 metric_id=abc，会对应规模列 digo_abc 和人数列 digo_abc_trans_user_cnt。
    metric_ids = (
        wd["metric_id"]
        .dropna()
        .astype(str)
        .str.strip()
    )
    metric_ids = [metric_id for metric_id in metric_ids.unique() if metric_id]

    if not metric_ids:
        result = []
    else:
        # 这个组件需要一行、两个固定展示字段。
        # 若查询返回多行，分别累加这些行的指标规模和转化人数。
        total_scale = 0
        total_users = 0

        for metric_id in metric_ids:
            scale_column = f"digo_{metric_id}"
            users_column = f"digo_{metric_id}_trans_user_cnt"

            # 指标列缺失时明确报错，避免静默展示错误的 0。
            missing_columns = [
                column for column in (scale_column, users_column)
                if column not in zb.columns
            ]
            if missing_columns:
                raise ValueError(
                    "table_zb 缺少 metric_id 对应的指标列: "
                    + ", ".join(missing_columns)
                )

            # digo_distr_count_1 不参与计算；只读取 metric_id 指向的两列。
            # Aloudata 有时会把数字作为字符串返回，这里统一转换为数值再求和。
            total_scale += pd.to_numeric(
                zb[scale_column], errors="coerce"
            ).fillna(0).sum()
            total_users += pd.to_numeric(
                zb[users_column], errors="coerce"
            ).fillna(0).sum()

        # 输出字段名与仪表盘卡片配置一致，供卡片直接展示。
        result = [{
            "转化规模": total_scale.item() if hasattr(total_scale, "item") else total_scale,
            "转化人数": total_users.item() if hasattr(total_users, "item") else total_users,
        }]

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}