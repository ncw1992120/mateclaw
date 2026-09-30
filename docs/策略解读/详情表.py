# ===== 造数示例与上游数据读取 =====
# 输入数据说明：datasets.input 读取的是上游数据集“查看数据-查询”产生的结果，
# 查询计划中的页面筛选、排序和分页已由平台提前应用；此脚本不再自行查询数据源。
# table_ab 与 table_wd 是数据集别名。下面的识别逻辑会根据字段结构判断哪份是维度明细、哪份是指标宽表，
# 因此即使两个别名与实际内容的对应关系不同，也会在校验后选择正确的处理方向。

# 造数示例：默认注释。若要脱离平台在本地调试，可取消注释并用上游查询结果的真实行构造 DataFrame，
# 例如：table_ab = pd.DataFrame([{"metric_time": "2026-09-01", ...}])。
# 只要对应变量已在当前执行环境中赋值，下面的 datasets.input 就会跳过该变量的真实输入读取。
# import json
# import polars as pl
# table_ab：尚未获取真实查询结果行，不生成 schema 造数；请检查上游数据集查询。
# table_wd：尚未获取真实查询结果行，不生成 schema 造数；请检查上游数据集查询。

if "table_ab" not in locals():
    table_ab = datasets.input(
        input_name="table_ab",
    ).to_polars()

if "table_wd" not in locals():
    table_wd = datasets.input(
        input_name="table_wd",
    ).to_polars()

# ===== 数据读取结束 =====

# ===== 自定义处理代码（可编辑） =====

import pandas as pd

# 维度明细与指标宽表共有的业务粒度。
# metric_time 是指标统计日期；计划、策略、平台和渠道字段用于区分业务记录。
# 这些字段会作为宽表聚合键，同时也是后续将指标结果关联回维度明细的键。
DIMENSION_COLUMNS = [
    "metric_time",
    "attribution_plan_id",
    "attribution_strategy_id",
    "platform_id",
    "channel",
]
# Aloudata 指标列约定：指标原值以 digo_ 开头；转化人数列额外以此后缀结尾。
METRIC_PREFIX = "digo_"
CONVERSION_USER_SUFFIX = "_trans_user_cnt"
# 最终关联键 = 业务维度 + 转化指标 ID；不同转化指标不能互相串数。
JOIN_KEYS = DIMENSION_COLUMNS + ["metric_id"]


def to_pandas(frame):
    # 将上游 Polars / Pandas 数据统一转换为 Pandas DataFrame。
    # DataAgent 的 datasets.input 通常返回 Polars 对象；本地调试时也允许传入
    # 已经是 Pandas 的对象。to_dicts 分支兼容支持该方法但不支持 to_pandas 的表对象。
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()


def metric_id_and_kind(column_name):
    # 从指标列名中拆出 Aloudata 指标 ID 和业务指标类型。
    # 例如 digo_xxx 表示指标 ID 为 xxx、类型为“转化规模”；
    # digo_xxx_trans_user_cnt 表示同一指标 ID 下的“转化人数”。
    # 不符合 digo_ 命名约定的字段直接报错，避免静默生成错误关联键。
    if not column_name.startswith(METRIC_PREFIX):
        raise ValueError(f"指标字段必须以 {METRIC_PREFIX} 开头: {column_name}")

    if column_name.endswith(CONVERSION_USER_SUFFIX):
        return (
            column_name[len(METRIC_PREFIX):-len(CONVERSION_USER_SUFFIX)],
            "转化人数",
        )

    return column_name[len(METRIC_PREFIX):], "转化规模"


def require_columns(frame, required_columns, dataset_name):
    # 在开始变形或关联前检查必需字段，并一次性报告全部缺失列。
    missing = [
        column for column in required_columns
        if column not in frame.columns
    ]
    if missing:
        raise ValueError(
            f"{dataset_name} 缺少必要字段: {', '.join(missing)}"
        )


