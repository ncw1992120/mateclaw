# ===== 系统生成区域：上游输入数据读取 =====
# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。

if "table_ab" not in locals():
    table_ab = datasets.input(
        input_name="table_ab",
    ).to_pandas()

if "table_wd" not in locals():
    table_wd = datasets.input(
        input_name="table_wd",
    ).to_pandas()

# ===== 数据读取结束 =====

# ===== 自定义处理代码（可编辑） =====

import pandas as pd

# 这四列描述同一条业务记录：属于哪个计划、策略、子策略，以及通过什么渠道触达。
# 注意：两个数据集当前都不包含 metric_time（指标日期），所以日期不参与 Python 关联。
# 日期/页面筛选应在数据集查询阶段完成，Python 这里只处理查询返回的结果。
BUSINESS_KEYS = [
    "attribution_plan_id",     # 计划 ID
    "attribution_strategy_id", # 策略 ID
    "platform_id",             # 子策略 ID
    "channel",                 # 触达渠道，例如 APP、SMS
]

# 宽指标数据集用 digo_ 开头的列保存指标数值。
# 带 _trans_user_cnt 后缀的列保存该指标对应的转化人数。
METRIC_PREFIX = "digo_"
CONVERSION_USER_SUFFIX = "_trans_user_cnt"

# 维度数据集中的 metric_id 指明当前记录要查找哪个指标。
# 因此最终关联键是四个业务字段 + 指标 ID；不包含已删除的指标日期字段。
JOIN_KEYS = BUSINESS_KEYS + ["metric_id"]


def to_pandas(frame):
    """将 DataAgent 提供的表格对象转换为 Pandas，统一后续处理方式。"""
    # 兼容传入 Polars DataFrame 的情况；若输入已经是 Pandas DataFrame，则直接复制。
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    # 兼容只支持逐行字典读取的表格对象。
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    # 如果输入已经是 Pandas DataFrame，则复制一份，避免修改原始输入。
    return frame.copy()


def parse_metric_column(column_name):
    """从宽表列名提取指标 ID，并判断它代表指标规模还是转化人数。"""
    # 例如 digo_metricA 对应指标 metricA 的规模值。
    # digo_metricA_trans_user_cnt 对应同一指标的转化人数。
    metric_id = column_name[len(METRIC_PREFIX):]
    if metric_id.endswith(CONVERSION_USER_SUFFIX):
        metric_id = metric_id[:-len(CONVERSION_USER_SUFFIX)]
        return metric_id, "转化人数"
    return metric_id, "转化规模"


def require_columns(frame, required_columns, dataset_name):
    """检查输入列是否齐全；缺字段时给出可读错误，避免产出错误关联结果。"""
    missing = [column for column in required_columns if column not in frame.columns]
    if missing:
        raise ValueError(f"{dataset_name} 缺少必要字段: {', '.join(missing)}")


def transform_strategy_readout(dimension_data, wide_data):
    """把宽指标摊平成明细，再按业务键和 metric_id 关联回维度数据。"""
    # 维度数据必须带 metric_id；宽表至少要带四个业务关联字段。
    require_columns(dimension_data, JOIN_KEYS, "维度数据集")
    require_columns(wide_data, BUSINESS_KEYS, "宽指标数据集")

    # 找出所有指标值列。普通业务字段不以 digo_ 开头，不会进入指标处理。
    metric_columns = [
        column for column in wide_data.columns
        if column.startswith(METRIC_PREFIX)
    ]
    if not metric_columns:
        raise ValueError("宽指标数据集中没有找到 digo_ 开头的指标字段")

    # 宽表一行有许多指标列；melt 将它们转换成“指标列名、指标值”两列，
    # 这样就能从列名解析 metric_id，并与维度数据中的 metric_id 精确匹配。
    metric_rows = wide_data[BUSINESS_KEYS + metric_columns].melt(
        id_vars=BUSINESS_KEYS,
        var_name="指标列名",
        value_name="指标值",
    )
    metric_rows[["metric_id", "指标类型"]] = pd.DataFrame(
        metric_rows["指标列名"].map(parse_metric_column).tolist(),
        index=metric_rows.index,
        columns=["metric_id", "指标类型"],
    )

    # Aloudata 可能把数字以字符串返回；转换失败或空值统一按 0 处理。
    metric_rows["指标值"] = pd.to_numeric(
        metric_rows["指标值"], errors="coerce"
    ).fillna(0)

    # 同一业务对象、同一指标如果有多行，就分别累计规模和转化人数。
    grouped = metric_rows.groupby(
        BUSINESS_KEYS + ["metric_id", "指标类型"], dropna=False
    )["指标值"].sum()
    metrics = grouped.unstack("指标类型", fill_value=0).reset_index()

    # 有些指标可能没有人数列或规模列，补 0 使后续输出字段始终完整。
    for column in ("转化规模", "转化人数"):
        if column not in metrics.columns:
            metrics[column] = 0
        metrics[column] = pd.to_numeric(metrics[column], errors="coerce").fillna(0)

    # 转化率 = 转化规模 ÷ 转化人数；人数为 0 时安全地返回 0。
    metrics["转化率"] = 0.0
    has_users = metrics["转化人数"] != 0
    metrics.loc[has_users, "转化率"] = (
        metrics.loc[has_users, "转化规模"] / metrics.loc[has_users, "转化人数"]
    )

    # 将关联键都转成字符串，避免一边是数字、一边是字符串时匹配失败。
    # metric_time 即使残留在上游响应中，也已从当前配置删除；不作为关联键或输出字段。
    left = dimension_data.drop(columns=["metric_time"], errors="ignore").copy()
    right = metrics.copy()
    for key in JOIN_KEYS:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")

    # 多字段精确关联：四个业务字段保证属于同一业务记录，metric_id 保证指标相同。
    # 日期字段已经从数据集中移除，所以这里不再将日期作为关联条件。
    result = left.merge(
        right[JOIN_KEYS + ["转化规模", "转化人数", "转化率"]],
        on=JOIN_KEYS,
        how="left",
        validate="many_to_one",
    )

    # 宽表中找不到对应指标时，用 0 显示，避免空值影响表格和后续计算。
    for column in ("转化规模", "转化人数", "转化率"):
        result[column] = pd.to_numeric(result[column], errors="coerce").fillna(0)

    return result


# 取出两个上游查询结果。查询配置中的固定筛选和页面筛选，
# 应当已在进入 Python 前作用于这两个输入；本脚本不重新实现筛选。
frame_ab = to_pandas(table_ab)
frame_wd = to_pandas(table_wd)

# 按字段特征识别输入角色，避免依赖用户给两个数据集起的先后顺序：
#   - 有 metric_id 的是维度数据；
#   - 有 digo_ 指标列的是宽指标数据。
if frame_ab.empty or frame_wd.empty:
    # 上游筛选后没有数据是正常情况，输出空表即可。
    result = []
elif "metric_id" in frame_wd.columns and any(
    column.startswith(METRIC_PREFIX) for column in frame_ab.columns
):
    result = transform_strategy_readout(frame_wd, frame_ab).to_dict(orient="records")
elif "metric_id" in frame_ab.columns and any(
    column.startswith(METRIC_PREFIX) for column in frame_wd.columns
):
    result = transform_strategy_readout(frame_ab, frame_wd).to_dict(orient="records")
else:
    raise ValueError(
        "无法识别两个输入：一个应包含 metric_id，另一个应包含 digo_ 指标列"
    )

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}

12:00