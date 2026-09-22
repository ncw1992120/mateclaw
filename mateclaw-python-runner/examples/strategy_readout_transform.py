"""策略解读数据集 Python 预处理规范样例。

这个文件是一个可以直接复制、修改和注册到 Python Dataset 的完整脚本。
脚本由 MateClaw Python Runner 执行时，Runner 会预先注入 ``datasets`` 对象，
并读取脚本最后的 ``result`` 变量作为处理结果。

本样例演示的处理链路是：

    cljd_zcl_wd 维度数据集
              +
    cljd_zcl_zb 宽指标数据集
              ↓
    指标列转行、成对指标匹配、缺失值补 0、计算转化率
              ↓
    按日期和业务维度 Join
              ↓
    result: list[dict]

开发者复制这个样例时，通常只需要修改：
1. datasets.read() 中的数据集别名；
2. DIMENSION_COLUMNS 中的维度字段；
3. 指标字段的前缀和人数后缀规则；
4. JOIN_KEYS 中的实际关联键。

不要把账号、密码、数据库连接串写进脚本。数据集访问由平台通过 datasets.read()
完成，脚本只处理平台授权后返回的数据。
"""

import pandas as pd


# ---------------------------------------------------------------------------
# 1. 数据集字段约定
# ---------------------------------------------------------------------------
# 这里列出 cljd_zcl_zb 中不参与列转行的维度字段。
# 除这些字段外，所有以 METRIC_PREFIX 开头的字段都按指标字段处理。
DIMENSION_COLUMNS = [
    "metric_time",                   # 指标日期
    "attribution_plan_id",           # 计划 id
    "attribution_strategy_id",       # 策略 id
    "platform_id",                   # 子策略 id
    "channel",                       # 触达渠道
]

# 指标字段的命名规则：
#   digo_xxx                         -> 转化规模
#   digo_xxx_trans_user_cnt          -> 转化人数
METRIC_PREFIX = "digo_"
CONVERSION_USER_SUFFIX = "_trans_user_cnt"

# Join 时必须同时包含 metric_time。
# 如果不包含日期，同一组计划/策略/子策略/渠道在不同日期的数据会互相匹配，
# 形成重复行或错误的转化率。
JOIN_KEYS = DIMENSION_COLUMNS + ["metric_id"]


def metric_id_and_kind(column_name):
    """把一个宽表指标字段解析为转化指标 ID 和指标类型。

    例如：
        digo_pub_xxx
            -> ("pub_xxx", "转化规模")
        digo_pub_xxx_trans_user_cnt
            -> ("pub_xxx", "转化人数")

    注意：这里去掉的是字段名的前缀/后缀，不修改中间的业务指标编码。
    """
    if not column_name.startswith(METRIC_PREFIX):
        raise ValueError(f"指标字段必须以 {METRIC_PREFIX} 开头: {column_name}")

    if column_name.endswith(CONVERSION_USER_SUFFIX):
        metric_id = column_name[
            len(METRIC_PREFIX):-len(CONVERSION_USER_SUFFIX)
        ]
        return metric_id, "转化人数"

    return column_name[len(METRIC_PREFIX):], "转化规模"


def require_columns(frame, required_columns, dataset_name):
    """在处理前校验数据集契约，避免字段变化后静默生成错误结果。"""
    missing = [column for column in required_columns if column not in frame.columns]
    if missing:
        raise ValueError(
            f"{dataset_name} 缺少必要字段: {', '.join(missing)}"
        )