def transform_strategy_readout(wd, zb):
    # 将维度明细 wd 与指标宽表 zb 合成为策略解读详情表。
    # 处理顺序：
    # 1. 检查两侧输入字段，确认维度表含关联键、宽指标表含业务维度。
    # 2. 找出宽指标表中的 digo_ 指标列，将每一列展开成“指标 ID / 指标类型 / 指标值”记录。
    # 3. 按业务维度、指标 ID 和指标类型求和，再把转化规模与转化人数透视回列。
    # 4. 计算转化率（转化规模 / 转化人数）；人数为 0 时转化率保持 0，避免除零。
    # 5. 按业务维度和指标 ID 将聚合指标关联回维度明细，返回可直接作为表格输出的结果。
    # validate="many_to_one" 要求聚合后的右表在关联键上至多一行；若仍有重复，Pandas 会报错，
    # 以便尽早发现聚合粒度或关联键错误，而不是让明细行被意外扩增。
    require_columns(wd, JOIN_KEYS, "维度数据集")
    require_columns(zb, DIMENSION_COLUMNS, "宽指标数据集")

    # 仅把维度之外的 digo_ 字段视为指标值列；其它辅助字段不参与指标聚合。
    metric_columns = [
        column
        for column in zb.columns
        if column not in DIMENSION_COLUMNS
        and column.startswith(METRIC_PREFIX)
    ]

    if not metric_columns:
        raise ValueError("宽指标数据集中没有找到 digo_ 指标字段")

    # 宽表转长表：每个指标列成为一条记录，公共维度保留在 id_vars 中。
    metric_rows = zb[
        list(DIMENSION_COLUMNS) + metric_columns
    ].melt(
        id_vars=DIMENSION_COLUMNS,
        var_name="metric_column",
        value_name="metric_value",
    )

    # 根据列名拆分指标 ID 与类型，使规模和人数能够按同一个指标 ID 配对。
    metric_rows[["转化指标id", "metric_kind"]] = (
        metric_rows["metric_column"].apply(
            lambda column: pd.Series(metric_id_and_kind(column))
        )
    )

    # 非数值或空指标按 0 处理，保证后续 sum 运算稳定。
    metric_rows["metric_value"] = pd.to_numeric(
        metric_rows["metric_value"],
        errors="coerce",
    ).fillna(0)

    # 先按维度 + 指标 ID + 指标类型聚合，再将“转化规模/转化人数”透视成独立列。
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

    # 若某个输入只包含规模或人数其中一种，补齐另一列并规范数值类型。
    for column in ("转化规模", "转化人数"):
        if column not in long_metrics:
            long_metrics[column] = 0

        long_metrics[column] = pd.to_numeric(
            long_metrics[column],
            errors="coerce",
        ).fillna(0)

    # 转化率 = 转化规模 / 转化人数；人数为 0 或缺失时保留 0。
    long_metrics["转化率"] = 0.0

    has_users = long_metrics["转化人数"] != 0
    long_metrics.loc[has_users, "转化率"] = (
        long_metrics.loc[has_users, "转化规模"]
        / long_metrics.loc[has_users, "转化人数"]
    )

    # 保留维度明细的原始列；右侧增加中文结果字段，并保留转化指标 ID 供最终展示。
    left = wd.copy()
    right = long_metrics.rename(
        columns={"转化指标id": "metric_id"}
    )
    right["转化指标id"] = right["metric_id"]

    # 两侧 ID/日期等字段可能分别被推断为数字、日期或字符串，统一成字符串再关联，
    # 避免值相同但类型不同导致匹配失败。
    for key in JOIN_KEYS:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")

    # 左关联确保维度明细记录不会因缺少指标值而被丢弃；many_to_one 防止右表重复扩行。
    result = left.merge(
        right[
            JOIN_KEYS
            + ["转化指标id", "转化规模", "转化人数", "转化率"]
        ],
        on=JOIN_KEYS,
        how="left",
        validate="many_to_one",
    )

    # 没匹配到指标时保留该维度行：指标 ID 使用空字符串，数值结果使用 0。
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


# 先转换两份输入，然后通过结构字段判断各自角色，而不依赖数据集别名的先后顺序。
frame_zb = to_pandas(table_ab)
frame_wd = to_pandas(table_wd)

# 维度明细特征：有 metric_id 关联字段；另一份表含 digo_ 宽指标列。
# 优先按字段形状识别，可兼容别名 table_ab / table_wd 与真实数据内容对调的情况。
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
    # 两份输入都不满足“一份维度明细 + 一份指标宽表”的结构时停止，
    # 不返回看似成功但字段或指标含义错误的结果。
    raise ValueError(
        "两个数据集中未识别出一个维度数据集和一个 digo_ 宽指标数据集"
    )

# 最终输出为 records 列表（每个元素是一行字段字典），供 Python 组件结果集渲染。
result = transform_strategy_readout(
    wd,
    zb,
).to_dict(orient="records")

# ===== 输出结果示例（参考） =====

# 示例仅用于说明 Python 最终结果在组件输出契约中的表格结构；
# 这里不执行该 JSON，也不会替换上面的 result。实际字段和数据由 result 决定。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}
