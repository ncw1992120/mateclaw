# 子策略贡献表输入别名固定为：table_ab（digo_ 宽指标）、table_wd（维度明细）。
# 上游数据已按查询配置应用筛选、排序和分页；这里仅做宽转长、关联和指标计算。
if "table_ab" not in locals():
    table_ab = datasets.input(input_name="table_ab").to_polars()
if "table_wd" not in locals():
    table_wd = datasets.input(input_name="table_wd").to_polars()

import pandas as pd

METRIC_PREFIX = "digo_"
USER_COUNT_SUFFIX = "_trans_user_cnt"
DIMENSION_COLUMNS = [
    "metric_time",
    "attribution_plan_id",
    "attribution_strategy_id",
    "platform_id",
    "channel",
]
# 这四个业务键是当前数据集稳定提供的；metric_time 只在两边都投影时参与关联。
REQUIRED_DIMENSIONS = [
    "attribution_plan_id",
    "attribution_strategy_id",
    "platform_id",
    "channel",
]


def to_pandas(frame):
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()


def require_columns(frame, columns, dataset_name):
    missing = [column for column in columns if column not in frame.columns]
    if missing:
        raise ValueError(f"{dataset_name} 缺少必要字段: {', '.join(missing)}")


def parse_metric_column(column):
    if column.endswith(USER_COUNT_SUFFIX):
        return column[len(METRIC_PREFIX):-len(USER_COUNT_SUFFIX)], "转化人数"
    return column[len(METRIC_PREFIX):], "转化规模"


def transform(wd, zb):
    if wd.empty or zb.empty:
        return []

    require_columns(wd, REQUIRED_DIMENSIONS + ["metric_id"], "table_wd 维度数据集")
    require_columns(zb, REQUIRED_DIMENSIONS, "table_ab 宽指标数据集")
    dimensions = [
        column for column in DIMENSION_COLUMNS
        if column in wd.columns and column in zb.columns
    ]
    metric_columns = [
        column for column in zb.columns
        if column not in dimensions and column.startswith(METRIC_PREFIX)
    ]
    if not metric_columns:
        raise ValueError("table_ab 中没有找到 digo_ 指标字段")

    long_metrics = zb[dimensions + metric_columns].melt(
        id_vars=dimensions,
        var_name="metric_column",
        value_name="metric_value",
    )
    long_metrics[["metric_id", "metric_kind"]] = pd.DataFrame(
        long_metrics["metric_column"].map(parse_metric_column).tolist(),
        index=long_metrics.index,
    )
    long_metrics["metric_value"] = pd.to_numeric(
        long_metrics["metric_value"], errors="coerce"
    ).fillna(0)

    wide = (
        long_metrics.groupby(dimensions + ["metric_id", "metric_kind"], dropna=False)["metric_value"]
        .sum().unstack("metric_kind", fill_value=0).reset_index()
    )
    for column in ("转化规模", "转化人数"):
        if column not in wide.columns:
            wide[column] = 0
    values = pd.to_numeric(wide["转化规模"], errors="coerce").fillna(0)
    users = pd.to_numeric(wide["转化人数"], errors="coerce").fillna(0)
    wide["转化率"] = values.div(users.where(users.ne(0))).fillna(0)

    join_keys = dimensions + ["metric_id"]
    left = wd.copy()
    right = wide.copy()
    for key in join_keys:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")
    result = left.merge(
        right[join_keys + ["转化规模", "转化人数", "转化率"]],
        on=join_keys,
        how="left",
        validate="many_to_one",
    )
    result[["转化规模", "转化人数", "转化率"]] = result[
        ["转化规模", "转化人数", "转化率"]
    ].apply(pd.to_numeric, errors="coerce").fillna(0)
    result["转化指标id"] = result["metric_id"]
    return result.to_dict(orient="records")


frame_ab = to_pandas(table_ab)
frame_wd = to_pandas(table_wd)
if frame_ab.empty or frame_wd.empty:
    result = []
else:
    # 先按本表的固定别名处理；保留结构识别作为历史别名互换时的兼容。
    if "metric_id" in frame_wd.columns and any(
        column.startswith(METRIC_PREFIX) for column in frame_ab.columns
    ):
        wd, zb = frame_wd, frame_ab
    elif "metric_id" in frame_ab.columns and any(
        column.startswith(METRIC_PREFIX) for column in frame_wd.columns
    ):
        wd, zb = frame_ab, frame_wd
    else:
        raise ValueError(
            "子策略贡献表输入列不匹配：table_wd 需要 metric_id，table_ab 需要 digo_ 指标列"
        )
    result = transform(wd, zb)
