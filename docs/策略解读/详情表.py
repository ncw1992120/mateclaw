# ===== 造数示例与上游数据读取 =====
# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。

# 造数示例：默认注释；取消对应注释即可用上游「查看数据-查询」的最近结果进行本地调试。
# 造数优先：变量已在此处赋值时，下面会跳过对应的真实数据读取。
# import json
# import polars as pl
# table_ab = pl.DataFrame(json.loads("[{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"channel\":\"APP\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"3890000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"400\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"1320000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"302\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"2670000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"232\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"670000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"123\",\"digo_cust_asset_in\":\"574\",\"digo_new_cust_asset_in\":\"228\",\"digo_cnt_cust_code_new_brok\":\"480\",\"digo_cust_valid_new_yxh\":\"285\",\"digo_cust_asset_in10000\":\"135\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"channel\":\"SMS\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"3060000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"319\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"991000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"255\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"2020000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"181\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"551000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"99\",\"digo_cust_asset_in\":\"445\",\"digo_new_cust_asset_in\":\"175\",\"digo_cnt_cust_code_new_brok\":\"371\",\"digo_cust_valid_new_yxh\":\"214\",\"digo_cust_asset_in10000\":\"102\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-002\",\"platform_id\":\"SUB-002\",\"channel\":\"APP\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"4580000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"474\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"1582000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"354\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"2894000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"276\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"823000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"144\",\"digo_cust_asset_in\":\"657\",\"digo_new_cust_asset_in\":\"264\",\"digo_cnt_cust_code_new_brok\":\"559\",\"digo_cust_valid_new_yxh\":\"338\",\"digo_cust_asset_in10000\":\"161\"}]"))
# table_wd = pl.DataFrame(json.loads("[{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"trd_fund_amt_inout_cy_jjgr\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"fund_trd_amt_a566_fh_kgdb_jj0\",\"metric_name\":\"经纪个人场内公募非货交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"SMS\",\"metric_id\":\"trd_fund_amt_inout_cy_jjgr\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"390\",\"digo_distr_user_cnt_a\":\"315\",\"digo_touch_cnt_1\":\"264\",\"digo_touch_user_cnt_1\":\"213\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"SMS\",\"metric_id\":\"fund_trd_amt_a566_fh_kgdb_jj0\",\"metric_name\":\"经纪个人场内公募非货交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"390\",\"digo_distr_user_cnt_a\":\"315\",\"digo_touch_cnt_1\":\"264\",\"digo_touch_user_cnt_1\":\"213\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"SMS\",\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"390\",\"digo_distr_user_cnt_a\":\"315\",\"digo_touch_cnt_1\":\"264\",\"digo_touch_user_cnt_1\":\"213\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-002\",\"attribution_strategy_name\":\"新客增长策略\",\"attribution_over_by\":\"strategy_owner_b\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"trd_fund_amt_inout_cy_jjgr\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\",\"digo_strategy_cnt\":\"9\",\"digo_strategy_cnt_distr_1\":\"9\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-002\",\"attribution_strategy_name\":\"新客增长策略\",\"attribution_over_by\":\"strategy_owner_b\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"fund_trd_amt_a566_fh_kgdb_jj0\",\"metric_name\":\"经纪个人场内公募非货交易量\",\"digo_strategy_cnt\":\"9\",\"digo_strategy_cnt_distr_1\":\"9\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-002\",\"attribution_strategy_name\":\"新客增长策略\",\"attribution_over_by\":\"strategy_owner_b\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\",\"digo_strategy_cnt\":\"9\",\"digo_strategy_cnt_distr_1\":\"9\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"}]"))

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

# 本脚本用于“详情表”组件：保留维度数据集中的业务字段，并为每个 metric_id 生成一条明细。
# table_ab 是指标宽表（digo_ 指标列）；table_wd 是维度/指标目录数据（含 metric_id、业务维度和名称）。
# 输出在每条维度记录上附加“转化指标id、转化规模、转化人数、转化率”，而不是汇总成单行卡片。
import pandas as pd

