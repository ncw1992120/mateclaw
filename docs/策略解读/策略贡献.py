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

# 本脚本用于“策略贡献”组件：把指标宽表中的每个选中指标列汇总成一组贡献值，
# 最终返回一行记录；每个指标输出“指标展示名”和“指标展示名-转化人数”两个字段。
# strategy_contribution_wd 是指标目录/维度侧输入，提供 metric_id、metric_name 和关联维度；
# strategy_contribution_zb 是指标宽表输入，提供维度列以及 digo_<metric_id> 指标列。
import pandas as pd

# 指标宽表通过列名表达指标及其统计口径：
#   digo_<metric_id>                         -> 指标值
#   digo_<metric_id>_trans_user_cnt          -> 转化人数
# 下面两个常量集中描述这个命名约定，避免在解析逻辑中散落硬编码。
METRIC_PREFIX = "digo_"
USER_COUNT_SUFFIX = "_trans_user_cnt"
METRIC_ID_BY_NAME = {
    "经纪个人客户场内公募非货当年净买入": "trd_fund_amt_inout_cy_jjgr",
    "经纪个人场内公募非货交易量": "fund_trd_amt_a566_fh_kgdb_jj0",
    "经纪个人场内公募非货加仓交易量": "pub_fh_kgdb_trdamt_ppcadd_jjgr",
}


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
    """将策略维度清单与指标宽表合成为供贡献表卡片展示的一行汇总记录。

    wd 的每行表示某个指标在某组业务维度下的记录；zb 中同一组维度的指标值横向铺在列上。
    本函数先将 zb 宽转长、按共有维度和 metric_id 归并，再与 wd 对齐，最后跨所有维度汇总。
    """
    # 页面筛选后任一输入无数据时返回合法空表，不继续做列识别和 melt。
    if wd.empty or zb.empty:
        return []

    # 宽表使用 Aloudata 指标 ID 作为列名，维度集在旧版契约中可能只有中文 metric_name。
    # 优先按已知展示名映射；其余情况下保留原值，兼容 metric_name 本身就是 ID 的旧数据。
    if "metric_id" not in wd.columns and "metric_name" in wd.columns:
        wd = wd.copy()
        wd["metric_id"] = wd["metric_name"].map(METRIC_ID_BY_NAME).fillna(wd["metric_name"])
    elif "metric_id" in wd.columns and "metric_name" in wd.columns:
        wd = wd.copy()
        mapped_ids = wd["metric_name"].map(METRIC_ID_BY_NAME)
        wd["metric_id"] = mapped_ids.fillna(wd["metric_id"])
    if "metric_id" not in wd.columns:
        raise ValueError("strategy_contribution_wd 缺少必要字段: metric_id（兼容字段: metric_name）")

    # 自动发现两份数据共有的业务维度列作为 Join 键，便于输入字段增减时无需维护硬编码列表。
    # metric_id/metric_name 是指标标识而非业务维度；digo_ 开头的列属于指标值，也不能参与 Join。
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
    # 用维度侧本次实际选择的指标 ID 做白名单，过滤宽表中虽存在但当前组件未配置的指标列。
    selected_metric_ids = set(wd["metric_id"].dropna().astype(str))
    column_kinds = {
        column: metric_id_and_kind(column, selected_metric_ids)
        for column in zb.columns
    }
    metric_columns = [column for column, parsed in column_kinds.items() if parsed]
    if not metric_columns:
        raise ValueError("strategy_contribution_zb 中没有找到与维度数据集 metric_id/metric_name 对应的 digo_ 指标列")

    # melt 将每个指标列变成一行：metric_column 保存原列名，metric_value 保存该行的值；
    # join_keys 保留在每条记录上，保证后续分组不会把不同日期、计划、策略、子策略或渠道串在一起。
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
    # 上游可能返回字符串或空值；无法转换的值按 0 处理，供后续数值汇总。
    long["metric_value"] = pd.to_numeric(long["metric_value"], errors="coerce").fillna(0)
    # 同一个维度组合和指标可能有多行，因此先求和；unstack 再把 value/users 两种口径还原成列。
    # 某指标若只有“值”或只有“人数”列，后面的补列逻辑会把缺失口径填为 0。
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
    # 统一关联键类型，避免例如数字 1 与字符串 "1" 因类型不同而无法匹配。
    for key in join_keys + ["metric_id"]:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")
    joined = left.merge(
        right[join_keys + ["metric_id", "value", "users"]],
        on=join_keys + ["metric_id"],
        how="left",
        validate="many_to_one",
    )
    # wd 可能包含某指标/维度组合而 zb 没有对应值；左连接保留目录侧记录，空指标值补成 0。
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
    # 贡献卡片不展示每个维度组合的明细，而是按指标 ID 汇总整个当前查询结果。
    totals = joined.groupby("metric_id", dropna=False)[["value", "users"]].sum()
    result_row = {}
    used_labels = set()
    for metric_id in wd["metric_id"].dropna().drop_duplicates().astype(str):
        if metric_id not in totals.index:
            continue
        values = totals.loc[metric_id]
        label = metric_names.get(metric_id)
        label = str(label) if pd.notna(label) and str(label).strip() else metric_id
        # 不同指标可能配置了相同中文名；为避免结果字典的同名 key 覆盖，重复名称附上技术 ID。
        if label in used_labels:
            label = f"{label} ({metric_id})"
        used_labels.add(label)
        result_row[label] = values["value"].item() if hasattr(values["value"], "item") else values["value"]
        result_row[f"{label}-转化人数"] = values["users"].item() if hasattr(values["users"], "item") else values["users"]
    # Runner 约定 result 是 records 列表；单条汇总结果因此包装成单元素列表。
    return [result_row] if result_row else []


# 统一为 Pandas 以复用 melt/groupby/merge；to_pandas() 同时兼容脚本 Runner 的 Polars 输入。
wd, zb = to_pandas(strategy_contribution_wd), to_pandas(strategy_contribution_zb)
# Runner 脚本的结果约定为 records 列表；无数据时直接返回 []，避免生成 [{}]。
result = transform_contribution(wd, zb)

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}
