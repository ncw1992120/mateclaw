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


wd, zb = to_pandas(strategy_contribution_wd), to_pandas(strategy_contribution_zb)
# wd（维度/指标清单）决定最终要展示哪些指标；每行至少要有稳定的指标 ID。
# 这里不要求写死维度列名，后续关联粒度由两个输入实际共有的字段推导。
required_wd = ["metric_id"]
missing_wd = [column for column in required_wd if column not in wd.columns]
if missing_wd:
    raise ValueError("strategy_contribution_wd 缺少必要字段: " + ", ".join(missing_wd))

# 仅用两份输入都包含的普通字段关联数据；metric_id 是 wd 的指标标识，
# metric_name 是展示名，digo_* 则是 zb 的指标值，不参与维度关联。
join_keys = [
    column
    for column in wd.columns
    if column in zb.columns
    and column not in ("metric_id", "metric_name")
    and not column.startswith(METRIC_PREFIX)
]
if not join_keys:
    raise ValueError("两个上游数据集没有可用于关联的共有维度字段")

# 将 ID 规范为字符串，是为了避免一边由数据源返回数字、另一边返回字符串时无法匹配。
# 同时生成选中 ID 集合，供下方从 zb 的所有列中筛出本次需要的指标列。
wd = wd.copy()
wd["metric_id"] = wd["metric_id"].astype("string")
selected_metric_ids = set(wd["metric_id"].dropna().astype(str))
column_kinds = {
    column: metric_id_and_kind(column, selected_metric_ids)
    for column in zb.columns
}
metric_columns = [column for column, parsed in column_kinds.items() if parsed]
if not metric_columns:
    raise ValueError("strategy_contribution_zb 中没有找到与维度数据集 metric_id 对应的 digo_ 指标列")

# zb 是“宽表”：每个指标/口径各占一列。先 melt 成长表（一行对应一个维度、指标和口径），
# 再把列名解析出的 metric_id 与口径补回行数据，这样便于按维度和指标统一汇总。
# 展开宽指标列时，只保留两份数据共有的维度，避免跨日期或跨业务对象串数。
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
# 非数值、空值统一按 0 处理；groupby 汇总后再把 value/users 两种口径转回并列字段。
long["metric_value"] = pd.to_numeric(long["metric_value"], errors="coerce").fillna(0)
wide = (
    long.groupby(join_keys + ["metric_id", "metric_kind"], dropna=False)["metric_value"]
    .sum()
    .unstack("metric_kind", fill_value=0)
    .reset_index()
)
for column in ("value", "users"):
    # 如果输入没有任何一列提供某种口径，仍补零列，保证后续合并和输出字段结构稳定。
    if column not in wide.columns:
        wide[column] = 0

# wd 若包含共有维度之外的描述字段，它们不应改变指标宽表的关联粒度；
# 因此关联前按实际共有维度与指标 ID 去重，防止重复放大汇总值。
left_columns = join_keys + ["metric_id"]
if "metric_name" in wd.columns:
    left_columns.append("metric_name")
# 去重时保留展示名，但不把其他描述字段带入关联：那些字段可能让同一指标被重复匹配。
left = wd[left_columns].drop_duplicates().copy()
right = wide.copy()
# 与上面一致，关联键统一成字符串类型。
for key in join_keys + ["metric_id"]:
    left[key] = left[key].astype("string")
    right[key] = right[key].astype("string")
joined = left.merge(
    right[join_keys + ["metric_id", "value", "users"]],
    on=join_keys + ["metric_id"],
    how="left",
    validate="many_to_one",
)
# 左关联保留维度/指标清单；宽表没有匹配行的指标不会丢失，而是将对应统计值补成 0。
joined[["value", "users"]] = joined[["value", "users"]].apply(
    pd.to_numeric,
    errors="coerce",
).fillna(0)

# 展示标题来自上游维度数据集的 metric_name；没有展示名时才回退到 metric_id。
# 宽表当前只提供技术列名，因此人数标题在来源名称后追加其通用业务含义。
metric_names = (
    wd[["metric_id", "metric_name"]].dropna(subset=["metric_id"])
    if "metric_name" in wd.columns
    else wd[["metric_id"]].assign(metric_name=wd["metric_id"])
)
metric_names["metric_id"] = metric_names["metric_id"].astype("string")
metric_names = metric_names.drop_duplicates("metric_id").set_index("metric_id")["metric_name"]
# 最终结果按指标 ID 汇总所有共有维度组合，生成一行供下游表格/组件消费。
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
        # 展示名可能重复；追加 ID 保证输出字典中的列名仍唯一，不覆盖其他指标。
        label = f"{label} ({metric_id})"
    used_labels.add(label)
    result_row[label] = values["value"].item() if hasattr(values["value"], "item") else values["value"]
    users_label = f"{label}-转化人数"
    result_row[users_label] = values["users"].item() if hasattr(values["users"], "item") else values["users"]

# Runner 脚本的结果约定是“记录列表”；此处每个元素是一行，key 是展示列名。
result = [result_row]

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}