# 这些字段必须同时出现在两份输入中，才能关联宽表与目录明细；metric_id 则单独作为指标关联键。
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
    """把 Runner 提供的 Polars/兼容 DataFrame 转成 Pandas，便于执行 melt 和 merge。"""
    # 优先走 Polars 的原生转换；测试或轻量运行环境若只提供 to_dicts，则从记录列表建表。
    # 已是 Pandas 时复制，避免本脚本的类型转换和列赋值修改 Runner 持有的输入对象。
    if hasattr(frame, "to_pandas"):
        return frame.to_pandas()
    if hasattr(frame, "to_dicts"):
        return pd.DataFrame.from_records(frame.to_dicts())
    return frame.copy()


def metric_id_and_kind(column_name):
    """将 digo_ 指标列名解析为 (指标 ID, 输出口径)。

    例如 digo_xxx 表示转化规模，digo_xxx_trans_user_cnt 表示转化人数；
    该脚本的宽表字段按此命名协议提供，其他前缀视为输入结构错误。
    """
    if not column_name.startswith(METRIC_PREFIX):
        raise ValueError(f"指标字段必须以 {METRIC_PREFIX} 开头: {column_name}")

    if column_name.endswith(CONVERSION_USER_SUFFIX):
        return (
            column_name[len(METRIC_PREFIX):-len(CONVERSION_USER_SUFFIX)],
            "转化人数",
        )

    return column_name[len(METRIC_PREFIX):], "转化规模"


def require_columns(frame, required_columns, dataset_name):
    """在转换前校验输入字段，尽早给出缺失字段名，避免后续 Pandas 报难定位的 KeyError。"""
    missing = [
        column for column in required_columns
        if column not in frame.columns
    ]
    if missing:
        raise ValueError(
            f"{dataset_name} 缺少必要字段: {', '.join(missing)}"
        )


def transform_strategy_readout(wd, zb):
    """将指标宽表转成长指标，并按 JOIN_KEYS 关联回维度明细。

    wd：维度数据集，每条记录包含 metric_time、计划/策略/子策略/渠道和 metric_id。
    zb：宽指标数据集，包含相同业务维度及一个或多个 digo_ 指标列。
    返回：保留 wd 字段的明细 DataFrame，并附加转化指标 ID、规模、人数、比率。
    """
    # 先检查两侧用于 Join 的完整键；避免缺键时生成错误的笛卡尔积或错误匹配。
    require_columns(wd, JOIN_KEYS, "维度数据集")
    require_columns(zb, DIMENSION_COLUMNS, "宽指标数据集")

    # 只把非维度列中的 digo_ 字段视为指标值。成对字段会在解析时归到同一个指标 ID，
    # 未成对的“规模”或“人数”列也允许存在，缺失的另一种口径稍后补 0。
    metric_columns = [
        column
        for column in zb.columns
        if column not in DIMENSION_COLUMNS
        and column.startswith(METRIC_PREFIX)
    ]

    if not metric_columns:
        raise ValueError("宽指标数据集中没有找到 digo_ 指标字段")

    # 宽转长：每条输入宽表记录会按指标列展开成多行；维度列作为 id_vars 原样保留。
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

    # 将字符串数值转成数值类型；空字符串、非数字和空值统一按 0，确保可聚合和计算比率。
    metric_rows["metric_value"] = pd.to_numeric(
        metric_rows["metric_value"],
        errors="coerce",
    ).fillna(0)

    # 同一业务维度 + 指标 ID + 口径可能有多条记录，因此先求和。
    # unstack 将“转化规模/转化人数”从行值展开回两列，fill_value=0 覆盖组内缺失口径。
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

    # 转化率定义为规模 / 人数；默认置 0 并仅对人数非 0 的行计算，避免除零。
    long_metrics["转化率"] = 0.0

    has_users = long_metrics["转化人数"] != 0
    long_metrics.loc[has_users, "转化率"] = (
        long_metrics.loc[has_users, "转化规模"]
        / long_metrics.loc[has_users, "转化人数"]
    )

    # 转化指标 ID 与维度表 metric_id 是同一业务键：临时改名仅用于 Join，
    # 同时保留“转化指标id”作为最终输出字段。
    left = wd.copy()
    right = long_metrics.rename(
        columns={"转化指标id": "metric_id"}
    )
    right["转化指标id"] = right["metric_id"]

    # 两侧同一主键可能分别以数字和字符串形式返回，统一成字符串再连接。
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

    # 以 wd 为左表保留所有目录明细；没有匹配到指标值时，输出字段使用约定的空值默认值。
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


