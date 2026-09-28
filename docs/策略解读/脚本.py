import pandas as pd

DIMENSION_COLUMNS = [
    "metric_time",
    "attribution_plan_id",
    "attribution_strategy_id",
    "platform_id",
    "channel",
]
METRIC_PREFIX = "digo_"
CONVERSION_USER_SUFFIX = "_trans_user_cnt"
JOIN_KEYS = DIMENSION_COLUMNS + ["metric_id"]


def to_pandas(frame):
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()


def metric_id_and_kind(column_name):
    if not column_name.startswith(METRIC_PREFIX):
        raise ValueError(f"指标字段必须以 {METRIC_PREFIX} 开头: {column_name}")

    if column_name.endswith(CONVERSION_USER_SUFFIX):
        return (
            column_name[len(METRIC_PREFIX):-len(CONVERSION_USER_SUFFIX)],
            "转化人数",
        )

    return column_name[len(METRIC_PREFIX):], "转化规模"


def require_columns(frame, required_columns, dataset_name):
    missing = [
        column for column in required_columns
        if column not in frame.columns
    ]
    if missing:
        raise ValueError(
            f"{dataset_name} 缺少必要字段: {', '.join(missing)}"
        )


def transform_strategy_readout(wd, zb):
    require_columns(wd, JOIN_KEYS, "维度数据集")
    require_columns(zb, DIMENSION_COLUMNS, "宽指标数据集")

    metric_columns = [
        column
        for column in zb.columns
        if column not in DIMENSION_COLUMNS
        and column.startswith(METRIC_PREFIX)
    ]

    if not metric_columns:
        raise ValueError("宽指标数据集中没有找到 digo_ 指标字段")

    metric_rows = zb[
        list(DIMENSION_COLUMNS) + metric_columns
    ].melt(
        id_vars=DIMENSION_COLUMNS,
        var_name="metric_column",
        value_name="metric_value",
    )

    metric_rows[["转化指标id", "metric_kind"]] = (
        metric_rows["metric_column"].apply(
            lambda column: pd.Series(metric_id_and_kind(column))
        )
    )

    metric_rows["metric_value"] = pd.to_numeric(
        metric_rows["metric_value"],
        errors="coerce",
    ).fillna(0)

    long_metrics = (
        metric_rows
        .groupby(
            DIMENSION_COLUMNS + ["转化指标id", "metric_kind"],
            dropna=False,
        )["metric_value"]
        .sum()
        .unstack("metric_kind", fill_value=0)
        .reset_index()
    )

    for column in ("转化规模", "转化人数"):
        if column not in long_metrics:
            long_metrics[column] = 0

        long_metrics[column] = pd.to_numeric(
            long_metrics[column],
            errors="coerce",
        ).fillna(0)

    long_metrics["转化率"] = 0.0

    has_users = long_metrics["转化人数"] != 0
    long_metrics.loc[has_users, "转化率"] = (
        long_metrics.loc[has_users, "转化规模"]
        / long_metrics.loc[has_users, "转化人数"]
    )

    left = wd.copy()
    right = long_metrics.rename(
        columns={"转化指标id": "metric_id"}
    )
    right["转化指标id"] = right["metric_id"]

    for key in JOIN_KEYS:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")

    result = left.merge(
        right[
            JOIN_KEYS
            + ["转化指标id", "转化规模", "转化人数", "转化率"]
        ],
        on=JOIN_KEYS,
        how="left",
        validate="many_to_one",
    )

    result[
        ["转化指标id", "转化规模", "转化人数", "转化率"]
    ] = result[
        ["转化指标id", "转化规模", "转化人数", "转化率"]
    ].fillna({
        "转化指标id": "",
        "转化规模": 0,
        "转化人数": 0,
        "转化率": 0.0,
    })

    return result


frame_zb = to_pandas(table_ab)
frame_wd = to_pandas(table_wd)

if (
    "metric_id" in frame_zb.columns
    and any(
        column.startswith(METRIC_PREFIX)
        for column in frame_wd.columns
    )
):
    wd, zb = frame_zb, frame_wd
elif (
    "metric_id" in frame_wd.columns
    and any(
        column.startswith(METRIC_PREFIX)
        for column in frame_zb.columns
    )
):
    wd, zb = frame_wd, frame_zb
else:
    raise ValueError(
        "两个数据集中未识别出一个维度数据集和一个 digo_ 宽指标数据集"
    )

result = transform_strategy_readout(
    wd,
    zb,
).to_dict(orient="records")