def transform_strategy_readout(wd, zb):
    """完成指标列转行并与维度数据集 Join。

    参数：
        wd: cljd_zcl_wd 的 pandas.DataFrame。
            其中应包含 metric_id，用于和列转行后的指标 ID 关联。
        zb: cljd_zcl_zb 的 pandas.DataFrame。
            其中包含维度字段和若干 digo_* 指标字段。

    返回：
        以 wd 为主表的 pandas.DataFrame。除 wd 原有字段外，增加：
        - 转化指标id
        - 转化规模
        - 转化人数
        - 转化率
    """
    require_columns(wd, JOIN_KEYS, "cljd_zcl_wd")
    require_columns(zb, DIMENSION_COLUMNS, "cljd_zcl_zb")

    # 只把 digo_ 开头的非维度字段识别为指标字段。
    # 这样新增普通备注列、技术列时，不会被误当成指标展开。
    metric_columns = [
        column
        for column in zb.columns
        if column not in DIMENSION_COLUMNS and column.startswith(METRIC_PREFIX)
    ]
    if not metric_columns:
        raise ValueError("cljd_zcl_zb 中没有找到 digo_ 指标字段")

    # 宽表转成长表：每个指标字段先变成一行，保留原始维度字段。
    # 例：
    #   digo_pub_xxx = 100
    #   digo_pub_xxx_trans_user_cnt = 4
    # 会先变成两行：
    #   pub_xxx / 转化规模 / 100
    #   pub_xxx / 转化人数 / 4
    metric_rows = zb[list(DIMENSION_COLUMNS) + metric_columns].melt(
        id_vars=DIMENSION_COLUMNS,
        var_name="metric_column",
        value_name="metric_value",
    )
    metric_rows[["转化指标id", "metric_kind"]] = metric_rows["metric_column"].apply(
        lambda column: pd.Series(metric_id_and_kind(column))
    )

    # 空值、非数字值按 0 处理。业务上缺少成对指标时也要求补 0。
    metric_rows["metric_value"] = pd.to_numeric(
        metric_rows["metric_value"], errors="coerce"
    ).fillna(0)

    # 同一维度、同一转化指标可能来自多个原始行，因此先求和再展开。
    # pivot 后，如果某个指标没有规模列或没有人数列，下面会显式补 0。
    long_metrics = (
        metric_rows.groupby(
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
            long_metrics[column], errors="coerce"
        ).fillna(0)

    # 转化率定义为转化规模 / 转化人数。
    # 人数为 0 时按用户要求补 0，避免产生 inf 或 NaN。
    long_metrics["转化率"] = 0.0
    has_users = long_metrics["转化人数"] != 0
    long_metrics.loc[has_users, "转化率"] = (
        long_metrics.loc[has_users, "转化规模"]
        / long_metrics.loc[has_users, "转化人数"]
    )

    # 不同数据源适配器可能把 metric_time 返回成字符串或日期类型。
    # 统一成字符串后再 Join，避免因类型不同导致本应匹配的记录匹配失败。
    left = wd.copy()
    right = long_metrics.rename(columns={"转化指标id": "metric_id"})
    right["转化指标id"] = right["metric_id"]
    for key in JOIN_KEYS:
        left[key] = left[key].astype("string")
        right[key] = right[key].astype("string")

    # 以 cljd_zcl_wd 为主表做 left join，保证维度数据集中的指标记录不丢失。
    # right 侧按 JOIN_KEYS 应为一条记录，因此使用 many_to_one 让重复数据直接报错，
    # 避免异常数据静默放大结果集。
    result = left.merge(
        right[JOIN_KEYS + ["转化指标id", "转化规模", "转化人数", "转化率"]],
        on=JOIN_KEYS,
        how="left",
        validate="many_to_one",
    )
    result[["转化指标id", "转化规模", "转化人数", "转化率"]] = result[
        ["转化指标id", "转化规模", "转化人数", "转化率"]
    ].fillna({"转化指标id": "", "转化规模": 0, "转化人数": 0, "转化率": 0.0})
    return result


# ---------------------------------------------------------------------------
# 2. Runner 执行入口
# ---------------------------------------------------------------------------
# Runner 会在执行用户脚本前注入 datasets。保留这个判断是为了让本文件也能被
# 测试代码 import，测试时只复用上面的纯处理函数，不会误发起数据集读取请求。
if "datasets" in globals():
    wd = datasets.read("cljd_zcl_wd").to_pandas()
    zb = datasets.read("cljd_zcl_zb").to_pandas()

    # Runner 会序列化 result。list[dict] 是当前 Python Runner 的通用结果契约。
    result = transform_strategy_readout(wd, zb).to_dict(orient="records")