# 输入名固定为 table_ab/table_wd，但组件配置不保证它们的业务角色顺序；
# 先看字段特征识别哪份是 metric_id 目录表、哪份是 digo_ 宽指标表，再调用转换函数。
frame_zb = to_pandas(table_ab)
frame_wd = to_pandas(table_wd)

# 页面筛选可能让上游查询返回无列空表；这是正常的“无数据”，不是输入结构异常。
if frame_zb.empty or frame_wd.empty:
    result = []
elif (
    "metric_id" in frame_zb.columns
    and any(column.startswith(METRIC_PREFIX) for column in frame_wd.columns)
):
    # 常见约定：table_ab 是宽指标数据，table_wd 是维度目录数据。
    wd, zb = frame_zb, frame_wd
    result = transform_strategy_readout(wd, zb).to_dict(orient="records")
elif (
    "metric_id" in frame_wd.columns
    and any(column.startswith(METRIC_PREFIX) for column in frame_zb.columns)
):
    # 若组件输入顺序相反，则交换角色，转换逻辑仍保持 wd 在左、zb 在右。
    wd, zb = frame_wd, frame_zb
    result = transform_strategy_readout(wd, zb).to_dict(orient="records")
else:
    raise ValueError(
        "两个数据集中未识别出一个维度数据集和一个 digo_ 宽指标数据集"
    )

# ===== 输出结果示例（参考） =====

# 示例仅用于参考；实际输出仍按组件契约校验。

# {"schemaVersion":"1.0","kind":"table","data":{"columns":[],"rows":[]},"meta":{"rowCount":0,"truncated":false}}
# ===== 造数示例与上游数据读取 =====
# 上游输入已按查询计划应用页面筛选、排序和分页；默认读取该查询结果。

# 造数示例：默认注释；取消对应注释即可用上游「查看数据-查询」的最近结果进行本地调试。
# 造数优先：变量已在此处赋值时，下面会跳过对应的真实数据读取。
# import json
# import polars as pl
# table_ab = pl.DataFrame(json.loads("[{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"channel\":\"APP\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"3890000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"400\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"1320000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"302\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"2670000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"232\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"670000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"123\",\"digo_cust_asset_in\":\"574\",\"digo_new_cust_asset_in\":\"228\",\"digo_cnt_cust_code_new_brok\":\"480\",\"digo_cust_valid_new_yxh\":\"285\",\"digo_cust_asset_in10000\":\"135\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-001\",\"platform_id\":\"SUB-001\",\"channel\":\"SMS\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"3060000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"319\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"991000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"255\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"2020000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"181\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"551000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"99\",\"digo_cust_asset_in\":\"445\",\"digo_new_cust_asset_in\":\"175\",\"digo_cnt_cust_code_new_brok\":\"371\",\"digo_cust_valid_new_yxh\":\"214\",\"digo_cust_asset_in10000\":\"102\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_strategy_id\":\"STR-002\",\"platform_id\":\"SUB-002\",\"channel\":\"APP\",\"digo_trd_fund_amt_inout_cy_jjgr\":\"4580000\",\"digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt\":\"474\",\"digo_trd_fund_amt_inout_cy_jjgr_pb\":\"1582000\",\"digo_pbcnt_kgdb_a566_fh_jjgr\":\"354\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0\":\"2894000\",\"digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt\":\"276\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr\":\"823000\",\"digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt\":\"144\",\"digo_cust_asset_in\":\"657\",\"digo_new_cust_asset_in\":\"264\",\"digo_cnt_cust_code_new_brok\":\"559\",\"digo_cust_valid_new_yxh\":\"338\",\"digo_cust_asset_in10000\":\"161\"}]"))
# table_wd = pl.DataFrame(json.loads("[{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"trd_fund_amt_inout_cy_jjgr\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"fund_trd_amt_a566_fh_kgdb_jj0\",\"metric_name\":\"经纪个人场内公募非货交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"SMS\",\"metric_id\":\"trd_fund_amt_inout_cy_jjgr\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"390\",\"digo_distr_user_cnt_a\":\"315\",\"digo_touch_cnt_1\":\"264\",\"digo_touch_user_cnt_1\":\"213\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"SMS\",\"metric_id\":\"fund_trd_amt_a566_fh_kgdb_jj0\",\"metric_name\":\"经纪个人场内公募非货交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"390\",\"digo_distr_user_cnt_a\":\"315\",\"digo_touch_cnt_1\":\"264\",\"digo_touch_user_cnt_1\":\"213\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-001\",\"attribution_strategy_name\":\"高净值客户触达策略\",\"attribution_over_by\":\"strategy_owner_a\",\"platform_id\":\"SUB-001\",\"platform_name\":\"场内公募非货子策略\",\"create_by\":\"data_owner\",\"channel\":\"SMS\",\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\",\"digo_strategy_cnt\":\"6\",\"digo_strategy_cnt_distr_1\":\"6\",\"digo_distr_count_1\":\"390\",\"digo_distr_user_cnt_a\":\"315\",\"digo_touch_cnt_1\":\"264\",\"digo_touch_user_cnt_1\":\"213\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-002\",\"attribution_strategy_name\":\"新客增长策略\",\"attribution_over_by\":\"strategy_owner_b\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"trd_fund_amt_inout_cy_jjgr\",\"metric_name\":\"经纪个人客户场内公募非货当年净买入\",\"digo_strategy_cnt\":\"9\",\"digo_strategy_cnt_distr_1\":\"9\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-002\",\"attribution_strategy_name\":\"新客增长策略\",\"attribution_over_by\":\"strategy_owner_b\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"fund_trd_amt_a566_fh_kgdb_jj0\",\"metric_name\":\"经纪个人场内公募非货交易量\",\"digo_strategy_cnt\":\"9\",\"digo_strategy_cnt_distr_1\":\"9\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"},{\"attribution_plan_id\":\"PLAN-001\",\"attribution_plan_name\":\"策略解读示例计划\",\"attribution_plan_um_account\":\"plan_owner\",\"attribution_strategy_id\":\"STR-002\",\"attribution_strategy_name\":\"新客增长策略\",\"attribution_over_by\":\"strategy_owner_b\",\"platform_id\":\"SUB-002\",\"platform_name\":\"新客入金子策略\",\"create_by\":\"data_owner\",\"channel\":\"APP\",\"metric_id\":\"pub_fh_kgdb_trdamt_ppcadd_jjgr\",\"metric_name\":\"经纪个人场内公募非货加仓交易量\",\"digo_strategy_cnt\":\"9\",\"digo_strategy_cnt_distr_1\":\"9\",\"digo_distr_count_1\":\"540\",\"digo_distr_user_cnt_a\":\"450\",\"digo_touch_cnt_1\":\"360\",\"digo_touch_user_cnt_1\":\"288\"}]"))

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
    # 当前 Runner 常见输入是 Polars DataFrame，优先使用它的转换方法。
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